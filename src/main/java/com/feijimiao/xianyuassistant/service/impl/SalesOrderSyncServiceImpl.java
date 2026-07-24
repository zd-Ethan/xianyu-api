package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.controller.dto.SalesSyncRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import com.feijimiao.xianyuassistant.entity.XianyuSalesSyncState;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuSalesSyncStateMapper;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.SalesOrderSyncService;
import com.feijimiao.xianyuassistant.service.order.SellerOrderPage;
import com.feijimiao.xianyuassistant.service.sales.SalesOrderParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class SalesOrderSyncServiceImpl implements SalesOrderSyncService {
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGE_COUNT = 2000;
    private static final int KNOWN_PAGE_WATERMARK = 2;

    @Autowired
    private OrderService orderService;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuSalesOrderMapper salesOrderMapper;

    @Autowired
    private XianyuSalesSyncStateMapper syncStateMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock salesClock;

    private final SalesOrderParser orderParser = new SalesOrderParser();
    private final Map<Long, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    @Override
    public SalesSyncRespDTO syncSalesOrders(Long accountId) {
        return syncSalesOrders(accountId, false);
    }

    private SalesSyncRespDTO syncSalesOrders(Long accountId, boolean incremental) {
        List<XianyuAccount> accounts;
        if (accountId == null) {
            accounts = accountMapper.selectList(null);
        } else {
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                throw new IllegalArgumentException("所选闲鱼账号不存在");
            }
            accounts = List.of(account);
        }

        SalesSyncRespDTO response = new SalesSyncRespDTO();
        response.setRequestedAccountCount(accounts.size());
        for (XianyuAccount account : accounts) {
            XianyuSalesSyncState state = syncStateMapper.selectByAccountId(account.getId());
            boolean hasCompletedSync = state != null
                    && state.getLastSuccessAt() != null
                    && !state.getLastSuccessAt().isBlank();
            AccountSyncResult result = syncAccount(account.getId(), incremental && hasCompletedSync);
            response.setSyncedOrderCount(response.getSyncedOrderCount() + result.orderCount());
            response.setDataQualityErrorCount(
                    response.getDataQualityErrorCount() + result.dataQualityErrorCount());
            if (result.skipped()) {
                response.setSkippedAccountCount(response.getSkippedAccountCount() + 1);
            } else if (result.success()) {
                response.setSuccessAccountCount(response.getSuccessAccountCount() + 1);
            } else {
                response.setFailedAccountCount(response.getFailedAccountCount() + 1);
            }
        }
        return response;
    }

    /** 高频同步沿已知订单水位停止；从未成功同步的账号仍会首次全量回溯。 */
    /*
     * 销售面板以本地订单为事实来源，不再自动调用闲鱼卖家订单接口。
     * 保留该方法供后续受控的人工核对流程复用。
     */
    public void scheduledIncrementalSync() {
        try {
            SalesSyncRespDTO result = syncSalesOrders(null, true);
            log.info("销售订单增量同步完成: accounts={}, success={}, failed={}, orders={}, qualityErrors={}",
                    result.getRequestedAccountCount(), result.getSuccessAccountCount(),
                    result.getFailedAccountCount(), result.getSyncedOrderCount(),
                    result.getDataQualityErrorCount());
        } catch (Exception exception) {
            log.error("销售订单增量同步异常", exception);
        }
    }

    /** 每日全量核对历史订单，使较早订单发生退款后也能从原付款日回减。 */
    public void scheduledFullSync() {
        try {
            SalesSyncRespDTO result = syncSalesOrders(null, false);
            log.info("销售订单全量同步完成: accounts={}, success={}, failed={}, orders={}, qualityErrors={}",
                    result.getRequestedAccountCount(), result.getSuccessAccountCount(),
                    result.getFailedAccountCount(), result.getSyncedOrderCount(),
                    result.getDataQualityErrorCount());
        } catch (Exception exception) {
            log.error("销售订单全量同步异常", exception);
        }
    }

    /** 分页同步单个账号；账号级锁避免手动同步和定时同步相互覆盖状态。 */
    private AccountSyncResult syncAccount(Long accountId, boolean incremental) {
        ReentrantLock lock = accountLocks.computeIfAbsent(accountId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return AccountSyncResult.skippedResult();
        }

        List<XianyuSalesOrder> ordersToPersist = new ArrayList<>();
        List<String> dataQualityErrors = new ArrayList<>();
        try {
            LocalDateTime startedAt = LocalDateTime.now(salesClock);
            syncStateMapper.markSyncing(accountId, startedAt.format(DB_DATE_TIME));
            Set<String> pageFingerprints = new HashSet<>();
            int consecutiveKnownPages = 0;
            for (int pageNumber = 1; pageNumber <= MAX_PAGE_COUNT; pageNumber++) {
                SellerOrderPage page = orderService.querySellerOrderPage(accountId, "ALL", pageNumber, PAGE_SIZE);
                if (!page.success()) {
                    throw new IllegalStateException(page.errorMessage());
                }

                String fingerprint = buildPageFingerprint(page.items());
                if (!page.items().isEmpty() && !pageFingerprints.add(fingerprint)) {
                    throw new IllegalStateException("平台重复返回同一订单页，已停止同步");
                }

                List<String> orderIds = extractOrderIds(page.items());
                Set<String> existingOrderIds = orderIds.isEmpty()
                        ? Set.of()
                        : new HashSet<>(salesOrderMapper.selectExistingOrderIds(accountId, orderIds));
                boolean isKnownPage = !orderIds.isEmpty()
                        && existingOrderIds.size() == orderIds.size();
                consecutiveKnownPages = isKnownPage ? consecutiveKnownPages + 1 : 0;

                LocalDateTime syncedAt = LocalDateTime.now(salesClock);
                for (int itemIndex = 0; itemIndex < page.items().size(); itemIndex++) {
                    Map<String, Object> source = page.items().get(itemIndex);
                    XianyuSalesOrder order = orderParser.parse(accountId, source, syncedAt);
                    if (order == null) {
                        dataQualityErrors.add("第 " + pageNumber + " 页第 " + (itemIndex + 1) + " 条订单缺少订单 ID");
                        continue;
                    }
                    String qualityError = resolveDataQualityError(
                            order, existingOrderIds.contains(order.getOrderId()));
                    if (!qualityError.isBlank()) {
                        dataQualityErrors.add("订单 " + order.getOrderId() + "：" + qualityError);
                    }
                    ordersToPersist.add(order);
                }

                boolean reachedWatermark = incremental && consecutiveKnownPages >= KNOWN_PAGE_WATERMARK;
                if (page.items().isEmpty() || !page.hasMore() || reachedWatermark) {
                    ensureNoDataQualityErrors(dataQualityErrors);
                    String successAt = LocalDateTime.now(salesClock).format(DB_DATE_TIME);
                    persistCompletedSync(accountId, ordersToPersist, successAt, incremental);
                    return AccountSyncResult.success(ordersToPersist.size());
                }
            }
            throw new IllegalStateException("平台订单超过同步页数上限");
        } catch (Exception exception) {
            String errorMessage = normalizeErrorMessage(exception.getMessage());
            int dataQualityErrorCount = exception instanceof SalesDataQualityException qualityException
                    ? qualityException.getErrorCount()
                    : 0;
            if (accountMapper.selectById(accountId) != null) {
                syncStateMapper.markFailed(
                        accountId, LocalDateTime.now(salesClock).format(DB_DATE_TIME),
                        errorMessage, dataQualityErrorCount);
            }
            log.warn("【账号{}】销售订单同步失败: {}", accountId, errorMessage);
            return AccountSyncResult.failed(dataQualityErrorCount);
        } finally {
            lock.unlock();
        }
    }

    private String buildPageFingerprint(List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return "empty";
        }
        return extractOrderId(items.getFirst()) + ":" + extractOrderId(items.getLast()) + ":" + items.size();
    }

    private String extractOrderId(Map<String, Object> item) {
        String orderId = orderParser.resolveOrderId(item);
        return orderId == null ? "unknown" : orderId;
    }

    private List<String> extractOrderIds(List<Map<String, Object>> items) {
        return items.stream()
                .map(this::extractOrderId)
                .filter(orderId -> !"unknown".equals(orderId))
                .distinct()
                .toList();
    }

    /** 校验订单是否足以安全更新已有事实或创建新的销售事实。 */
    private String resolveDataQualityError(XianyuSalesOrder order, boolean existingOrder) {
        List<String> errors = new ArrayList<>();
        if (order.getDataQualityError() != null && !order.getDataQualityError().isBlank()) {
            errors.add(order.getDataQualityError());
        }
        if (!existingOrder) {
            if (order.getPaidAt() == null) {
                errors.add("缺少有效付款时间");
            }
            if (!order.isQuantityProvided() && errors.stream().noneMatch(error -> error.contains("购买数量"))) {
                errors.add("缺少购买数量");
            } else if (order.isQuantityProvided() && order.getQuantity() <= 0) {
                errors.add("购买数量必须大于 0");
            }
            if (!order.isGrossAmountProvided() && errors.stream().noneMatch(error -> error.contains("订单金额"))) {
                errors.add("缺少订单金额");
            }
        }
        return String.join("；", errors);
    }

    private void ensureNoDataQualityErrors(List<String> errors) {
        if (errors.isEmpty()) {
            return;
        }
        throw new SalesDataQualityException(
                errors.size(), "发现 " + errors.size() + " 条异常订单，首条：" + errors.getFirst());
    }

    /**
     * 在所有分页均成功后，用短事务提交订单和同步状态。
     *
     * @param accountId 闲鱼账号 ID。
     * @param orders 本轮完整拉取并解析的订单。
     * @param successAt 本轮完成时间。
     * @param incremental 是否为增量同步。
     */
    private void persistCompletedSync(
            Long accountId,
            List<XianyuSalesOrder> orders,
            String successAt,
            boolean incremental
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            if (accountMapper.selectById(accountId) == null) {
                throw new IllegalStateException("闲鱼账号已删除，停止写入销售数据");
            }
            for (XianyuSalesOrder order : orders) {
                salesOrderMapper.upsert(order);
            }
            syncStateMapper.markSuccess(accountId, successAt, orders.size(), 0, incremental);
        });
    }

    private String normalizeErrorMessage(String message) {
        String value = message == null || message.isBlank() ? "未知同步错误" : message.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record AccountSyncResult(
            boolean success,
            boolean skipped,
            int orderCount,
            int dataQualityErrorCount
    ) {
        private static AccountSyncResult success(int orderCount) {
            return new AccountSyncResult(true, false, orderCount, 0);
        }

        private static AccountSyncResult failed(int dataQualityErrorCount) {
            return new AccountSyncResult(false, false, 0, dataQualityErrorCount);
        }

        private static AccountSyncResult skippedResult() {
            return new AccountSyncResult(false, true, 0, 0);
        }
    }

    /** 表示平台订单数据不完整，整轮同步不得提交。 */
    private static class SalesDataQualityException extends IllegalStateException {
        private final int errorCount;

        private SalesDataQualityException(int errorCount, String message) {
            super(message);
            this.errorCount = errorCount;
        }

        private int getErrorCount() {
            return errorCount;
        }
    }
}
