package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoReplyRecord;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.DeliveryTemplateResolver;
import com.feijimiao.xianyuassistant.service.EmailNotifyService;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryContext;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryStrategyResolver;
import com.feijimiao.xianyuassistant.service.delivery.OrderDetailFetcher;
import com.feijimiao.xianyuassistant.service.order.DeliveryLockManager;
import com.feijimiao.xianyuassistant.service.order.OrderStatus;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动发货服务实现类（编排层）
 *
 * <p>负责发货流程的编排，具体逻辑委托给 delivery 包下的组件：</p>
 * <ul>
 *   <li>{@link OrderDetailFetcher} - 订单详情获取与解析</li>
 *   <li>{@link DeliveryStrategyResolver} - 发货内容策略解析（文本/卡密/自定义）</li>
 * </ul>
 */
@Slf4j
@Service
public class AutoDeliveryServiceImpl implements AutoDeliveryService {
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Autowired
    private XianyuGoodsOrderMapper orderMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;
    
    @Lazy
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.SentMessageSaveService sentMessageSaveService;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDetailFetcher orderDetailFetcher;

    @Autowired
    private DeliveryStrategyResolver deliveryStrategyResolver;

    @Autowired
    private DeliveryLockManager deliveryLockManager;

    @Autowired
    private DeliveryTemplateResolver deliveryTemplateResolver;
    
    @Override
    public XianyuGoodsConfig getGoodsConfig(Long accountId, String xyGoodsId) {
        return goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
    }
    
    @Override
    public XianyuGoodsAutoDeliveryConfig getAutoDeliveryConfig(Long accountId, String xyGoodsId) {
        return deliveryTemplateResolver.getExecutionConfig(accountId, xyGoodsId, null);
    }
    
    @Override
    public void saveOrUpdateGoodsConfig(XianyuGoodsConfig config) {
        if (config.getFirstReplySkipManualOn() == null) {
            config.setFirstReplySkipManualOn(0);
        }
        XianyuGoodsConfig existing = goodsConfigMapper.selectByAccountAndGoodsId(
                config.getXianyuAccountId(), config.getXyGoodsId());
        
        if (existing == null) {
            goodsConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            goodsConfigMapper.update(config);
        }
    }
    
    @Override
    public void saveOrUpdateAutoDeliveryConfig(XianyuGoodsAutoDeliveryConfig config) {
        if (config.getMultiQuantityDelivery() == null) {
            config.setMultiQuantityDelivery(1);
        }
        String skuId = config.getSkuId();
        if (skuId != null && skuId.isEmpty()) {
            skuId = null;
            config.setSkuId(null);
        }
        XianyuGoodsAutoDeliveryConfig existingConfig;
        if (skuId != null) {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(
                    config.getXianyuAccountId(), config.getXyGoodsId(), skuId);
        } else {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(
                    config.getXianyuAccountId(), config.getXyGoodsId());
        }
        
        if (existingConfig == null) {
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setId(existingConfig.getId());
            autoDeliveryConfigMapper.updateById(config);
        }
    }
    
