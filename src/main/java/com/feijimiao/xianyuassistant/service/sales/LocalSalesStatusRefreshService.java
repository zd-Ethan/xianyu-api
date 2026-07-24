package com.feijimiao.xianyuassistant.service.sales;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import com.feijimiao.xianyuassistant.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 低频核对本地已观察订单状态，覆盖发货后退款等不会再次进入待发货列表的变化。 */
@Slf4j
@Service
public class LocalSalesStatusRefreshService {
    private static final int MAX_ORDERS_PER_ACCOUNT = 20;
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuSalesOrderMapper salesOrderMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private Clock salesClock;

    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void scheduledRefresh() {
        refreshObservedOrders();
    }

    /** 只刷新事实表中本地已观察且尚未进入退款终态的订单。 */
    public void refreshObservedOrders() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        for (XianyuAccount account : accounts) {
            if (account == null || account.getId() == null) {
                continue;
            }
            List<String> orderIds = salesOrderMapper.selectOrderIdsForStatusRefresh(
                    account.getId(), MAX_ORDERS_PER_ACCOUNT);
            if (orderIds == null || orderIds.isEmpty()) {
                continue;
            }
            for (String orderId : orderIds) {
                if (orderId == null || orderId.isBlank()) {
                    continue;
                }
                try {
                    // 订单服务会在详情请求成功后写入销售事实，此处无需重复写入。
                    orderService.getOrderDetailMap(account.getId(), orderId);
                } catch (Exception exception) {
                    log.debug("【账号{}】刷新销售订单状态失败: orderId={}, error={}",
                            account.getId(), orderId, exception.getMessage());
                } finally {
                    markRefreshAttempt(account.getId(), orderId);
                }
            }
        }
    }

    /** 记录本轮核对时间，使成功和失败的订单都能公平轮转。 */
    private void markRefreshAttempt(Long accountId, String orderId) {
        try {
            String attemptedAt = LocalDateTime.now(salesClock).format(DB_DATE_TIME);
            salesOrderMapper.markStatusRefreshAttempt(accountId, orderId, attemptedAt);
        } catch (Exception exception) {
            log.debug("【账号{}】记录销售订单核对时间失败: orderId={}, error={}",
                    accountId, orderId, exception.getMessage());
        }
    }
}
