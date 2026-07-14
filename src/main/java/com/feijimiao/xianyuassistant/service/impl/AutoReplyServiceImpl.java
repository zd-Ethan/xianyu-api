package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.config.rag.DynamicAIChatClientManager;
import com.feijimiao.xianyuassistant.entity.XianyuFirstReplyRecord;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoReplyRecord;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.bo.AutoReplyTriggerContext;
import com.feijimiao.xianyuassistant.entity.bo.EffectiveReplyConfigBO;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.mapper.XianyuFirstReplyRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.service.AIService;
import com.feijimiao.xianyuassistant.service.AutoReplyService;
import com.feijimiao.xianyuassistant.service.ReplyTemplateResolver;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.bo.RAGReplyResult;
import com.feijimiao.xianyuassistant.service.reply.ReplyStrategy;
import com.feijimiao.xianyuassistant.service.reply.ReplyStrategyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AutoReplyServiceImpl implements AutoReplyService {
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;

    @Autowired
    private XianyuFirstReplyRecordMapper firstReplyRecordMapper;
    
    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;
    
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired(required = false)
    private AIService aiService;

    @Autowired
    private DynamicAIChatClientManager dynamicAIChatClientManager;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.SentMessageSaveService sentMessageSaveService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.AccountService accountService;

    @Autowired
    private ReplyStrategyResolver replyStrategyResolver;

    @Autowired
    private ReplyTemplateResolver replyTemplateResolver;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void executeAutoReply(ChatMessageData messageData) {
        if (messageData == null) {
            log.warn("消息数据为空，无法执行自动回复");
            return;
        }
        executeAutoReply(Collections.singletonList(messageData));
    }
    
    @Override
    public void executeAutoReply(List<ChatMessageData> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            log.warn("消息列表为空，无法执行自动回复");
            return;
        }
        
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();
        String sId = lastMessage.getSId();
        String pnmId = lastMessage.getPnmId();
        
        String buyerMessage = messageList.stream()
                .map(ChatMessageData::getMsgContent)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        
        log.info("【账号{}】开始执行自动回复: xyGoodsId={}, sId={}, 触发消息数={}, buyerMessage={}", 
                accountId, xyGoodsId, sId, messageList.size(), buyerMessage);
        
        try {
            // 1. 检查是否有任何回复开关开启
            if (!isAnyReplyEnabled(accountId, xyGoodsId)) {
                log.info("【账号{}】商品未开启任何回复开关: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            // 2. 获取商品本地ID
            XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(
                    new LambdaQueryWrapper<XianyuGoodsInfo>()
                            .eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
                            .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
            );
            if (goodsInfo == null) {
                log.warn("【账号{}】未找到商品信息: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }

            EffectiveReplyConfigBO effectiveConfig = replyTemplateResolver.getEffectiveConfig(accountId, xyGoodsId);
            if (tryHandleFirstReply(messageList, lastMessage, effectiveConfig, goodsInfo, buyerMessage)) {
                return;
            }
            
            // 3. 解析回复策略
            ReplyStrategy strategy = replyStrategyResolver.resolve(messageList);
            if (strategy == null) {
                log.info("【账号{}】无可用回复策略: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            // 4. 构建触发上下文
            AutoReplyTriggerContext triggerContext = buildTriggerContext(messageList);
            
            // 5. 创建回复记录（状态=0，待回复）
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXianyuGoodsId(goodsInfo.getId());
            record.setXyGoodsId(xyGoodsId);
            record.setSId(sId);
            record.setPnmId(pnmId);
            record.setBuyerUserId(lastMessage.getSenderUserId());
            record.setBuyerUserName(lastMessage.getSenderUserName());
            record.setBuyerMessage(buyerMessage);
            record.setState(0);
            
            int insertResult;
            try {
                insertResult = autoReplyRecordMapper.insert(record);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                    log.info("【账号{}】该消息已处理过，跳过自动回复: sId={}, pnmId={}", accountId, sId, pnmId);
                    return;
                }
                throw e;
            }
            
            if (insertResult <= 0) {
                log.error("【账号{}】创建回复记录失败", accountId);
                return;
            }
            
            // 6. 执行回复策略
            ReplyStrategy.ReplyResult replyResult = strategy.execute(messageList);
            
            if (!replyResult.isSuccess() || replyResult.getItems() == null || replyResult.getItems().isEmpty()) {
                log.warn("【账号{}】回复策略未生成有效内容", accountId);
                updateRecordState(record.getId(), -1, null);
                return;
            }
            
            if (replyResult.getMatchedKeyword() != null) {
                record.setMatchedKeyword(replyResult.getMatchedKeyword());
            }
            
            String allReplyText = replyResult.getItems().stream()
                    .map(ReplyStrategy.ReplyResult.ReplyItem::getTextContent)
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .collect(java.util.stream.Collectors.joining("\n"));
            record.setReplyType(replyResult.getItems().get(0).getReplyType());
            
            log.info("【账号{}】回复策略生成内容: type={}, keyword={}, itemCount={}", 
                    accountId, replyResult.getItems().get(0).getReplyType(), replyResult.getMatchedKeyword(),
                    replyResult.getItems().size());
            
            // 7. 保存触发上下文
            try {
                if (effectiveConfig != null && hasText(effectiveConfig.getFixedMaterial())) {
                    triggerContext.setFixedMaterial(effectiveConfig.getFixedMaterial());
                }
                
                XianyuGoodsInfo goodsInfoForContext = goodsInfoMapper.selectOne(
                    new LambdaQueryWrapper<XianyuGoodsInfo>()
                            .eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
                            .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
                );
                if (goodsInfoForContext != null && goodsInfoForContext.getDetailInfo() != null && !goodsInfoForContext.getDetailInfo().isEmpty()) {
                    triggerContext.setGoodsDetail(goodsInfoForContext.getDetailInfo());
                }
            } catch (Exception e) {
                log.warn("【账号{}】获取固定资料和商品详情失败: {}", accountId, e.getMessage());
            }
            
            try {
                String triggerContextJson = objectMapper.writeValueAsString(triggerContext);
                record.setTriggerContext(triggerContextJson);
                autoReplyRecordMapper.updateTriggerContext(record.getId(), triggerContextJson);
            } catch (Exception e) {
                log.warn("【账号{}】序列化触发上下文失败，跳过保存: {}", accountId, e.getMessage());
            }
            
            // 8. 发送回复消息
            boolean sendSuccess = false;
            String cid = normalizeGoofishId(sId);
            String toId = cid;
            if (!hasText(cid)) {
                log.error("【账号{}】自动回复缺少会话ID，无法发送: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(record.getId(), -1, allReplyText);
                return;
            }
            
            for (ReplyStrategy.ReplyResult.ReplyItem item : replyResult.getItems()) {
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    boolean imageSent = webSocketService.sendImageMessageWithResult(accountId, cid, toId, item.getImageUrl(), 0, 0);
                    if (!imageSent) {
                        log.warn("【账号{}】发送回复图片失败: {}", accountId, item.getImageUrl());
                    } else {
                        sendSuccess = true;
                        sentMessageSaveService.saveAiImageReply(accountId, cid, toId, item.getImageUrl(), xyGoodsId);
                    }
                }
                if (item.getTextContent() != null && !item.getTextContent().trim().isEmpty()) {
                    boolean textSent = webSocketService.sendMessage(accountId, cid, toId, item.getTextContent());
                    if (textSent) {
                        sendSuccess = true;
                    }
                }
            }
            
            // 9. 更新记录状态
            if (sendSuccess) {
                log.info("【账号{}】自动回复成功: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId);
                updateRecordState(record.getId(), 1, allReplyText);
                
                if (allReplyText != null && !allReplyText.trim().isEmpty()) {
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, allReplyText, xyGoodsId);
                }
            } else {
                log.error("【账号{}】自动回复发送失败: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId);
                updateRecordState(record.getId(), -1, allReplyText);
            }
            
        } catch (Exception e) {
            log.error("【账号{}】执行自动回复异常: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId, e);
        }
    }
    
    @Override
    public boolean isAutoReplyEnabled(Long accountId, String xyGoodsId) {
        return isAnyReplyEnabled(accountId, xyGoodsId);
    }
    
    private boolean isAnyReplyEnabled(Long accountId, String xyGoodsId) {
        if (accountId == null || xyGoodsId == null) {
            return false;
        }
        try {
            EffectiveReplyConfigBO config = replyTemplateResolver.getEffectiveConfig(accountId, xyGoodsId);
            boolean aiOn = config.getXianyuAutoReplyOn() != null && config.getXianyuAutoReplyOn() == 1;
            boolean keywordOn = config.getXianyuKeywordReplyOn() != null && config.getXianyuKeywordReplyOn() == 1;
            boolean firstReplyOn = isFirstReplyConfigured(config);
            return aiOn || keywordOn || firstReplyOn;
        } catch (Exception e) {
            log.error("【账号{}】检查回复开关异常: xyGoodsId={}", accountId, xyGoodsId, e);
            return false;
        }
    }

    private boolean tryHandleFirstReply(List<ChatMessageData> messageList,
                                        ChatMessageData lastMessage,
                                        EffectiveReplyConfigBO replyConfig,
                                        XianyuGoodsInfo goodsInfo,
                                        String buyerMessage) {
        if (!isFirstReplyConfigured(replyConfig)) {
            return false;
        }

        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();
        String buyerUserId = lastMessage.getSenderUserId();
        if (!hasText(buyerUserId)) {
            log.warn("【账号{}】首次咨询回复缺少买家ID，跳过首次回复: xyGoodsId={}, sId={}",
                    accountId, xyGoodsId, lastMessage.getSId());
            return false;
        }

        if (isFirstReplySkipManualEnabled(replyConfig) && hasManualSellerReply(accountId, xyGoodsId, lastMessage.getSId())) {
            log.info("[account {}] Skip first reply because seller has manually replied: xyGoodsId={}, sId={}, buyerUserId={}",
                    accountId, xyGoodsId, lastMessage.getSId(), buyerUserId);
            return false;
        }

        if (hasPriorBuyerConsultMessage(messageList, accountId, xyGoodsId, buyerUserId)) {
            log.info("【账号{}】买家已有历史咨询消息，跳过首次咨询回复: xyGoodsId={}, buyerUserId={}, pnmId={}",
                    accountId, xyGoodsId, buyerUserId, lastMessage.getPnmId());
            return false;
        }

        XianyuFirstReplyRecord firstRecord = new XianyuFirstReplyRecord();
        firstRecord.setXianyuAccountId(accountId);
        firstRecord.setXianyuGoodsId(goodsInfo.getId());
        firstRecord.setXyGoodsId(xyGoodsId);
        firstRecord.setBuyerUserId(buyerUserId);
        firstRecord.setBuyerUserName(lastMessage.getSenderUserName());
        firstRecord.setSId(lastMessage.getSId());
        firstRecord.setPnmId(lastMessage.getPnmId());
        firstRecord.setReplyContent(trimToEmpty(replyConfig.getFirstReplyText()));
        firstRecord.setReplyImageUrl(trimToEmpty(replyConfig.getFirstReplyImageUrl()));
        firstRecord.setState(0);

        int claimResult = firstReplyRecordMapper.insertIgnore(firstRecord);
        if (claimResult <= 0) {
            XianyuFirstReplyRecord existing = firstReplyRecordMapper.selectByBuyerAndGoods(accountId, xyGoodsId, buyerUserId);
            if (existing != null && existing.getState() != null && existing.getState() == 0) {
                log.info("【账号{}】首次咨询回复已被其他任务领取，跳过本次消息: xyGoodsId={}, buyerUserId={}",
                        accountId, xyGoodsId, buyerUserId);
                return true;
            }
            log.info("【账号{}】买家已触发过首次咨询回复，继续普通自动回复: xyGoodsId={}, buyerUserId={}",
                    accountId, xyGoodsId, buyerUserId);
            return false;
        }

        if (firstRecord.getId() == null) {
            XianyuFirstReplyRecord inserted = firstReplyRecordMapper.selectByBuyerAndGoods(accountId, xyGoodsId, buyerUserId);
            if (inserted != null) {
                firstRecord.setId(inserted.getId());
            }
        }

        String replyText = trimToEmpty(replyConfig.getFirstReplyText());
        String imageUrl = trimToEmpty(replyConfig.getFirstReplyImageUrl());
        String replyContentForRecord = buildReplyContentForRecord(replyText, imageUrl);

        XianyuGoodsAutoReplyRecord autoRecord = buildAutoReplyRecord(
                accountId,
                goodsInfo,
                xyGoodsId,
                lastMessage.getSId(),
                lastMessage.getPnmId(),
                buyerUserId,
                lastMessage.getSenderUserName(),
                buyerMessage
        );
        autoRecord.setReplyType(4);
        autoRecord.setMatchedKeyword("首次咨询回复");

        try {
            autoReplyRecordMapper.insert(autoRecord);
            saveTriggerContext(autoRecord.getId(), autoRecord, buildTriggerContext(messageList));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                log.info("【账号{}】该消息已有回复记录，跳过首次咨询回复: sId={}, pnmId={}",
                        accountId, lastMessage.getSId(), lastMessage.getPnmId());
                updateFirstReplyRecordState(firstRecord.getId(), -1, replyContentForRecord, imageUrl);
                return true;
            }
            log.error("【账号{}】创建首次咨询回复记录失败: xyGoodsId={}, buyerUserId={}",
                    accountId, xyGoodsId, buyerUserId, e);
            updateFirstReplyRecordState(firstRecord.getId(), -1, replyContentForRecord, imageUrl);
            return true;
        }

        try {
            boolean sendSuccess = false;
            String cid = normalizeGoofishId(lastMessage.getSId());
            String toId = cid;
            if (!hasText(cid)) {
                log.error("【账号{}】首次咨询回复缺少会话ID，无法发送: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(autoRecord.getId(), -1, replyContentForRecord);
                updateFirstReplyRecordState(firstRecord.getId(), -1, replyContentForRecord, imageUrl);
                return true;
            }

            if (hasText(imageUrl)) {
                boolean imageSent = webSocketService.sendImageMessageWithResult(accountId, cid, toId, imageUrl, 0, 0);
                if (imageSent) {
                    sendSuccess = true;
                    sentMessageSaveService.saveAiImageReply(accountId, cid, toId, imageUrl, xyGoodsId);
                } else {
                    log.warn("【账号{}】发送首次咨询回复图片失败: {}", accountId, imageUrl);
                }
            }

            if (hasText(replyText)) {
                boolean textSent = webSocketService.sendMessage(accountId, cid, toId, replyText);
                if (textSent) {
                    sendSuccess = true;
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, replyText, xyGoodsId);
                } else {
                    log.warn("【账号{}】发送首次咨询回复文本失败", accountId);
                }
            }

            int state = sendSuccess ? 1 : -1;
            updateRecordState(autoRecord.getId(), state, replyContentForRecord);
            updateFirstReplyRecordState(firstRecord.getId(), state, replyContentForRecord, imageUrl);

            if (sendSuccess) {
                log.info("【账号{}】首次咨询回复成功: xyGoodsId={}, buyerUserId={}", accountId, xyGoodsId, buyerUserId);
            } else {
                log.error("【账号{}】首次咨询回复发送失败: xyGoodsId={}, buyerUserId={}", accountId, xyGoodsId, buyerUserId);
            }
        } catch (Exception e) {
            log.error("【账号{}】首次咨询回复发送异常: xyGoodsId={}, buyerUserId={}",
                    accountId, xyGoodsId, buyerUserId, e);
            updateRecordState(autoRecord.getId(), -1, replyContentForRecord);
            updateFirstReplyRecordState(firstRecord.getId(), -1, replyContentForRecord, imageUrl);
        }
        return true;
    }

    private boolean hasPriorBuyerConsultMessage(List<ChatMessageData> messageList,
                                                Long accountId,
                                                String xyGoodsId,
                                                String buyerUserId) {
        try {
            Long earliestMessageTime = messageList.stream()
                    .map(ChatMessageData::getMessageTime)
                    .filter(java.util.Objects::nonNull)
                    .min(Long::compareTo)
                    .orElse(null);
            List<String> currentPnmIds = messageList.stream()
                    .map(ChatMessageData::getPnmId)
                    .filter(this::hasText)
                    .distinct()
                    .toList();
            int priorCount = chatMessageMapper.countPriorBuyerConsultMessages(
                    accountId,
                    xyGoodsId,
                    buyerUserId,
                    earliestMessageTime,
                    currentPnmIds
            );
            return priorCount > 0;
        } catch (Exception e) {
            log.warn("【账号{}】检查买家历史咨询失败，跳过首次咨询回复以避免误发: xyGoodsId={}, buyerUserId={}, error={}",
                    accountId, xyGoodsId, buyerUserId, e.getMessage(), e);
            return true;
        }
    }

    private XianyuGoodsAutoReplyRecord buildAutoReplyRecord(Long accountId,
                                                           XianyuGoodsInfo goodsInfo,
                                                           String xyGoodsId,
                                                           String sId,
                                                           String pnmId,
                                                           String buyerUserId,
                                                           String buyerUserName,
                                                           String buyerMessage) {
        XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(goodsInfo.getId());
        record.setXyGoodsId(xyGoodsId);
        record.setSId(sId);
        record.setPnmId(pnmId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerUserName);
        record.setBuyerMessage(buyerMessage);
        record.setState(0);
        return record;
    }

    private AutoReplyTriggerContext buildTriggerContext(List<ChatMessageData> messageList) {
        AutoReplyTriggerContext triggerContext = new AutoReplyTriggerContext();
        List<AutoReplyTriggerContext.TriggerMessage> triggerMessages = new ArrayList<>();
        for (ChatMessageData msg : messageList) {
            AutoReplyTriggerContext.TriggerMessage tm = new AutoReplyTriggerContext.TriggerMessage();
            tm.setPnmId(msg.getPnmId());
            tm.setSenderUserId(msg.getSenderUserId());
            tm.setSenderUserName(msg.getSenderUserName());
            tm.setMsgContent(msg.getMsgContent());
            tm.setMessageTime(msg.getMessageTime());
            triggerMessages.add(tm);
        }
        triggerContext.setTriggerMessages(triggerMessages);
        return triggerContext;
    }

    private void saveTriggerContext(Long recordId, XianyuGoodsAutoReplyRecord record, AutoReplyTriggerContext triggerContext) {
        if (recordId == null) {
            return;
        }
        try {
            String triggerContextJson = objectMapper.writeValueAsString(triggerContext);
            record.setTriggerContext(triggerContextJson);
            autoReplyRecordMapper.updateTriggerContext(recordId, triggerContextJson);
        } catch (Exception e) {
            log.warn("序列化首次咨询回复上下文失败，跳过保存: {}", e.getMessage());
        }
    }

    private boolean isFirstReplyConfigured(EffectiveReplyConfigBO replyConfig) {
        return replyConfig != null
                && replyConfig.getFirstReplyOn() != null
                && replyConfig.getFirstReplyOn() == 1
                && (hasText(replyConfig.getFirstReplyText()) || hasText(replyConfig.getFirstReplyImageUrl()));
    }

    private boolean isFirstReplySkipManualEnabled(EffectiveReplyConfigBO replyConfig) {
        return replyConfig != null
                && replyConfig.getFirstReplySkipManualOn() != null
                && replyConfig.getFirstReplySkipManualOn() == 1;
    }

    private boolean hasManualSellerReply(Long accountId, String xyGoodsId, String sId) {
        if (accountId == null || !hasText(xyGoodsId) || !hasText(sId)) {
            return false;
        }
        try {
            String ownUserId = accountService.getXianyuUserId(accountId);
            int manualReplyCount = chatMessageMapper.countManualSellerReplies(
                    accountId,
                    xyGoodsId,
                    normalizeSessionId(sId),
                    normalizePlainSessionId(sId),
                    ownUserId
            );
            return manualReplyCount > 0;
        } catch (Exception e) {
            log.warn("[account {}] Failed to check manual seller replies, first reply will not be blocked: xyGoodsId={}, sId={}, error={}",
                    accountId, xyGoodsId, sId, e.getMessage(), e);
            return false;
        }
    }

    private String normalizeSessionId(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.endsWith("@goofish") ? value : value + "@goofish";
    }

    private String normalizePlainSessionId(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.endsWith("@goofish") ? value.substring(0, value.length() - "@goofish".length()) : value;
    }

    private String buildReplyContentForRecord(String replyText, String imageUrl) {
        if (hasText(replyText) && hasText(imageUrl)) {
            return replyText + "\n[图片] " + imageUrl;
        }
        if (hasText(replyText)) {
            return replyText;
        }
        return hasText(imageUrl) ? "[图片] " + imageUrl : "";
    }

    private void updateFirstReplyRecordState(Long recordId, Integer state, String replyContent, String imageUrl) {
        if (recordId == null) {
            return;
        }
        try {
            firstReplyRecordMapper.updateState(recordId, state, replyContent, imageUrl);
        } catch (Exception e) {
            log.error("更新首次咨询回复记录状态失败: recordId={}, state={}", recordId, state, e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeGoofishId(String value) {
        return value == null ? "" : value.replace("@goofish", "");
    }
    
    private void updateRecordState(Long recordId, Integer state, String replyContent) {
        try {
            autoReplyRecordMapper.updateStateAndContent(recordId, state, replyContent);
        } catch (Exception e) {
            log.error("更新回复记录状态失败: recordId={}, state={}", recordId, state, e);
        }
    }
}