    @Override
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, String content, Integer state) {
        recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, state, null, null);
    }
    
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, 
                                   String content, Integer state, String pnmId, String orderId) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(xyGoodsId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerUserName);
        record.setContent(content);
        record.setState(state);
        record.setPnmId(pnmId != null ? pnmId : "");
        record.setOrderId(orderId != null ? orderId : "");
        record.setConfirmState(0);
        
        orderMapper.insert(record);
    }
    
    @Override
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName) {
        handleAutoDelivery(accountId, xyGoodsId, sId, buyerUserId, buyerUserName, null);
    }
    
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName, String orderId) {
        try {
            log.info("【账号{}】处理自动发货: xyGoodsId={}, sId={}, buyerUserId={}, buyerUserName={}, orderId={}", 
                    accountId, xyGoodsId, sId, buyerUserId, buyerUserName, orderId);
            
            XianyuGoodsConfig goodsConfig = getGoodsConfig(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            XianyuGoodsAutoDeliveryConfig deliveryConfig = getAutoDeliveryConfig(accountId, xyGoodsId);
            if (deliveryConfig == null) {
                log.warn("【账号{}】商品未配置自动发货内容: xyGoodsId={}", accountId, xyGoodsId);
                recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
                return;
            }
            
            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            DeliveryContext ctx = DeliveryContext.builder()
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(sId)
                    .orderId(orderId)
                    .buyerUserName(buyerUserName)
                    .deliveryConfig(deliveryConfig)
                    .build();
            String content = deliveryStrategyResolver.resolve(deliveryMode, ctx);
            if (content == null || content.isEmpty()) {
                log.warn("【账号{}】商品未配置可用发货内容: xyGoodsId={}", accountId, xyGoodsId);
                recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
                return;
            }
            log.info("【账号{}】准备发送自动发货消息: content={}", accountId, content);

            HumanLikeDelayUtils.mediumDelay();
            HumanLikeDelayUtils.thinkingDelay();
            HumanLikeDelayUtils.typingDelay(content.length());
            
            String cid = sId.replace("@goofish", "");
            String toId = cid;
            
            boolean success = webSocketService.sendMessage(accountId, cid, toId, content);
            sendDeliveryImages(accountId, xyGoodsId, cid, toId, deliveryConfig, true);
            
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, success ? 1 : 0, null, orderId);
            
            if (success) {
                log.info("【账号{}】自动发货成功: xyGoodsId={}, buyerUserName={}, content={}", 
                        accountId, xyGoodsId, buyerUserName, content);
                sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
            } else {
                log.error("【账号{}】自动发货失败: xyGoodsId={}", accountId, xyGoodsId);
            }
            
        } catch (Exception e) {
            log.error("【账号{}】自动发货异常: xyGoodsId={}", accountId, xyGoodsId, e);
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
        }
    }
    
    @Override
    public void handleAutoReply(Long accountId, String xyGoodsId, String sId, String buyerMessage) {
        log.info("【账号{}】自动回复功能已移除: xyGoodsId={}", accountId, xyGoodsId);
    }
    
    private void recordAutoReply(Long accountId, String xyGoodsId, String buyerMessage, 
                                  String replyContent, String matchedKeyword, Integer state) {
        try {
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXyGoodsId(xyGoodsId);
            record.setBuyerMessage(buyerMessage);
            record.setReplyContent(replyContent);
            record.setMatchedKeyword(matchedKeyword);
            record.setState(state);
            
            autoReplyRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("【账号{}】记录自动回复失败", accountId, e);
        }
    }
    
    @Override
    public com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO getAutoDeliveryRecords(
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordReqDTO reqDTO) {
        
        Long accountId = reqDTO.getXianyuAccountId();
        String xyGoodsId = reqDTO.getXyGoodsId();
        String keyword = reqDTO.getKeyword();
        int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
        int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
        
        int offset = (pageNum - 1) * pageSize;
        
        List<XianyuGoodsOrder> records = orderMapper.selectByAccountIdWithPage(
                accountId, xyGoodsId, keyword, pageSize, offset);
        
        long total = orderMapper.countByAccountId(accountId, xyGoodsId, keyword);
        
        List<com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO> recordDTOs = new ArrayList<>();
        for (XianyuGoodsOrder record : records) {
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO dto = 
                    new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO();
            dto.setId(record.getId());
            dto.setXianyuAccountId(record.getXianyuAccountId());
            dto.setXyGoodsId(record.getXyGoodsId());
            dto.setGoodsTitle(record.getGoodsTitle());
            dto.setBuyerUserName(record.getBuyerUserName());
            dto.setContent(record.getContent());
            dto.setState(record.getState());
            dto.setConfirmState(record.getConfirmState());
            dto.setOrderId(record.getOrderId());
            dto.setSkuName(record.getSkuName());
            dto.setOrderCreateTime(record.getOrderCreateTime());
            dto.setPaySuccessTime(record.getPaySuccessTime());
            dto.setConsignTime(record.getConsignTime());
            dto.setTotalPrice(record.getTotalPrice());
            dto.setBuyNum(record.getBuyNum());
            dto.setCreateTime(record.getCreateTime());
            recordDTOs.add(dto);
        }
        
        com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO respDTO = 
                new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO();
        respDTO.setRecords(recordDTOs);
        respDTO.setTotal(total);
        respDTO.setPageNum(pageNum);
        respDTO.setPageSize(pageSize);
        
        return respDTO;
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<String> triggerAutoDelivery(
            com.feijimiao.xianyuassistant.controller.dto.TriggerAutoDeliveryReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();
            String orderId = reqDTO.getOrderId();
            Boolean needHumanLikeDelay = reqDTO.getNeedHumanLikeDelay() != null ? reqDTO.getNeedHumanLikeDelay() : false;

            log.info("【账号{}】触发自动发货: xyGoodsId={}, orderId={}, needHumanLikeDelay={}", 
                    accountId, xyGoodsId, orderId, needHumanLikeDelay);

            XianyuGoodsOrder record = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (record == null) {
                log.warn("【账号{}】发货记录不存在: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录不存在");
            }

            String pnmId = record.getPnmId();
            if (pnmId == null || pnmId.isEmpty()) {
                log.warn("【账号{}】发货记录没有pnmId: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录没有pnmId");
            }

            Long recordId = record.getId();
            String sId = record.getSid() != null ? record.getSid() : record.getBuyerUserId() + "@goofish";
            String buyerUserName = record.getBuyerUserName();

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("商品未开启自动发货");
            }

            executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay);

            XianyuGoodsOrder updatedRecord = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (updatedRecord != null && OrderStatus.isAlreadyDelivered(updatedRecord.getState())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.success("触发自动发货成功");
            } else {
                String failReason = updatedRecord != null ? updatedRecord.getFailReason() : "未知错误";
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(failReason != null ? failReason : "发货失败");
            }

        } catch (Exception e) {
            log.error("【账号{}】触发自动发货失败: xyGoodsId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderId(), e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("触发自动发货失败: " + e.getMessage());
        }
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId, String orderId, String buyerUserName, boolean needHumanLikeDelay) {
        String deliveryLockKey = deliveryLockManager.key(accountId, orderId, recordId);
        if (!deliveryLockManager.tryLock(deliveryLockKey)) {
            log.info("【账号{}】订单正在发货中，跳过重复触发: orderId={}, recordId={}", accountId, orderId, recordId);
            return;
        }
        try {
            log.info("【账号{}】开始执行自动发货: recordId={}, xyGoodsId={}, orderId={}", accountId, recordId, xyGoodsId, orderId);

            if (!prepareDeliveryRecord(recordId, accountId, orderId)) {
                return;
            }

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.warn("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, "商品未开启自动发货");
                return;
            }

            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(accountId, xyGoodsId, orderId);
            if (orderDetail == null && orderId != null && !orderId.isEmpty()) {
                log.warn("【账号{}】获取订单详情失败(可能Cookie过期或API异常)，中断发货: orderId={}", accountId, orderId);
                String failReason = "获取订单详情失败(可能Cookie过期)，请检查Cookie状态";
                updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, failReason);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                return;
            }
            String orderSkuId = orderDetail != null ? orderDetail.skuId : null;
            int buyNum = (orderDetail != null && orderDetail.buyNum != null && orderDetail.buyNum > 0) ? orderDetail.buyNum : 1;
            log.info("【账号{}】订单SKU: orderId={}, skuId={}, buyNum={}", accountId, orderId, orderSkuId, buyNum);

            if (orderDetail != null) {
                orderMapper.updateOrderDetail(recordId, orderDetail.buyerUserName, orderDetail.orderCreateTime, orderDetail.paySuccessTime, orderDetail.consignTime, orderDetail.skuName, orderDetail.goodsTitle, orderDetail.totalPrice, orderDetail.buyNum);
            }

            XianyuGoodsAutoDeliveryConfig deliveryConfig = deliveryTemplateResolver.getExecutionConfig(accountId, xyGoodsId, orderSkuId);

            if (deliveryConfig == null) {
                log.warn("【账号{}】商品无匹配的发货配置: xyGoodsId={}, skuId={}", accountId, xyGoodsId, orderSkuId);
                updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, "无匹配的发货配置");
                return;
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            int deliveryCount = resolveDeliveryCount(deliveryConfig, buyNum);
            String cid = sId.replace("@goofish", "");
            String toId = cid;
            boolean wsConnected = webSocketService.isConnected(accountId);
            boolean anySuccess = false;
            StringBuilder allContent = new StringBuilder();

            DeliveryContext ctx = DeliveryContext.builder()
                    .recordId(recordId)
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(sId)
                    .orderId(orderId)
                    .buyerUserName(buyerUserName)
                    .deliveryConfig(deliveryConfig)
                    .build();

            if (!wsConnected) {
                log.info("【账号{}】WebSocket未连接，使用虚拟发货API: orderId={}", accountId, orderId);
                List<String> contentList = new ArrayList<>();
                for (int i = 0; i < deliveryCount; i++) {
                    String content = deliveryStrategyResolver.resolve(deliveryMode, ctx);
                    if (content == null) {
                        String failMsg = resolveDeliveryContentFailMessage(deliveryMode);
                        log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                        updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, failMsg);
                        emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                        return;
                    }
                    contentList.add(content);
                }
                String content = String.join("\n", contentList);

                List<String> imageUrls = new ArrayList<>();
                String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
                if (imageUrlStr != null && !imageUrlStr.trim().isEmpty()) {
                    for (String url : imageUrlStr.split(",")) {
                        String trimmed = url.trim();
                        if (!trimmed.isEmpty()) imageUrls.add(trimmed);
                    }
                }

                String deliveryResult = orderService.consignDummyDelivery(accountId, orderId, content, imageUrls);
                if (deliveryResult != null) {
                    anySuccess = true;
                    allContent.append(content);
                    log.info("【账号{}】✅ 虚拟发货API成功: recordId={}, result={}", accountId, recordId, deliveryResult);
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
                } else {
                    log.error("【账号{}】❌ 虚拟发货API失败: recordId={}", accountId, recordId);
                    updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, content, "虚拟发货API失败");
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "虚拟发货API失败");
                    return;
                }
            } else {

            for (int i = 0; i < deliveryCount; i++) {
                log.info("【账号{}】发货第{}/{}次: orderId={}", accountId, i + 1, deliveryCount, orderId);

                String content = deliveryStrategyResolver.resolve(deliveryMode, ctx);

                if (content == null) {
                    String failMsg = resolveDeliveryContentFailMessage(deliveryMode);
                    log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                    updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, failMsg);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                    return;
                }

                if (needHumanLikeDelay) {
                    if (i > 0) {
                        HumanLikeDelayUtils.thinkingDelay();
                    }
                    HumanLikeDelayUtils.mediumDelay();
                    HumanLikeDelayUtils.thinkingDelay();
                    HumanLikeDelayUtils.typingDelay(content.length());
                }

                log.info("【账号{}】准备发送发货文本[{}/{}]: content长度={}, deliveryMode={}", accountId, i + 1, deliveryCount, content.length(), deliveryMode);
                boolean success = webSocketService.sendMessage(accountId, cid, toId, content);

                if (success && needHumanLikeDelay) {
                    HumanLikeDelayUtils.thinkingDelay();
                }

                sendDeliveryImages(accountId, xyGoodsId, cid, toId, deliveryConfig, needHumanLikeDelay);

                if (success) {
                    anySuccess = true;
                    if (allContent.length() > 0) allContent.append("\n");
                    allContent.append(content);
                    log.info("【账号{}】✅ 发货成功[{}/{}]: recordId={}, deliveryMode={}", accountId, i + 1, deliveryCount, recordId, deliveryMode);
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
                } else {
                    log.error("【账号{}】❌ 发货失败[{}/{}]: recordId={}", accountId, i + 1, deliveryCount, recordId);
                    if (i == 0) {
                        updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, content, "消息发送失败");
                        emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "消息发送失败");
                        return;
                    }
                    break;
                }
            }

            } // end else (wsConnected)

            if (anySuccess) {
                updateRecordState(recordId, OrderStatus.DELIVERY_SUCCESS, allContent.toString(), null);
                if (!wsConnected) {
                    orderMapper.updateConfirmState(accountId, orderId);
                    XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
                    if (order != null) {
                        orderMapper.updateOrderStatus(order.getId(), OrderStatus.PLATFORM_SHIPPED);
                    }
                }
                XianyuGoodsAutoDeliveryConfig baseConfig = deliveryTemplateResolver.getExecutionConfig(accountId, xyGoodsId, null);
                boolean autoConfirm = (baseConfig != null && baseConfig.getAutoConfirmShipment() != null && baseConfig.getAutoConfirmShipment() == 1);
                if (autoConfirm && wsConnected) {
                    log.info("【账号{}】检测到自动确认发货开关已开启，准备自动确认发货: orderId={}", accountId, orderId);
                    executeAutoConfirmShipment(accountId, orderId);
                }
            }

        } catch (Exception e) {
            log.error("【账号{}】执行自动发货异常: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId, e);
            String failReason = "发货异常: " + e.getMessage();
            updateRecordState(recordId, OrderStatus.DELIVERY_FAILED, null, failReason);
            emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
        } finally {
            deliveryLockManager.unlock(deliveryLockKey);
        }
    }

    private int resolveDeliveryCount(XianyuGoodsAutoDeliveryConfig deliveryConfig, int buyNum) {
        int safeBuyNum = Math.max(buyNum, 1);
        Integer multiQuantityDelivery = deliveryConfig.getMultiQuantityDelivery();
        boolean enabled = multiQuantityDelivery == null || multiQuantityDelivery == 1;
        return enabled ? safeBuyNum : 1;
    }

    private String resolveDeliveryContentFailMessage(int deliveryMode) {
        if (deliveryMode == 1) {
            return "未配置发货内容";
        }
        if (deliveryMode == 2) {
            return "卡密库存不足，无可用卡密";
        }
        return "未知的发货模式: " + deliveryMode;
    }

    private boolean prepareDeliveryRecord(Long recordId, Long accountId, String orderId) {
        XianyuGoodsOrder currentRecord = orderMapper.selectById(recordId);
        if (currentRecord == null) {
            log.warn("【账号{}】发货记录不存在，跳过: recordId={}, orderId={}", accountId, recordId, orderId);
            return false;
        }
        if (OrderStatus.isAlreadyDelivered(currentRecord.getState())) {
            log.info("【账号{}】订单已发货成功，跳过重复发货: recordId={}, orderId={}", accountId, recordId, orderId);
            return false;
        }
        if (OrderStatus.isDelivering(currentRecord.getState())) {
            log.info("【账号{}】订单正在发货中，跳过重复发货: recordId={}, orderId={}", accountId, recordId, orderId);
            return false;
        }
        if (orderId != null && !orderId.isBlank()
                && orderMapper.countSuccessfulSameOrder(accountId, orderId, recordId) > 0) {
            log.info("【账号{}】同一订单已有成功发货记录，跳过: recordId={}, orderId={}", accountId, recordId, orderId);
            updateRecordState(recordId, OrderStatus.DELIVERY_SUCCESS, currentRecord.getContent(), null);
            return false;
        }
        return orderMapper.markDelivering(recordId) > 0;
    }

    private void sendDeliveryImages(Long accountId, String xyGoodsId, String cid, String toId,
                                    XianyuGoodsAutoDeliveryConfig deliveryConfig, boolean needHumanLikeDelay) {
        String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return;
        }
        String[] imageUrls = imageUrlStr.split(",");
        for (int i = 0; i < imageUrls.length; i++) {
            try {
                String url = imageUrls[i].trim();
                if (url.isEmpty()) continue;
                if (i > 0) {
                    if (needHumanLikeDelay) {
                        HumanLikeDelayUtils.thinkingDelay();
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
                boolean imgSuccess = webSocketService.sendImageMessage(accountId, cid, toId, url, 800, 800);
                if (imgSuccess) {
                    log.info("【账号{}】自动发货图片[{}/{}]发送成功: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                    sentMessageSaveService.saveManualImageReply(accountId, cid, toId, url, xyGoodsId);
                } else {
                    log.warn("【账号{}】自动发货图片[{}/{}]发送失败: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动发货图片[{}/{}]发送异常: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId, e);
            }
        }
    }

    private void executeAutoConfirmShipment(Long accountId, String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("【账号{}】订单ID为空，无法自动确认发货", accountId);
            return;
        }
        log.info("【账号{}】提交异步自动确认发货: orderId={}", accountId, orderId);
        new Thread(() -> {
            try {
                HumanLikeDelayUtils.longDelay();
                String result = orderService.confirmShipment(accountId, orderId);
                if (result != null) {
                    log.info("【账号{}】✅ 自动确认发货成功: orderId={}", accountId, orderId);
                    orderMapper.updateConfirmState(accountId, orderId);
                    XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
                    if (order != null) {
                        orderMapper.updateOrderStatus(order.getId(), OrderStatus.PLATFORM_SHIPPED);
                    }
                } else {
                    log.error("【账号{}】❌ 自动确认发货失败: orderId={}", accountId, orderId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动确认发货异常: orderId={}", accountId, orderId, e);
            }
        }).start();
    }

    private void updateRecordState(Long recordId, Integer state, String content, String failReason) {
        try {
            orderMapper.updateStateContentAndFailReason(recordId, state, content, failReason);
        } catch (Exception e) {
            log.error("更新订单状态失败: recordId={}, state={}", recordId, state, e);
        }
    }

    @Override
    public void updateAutoConfirmShipment(Long accountId, String xyGoodsId, Integer autoConfirmShipment) {
        XianyuGoodsAutoDeliveryConfig config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        if (config == null) {
            config = new XianyuGoodsAutoDeliveryConfig();
            config.setXianyuAccountId(accountId);
            config.setXyGoodsId(xyGoodsId);
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.updateById(config);
        }
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<String> manualDelivery(Long xianyuAccountId, String orderId, String content) {
        String deliveryLockKey = null;
        try {
            if (orderId == null || orderId.isEmpty()) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单ID不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货内容不能为空");
            }

            XianyuGoodsOrder record = orderMapper.selectByAccountIdAndOrderId(xianyuAccountId, orderId);
            if (record == null) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单记录不存在");
            }
            if (OrderStatus.isDeliveryBlockedPlatformStatus(record.getOrderStatus())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(OrderStatus.getDeliveryBlockedReason(record.getOrderStatus()));
            }
            if (OrderStatus.isAlreadyDelivered(record.getState())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单已发货成功，请勿重复发货");
            }
            if (OrderStatus.isDelivering(record.getState())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单正在发货中，请稍后再试");
            }

            deliveryLockKey = deliveryLockManager.key(xianyuAccountId, orderId, record.getId());
            if (!deliveryLockManager.tryLock(deliveryLockKey)) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单正在发货中，请稍后再试");
            }
            record = orderMapper.selectByAccountIdAndOrderId(xianyuAccountId, orderId);
            if (record == null) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单记录不存在");
            }
            if (OrderStatus.isDeliveryBlockedPlatformStatus(record.getOrderStatus())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(OrderStatus.getDeliveryBlockedReason(record.getOrderStatus()));
            }
            if (OrderStatus.isAlreadyDelivered(record.getState())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单已发货成功，请勿重复发货");
            }
            if (OrderStatus.isDelivering(record.getState())) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单正在发货中，请稍后再试");
            }
            if (orderMapper.markDelivering(record.getId()) == 0) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单状态已变化，请刷新后再试");
            }

            String sId = record.getSid() != null ? record.getSid() : record.getBuyerUserId() + "@goofish";
            String cid = sId.replace("@goofish", "");
            String toId = cid;

            boolean success = webSocketService.sendMessage(xianyuAccountId, cid, toId, content);
            if (success) {
                updateRecordState(record.getId(), OrderStatus.DELIVERY_SUCCESS, content, null);
                sentMessageSaveService.saveAiAssistantReply(xianyuAccountId, cid, toId, content, record.getXyGoodsId());
                log.info("【账号{}】自定义发货成功: orderId={}", xianyuAccountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.success("自定义发货成功");
            } else {
                updateRecordState(record.getId(), OrderStatus.DELIVERY_FAILED, content, "消息发送失败");
                log.error("【账号{}】自定义发货失败: orderId={}", xianyuAccountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("消息发送失败");
            }
        } catch (Exception e) {
            log.error("【账号{}】自定义发货异常: orderId={}", xianyuAccountId, orderId, e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("自定义发货异常: " + e.getMessage());
        } finally {
            if (deliveryLockKey != null) {
                deliveryLockManager.unlock(deliveryLockKey);
            }
        }
    }
}
