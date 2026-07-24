package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.order.OrderStatus;
import com.feijimiao.xianyuassistant.service.sales.LocalSalesFactService;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 自动发货事件监听器
 *
 * <p>监听 {@link ChatMessageReceivedEvent} 事件，判断是否需要触发自动发货</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li>contentType = 26（已付款待发货类型）</li>
 *   <li>msgContent 包含 "[已付款，待发货]" 或 "[我已付款，等待你发货]"</li>
 * </ul>
 *
 * <p>职责：事件过滤 + 订单记录创建，发货执行委派给 {@link AutoDeliveryService#executeDelivery}</p>
 */
@Slf4j
@Component
public class ChatMessageEventAutoDeliveryListener {

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private AutoDeliveryService autoDeliveryService;

    @Autowired
    private LocalSalesFactService localSalesFactService;

    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData message = event.getMessageData();
        Long accountId = message.getXianyuAccountId();

        log.info("【账号{}】[AutoDeliveryListener]收到事件: pnmId={}, contentType={}, xyGoodsId={}, sId={}, orderId={}",
                accountId, message.getPnmId(), message.getContentType(),
                message.getXyGoodsId(), message.getSId(), message.getOrderId());

        try {
            if (!isPaymentMessage(message)) {
                return;
            }

            localSalesFactService.recordPaymentMessage(
                    accountId, message.getOrderId(), message.getXyGoodsId());

            if (message.getXyGoodsId() == null || message.getSId() == null) {
                log.warn("【账号{}】消息缺少商品ID或会话ID，无法触发自动发货: pnmId={}", accountId, message.getPnmId());
                return;
            }

            String buyerUserName = message.getSenderUserName();
            log.info("【账号{}】检测到已付款待发货消息: xyGoodsId={}, buyerUserId={}, orderId={}",
                    accountId, message.getXyGoodsId(), message.getSenderUserId(), message.getOrderId());

            Long xianyuGoodsId = resolveXianyuGoodsId(accountId, message.getXyGoodsId());
            if (xianyuGoodsId == null) {
                return;
            }

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, message.getXyGoodsId());
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货，跳过: xyGoodsId={}", accountId, message.getXyGoodsId());
                return;
            }

            Long recordId = createOrderRecord(accountId, xianyuGoodsId, message);
            if (recordId == null) {
                return;
            }

            autoDeliveryService.executeDelivery(
                    recordId, accountId, message.getXyGoodsId(), message.getSId(),
                    message.getOrderId(), buyerUserName, true);

        } catch (Exception e) {
            log.error("【账号{}】处理自动发货异常: pnmId={}", accountId, message.getPnmId(), e);
        }
    }

    private boolean isPaymentMessage(ChatMessageData message) {
        Integer contentType = message.getContentType();
        if (contentType == null || (contentType != 26 && contentType != 32)) {
            return false;
        }
        if (containsPaymentKeywords(message.getMsgContent())) {
            return true;
        }
        return containsPaymentKeywords(message.getCompleteMsg());
    }

    private boolean containsPaymentKeywords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean paid = containsAny(text, "\u5df2\u4ed8\u6b3e", "\\u5df2\\u4ed8\\u6b3e");
        boolean needShip = containsAny(
                text,
                "\u5f85\u53d1\u8d27",
                "\u7b49\u5f85\u4f60\u53d1\u8d27",
                "\\u5f85\\u53d1\\u8d27",
                "\\u7b49\\u5f85\\u4f60\\u53d1\\u8d27"
        );
        return paid && needShip;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Long resolveXianyuGoodsId(Long accountId, String xyGoodsId) {
        QueryWrapper<XianyuGoodsInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("xy_good_id", xyGoodsId);
        queryWrapper.eq("xianyu_account_id", accountId);
        XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(queryWrapper);
        if (goodsInfo == null) {
            log.warn("【账号{}】未找到商品信息: xyGoodsId={}", accountId, xyGoodsId);
            return null;
        }
        return goodsInfo.getId();
    }

    private Long createOrderRecord(Long accountId, Long xianyuGoodsId, ChatMessageData message) {
        XianyuGoodsOrder existing = null;
        if (message.getOrderId() != null && !message.getOrderId().isBlank()) {
            existing = orderMapper.selectByAccountIdAndOrderId(accountId, message.getOrderId());
        }
        if (existing == null && message.getPnmId() != null && !message.getPnmId().isBlank()) {
            existing = orderMapper.selectByPnmId(accountId, message.getPnmId());
        }
        if (existing != null) {
            if (!OrderStatus.canStartDelivery(existing.getState())) {
                log.info("【账号{}】订单已发货或发货中，跳过: orderId={}, state={}",
                        accountId, message.getOrderId(), existing.getState());
                return null;
            }
            orderMapper.updateEventContext(
                    existing.getId(),
                    message.getSenderUserId(),
                    message.getSenderUserName(),
                    message.getSId(),
                    OrderStatus.PLATFORM_PENDING_SHIP
            );
            return existing.getId();
        }

        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(xianyuGoodsId);
        record.setXyGoodsId(message.getXyGoodsId());
        record.setPnmId(message.getPnmId());
        record.setBuyerUserId(message.getSenderUserId());
        record.setBuyerUserName(message.getSenderUserName());
        record.setSid(message.getSId());
        record.setOrderId(message.getOrderId());
        record.setOrderStatus(OrderStatus.PLATFORM_PENDING_SHIP);
        record.setContent(null);
        record.setState(OrderStatus.DELIVERY_PENDING);
        record.setConfirmState(0);

        try {
            int result = orderMapper.insert(record);
            if (result > 0) {
                log.info("【账号{}】✅ 创建订单记录成功: recordId={}, orderId={}", accountId, record.getId(), message.getOrderId());
                return record.getId();
            } else {
                log.error("【账号{}】❌ 创建订单记录失败: pnmId={}, orderId={}", accountId, message.getPnmId(), message.getOrderId());
                return null;
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                log.info("【账号{}】消息已处理过，跳过: pnmId={}, xyGoodsId={}", accountId, message.getPnmId(), message.getXyGoodsId());
                XianyuGoodsOrder duplicate = orderMapper.selectByPnmId(accountId, message.getPnmId());
                if (duplicate != null && OrderStatus.canStartDelivery(duplicate.getState())) {
                    return duplicate.getId();
                }
                return null;
            }
            throw new RuntimeException("创建订单记录失败", e);
        }
    }
}
