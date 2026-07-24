package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuSalesOrder;
import com.feijimiao.xianyuassistant.entity.XianyuSalesSyncState;
import com.feijimiao.xianyuassistant.entity.bo.SalesSummaryBO;
import com.feijimiao.xianyuassistant.service.AccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "sales.sync.initial-delay-ms=3600000"
        }
)
class XianyuSalesOrderMapperIntegrationTest {
    private static final DateTimeFormatter DB_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path TEST_DIRECTORY = Path.of(
            "target", "sales-mapper-test-" + UUID.randomUUID());
    private static final Path TEST_DATABASE = TEST_DIRECTORY.resolve("sales.db");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DATABASE);
    }

    @AfterAll
    static void deleteTestDatabase() throws Exception {
        if (!Files.exists(TEST_DIRECTORY)) {
            return;
        }
        try (var paths = Files.walk(TEST_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuSalesOrderMapper salesOrderMapper;

    @Autowired
    private XianyuSalesSyncStateMapper salesSyncStateMapper;

    @Autowired
    private AccountService accountService;

    @Test
    void shouldUpsertAndAggregateNetSales() {
        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("sales-mapper-test-" + System.nanoTime());
        account.setStatus(1);
        accountMapper.insert(account);

        LocalDate today = LocalDate.now();
        XianyuSalesOrder paidOrder = buildOrder(account.getId(), "PAID-" + System.nanoTime(), today, "pending_ship", 3, 3000L, 0, 0L);
        XianyuSalesOrder refundedOrder = buildOrder(account.getId(), "REFUNDED-" + System.nanoTime(), today, "refunded", 1, 1000L, 1, 1000L);
        XianyuSalesOrder partialRefundOrder = buildOrder(account.getId(), "PARTIAL-" + System.nanoTime(), today, "refunded", 3, 3000L, 1, 1000L);
        XianyuSalesOrder refundingOrder = buildOrder(account.getId(), "REFUNDING-" + System.nanoTime(), today, "refunding", 1, 1000L, 0, 0L);
        salesOrderMapper.upsert(paidOrder);
        salesOrderMapper.upsert(refundedOrder);
        salesOrderMapper.upsert(partialRefundOrder);
        salesOrderMapper.upsert(refundingOrder);

        paidOrder.setQuantity(4);
        paidOrder.setGrossAmountCents(4000L);
        paidOrder.setRefundedQuantity(1);
        paidOrder.setRefundedAmountCents(1000L);
        salesOrderMapper.upsert(paidOrder);

        XianyuSalesOrder incompleteOrder = buildOrder(
                account.getId(), paidOrder.getOrderId(), today, "unknown", 0, 0L, 0, 0L);
        incompleteOrder.setPlatformStatusProvided(false);
        incompleteOrder.setQuantityProvided(false);
        incompleteOrder.setGrossAmountProvided(false);
        incompleteOrder.setRefundedQuantityProvided(false);
        incompleteOrder.setRefundedAmountProvided(false);
        salesOrderMapper.upsert(incompleteOrder);

        XianyuSalesOrder storedOrder = salesOrderMapper.selectOne(
                new LambdaQueryWrapper<XianyuSalesOrder>()
                        .eq(XianyuSalesOrder::getXianyuAccountId, account.getId())
                        .eq(XianyuSalesOrder::getOrderId, paidOrder.getOrderId()));

        List<String> existingOrderIds = salesOrderMapper.selectExistingOrderIds(
                account.getId(), List.of(paidOrder.getOrderId(), refundedOrder.getOrderId(), "NOT-EXIST"));
        SalesSummaryBO summary = salesOrderMapper.selectSummary(
                account.getId(), today.toString(), today.minusDays(6).toString(),
                today.minusDays(29).toString(), today.withDayOfMonth(1).toString(),
                today.withDayOfMonth(today.lengthOfMonth()).toString());
        List<String> refreshOrderIds = salesOrderMapper.selectOrderIdsForStatusRefresh(
                account.getId(), 20);

        assertEquals(2, existingOrderIds.size());
        assertEquals("pending_ship", storedOrder.getPlatformStatus());
        assertEquals(4, storedOrder.getQuantity());
        assertEquals(4000L, storedOrder.getGrossAmountCents());
        assertEquals(6L, summary.getTotalQuantity());
        assertEquals(6L, summary.getTodayQuantity());
        assertEquals(6000L, summary.getTotalAmountCents());
        assertTrue(refreshOrderIds.contains(paidOrder.getOrderId()));
        assertFalse(refreshOrderIds.contains(refundedOrder.getOrderId()));
    }

    @Test
    void shouldInferWholeRefundFromExistingFactWhenDetailOnlyHasStatus() {
        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("status-only-refund-test-" + System.nanoTime());
        account.setStatus(1);
        accountMapper.insert(account);

        LocalDate today = LocalDate.now();
        XianyuSalesOrder paidOrder = buildOrder(
                account.getId(), "STATUS-ONLY-REFUND-" + System.nanoTime(), today,
                "completed", 2, 2500L, 0, 0L);
        salesOrderMapper.upsert(paidOrder);

        XianyuSalesOrder refundUpdate = buildOrder(
                account.getId(), paidOrder.getOrderId(), today,
                "refunded", 0, 0L, 0, 0L);
        refundUpdate.setPaidAt(null);
        refundUpdate.setPaidDate(null);
        refundUpdate.setQuantityProvided(false);
        refundUpdate.setGrossAmountProvided(false);
        refundUpdate.setRefundedQuantityProvided(false);
        refundUpdate.setRefundedAmountProvided(false);
        salesOrderMapper.upsert(refundUpdate);

        XianyuSalesOrder storedOrder = salesOrderMapper.selectOne(
                new LambdaQueryWrapper<XianyuSalesOrder>()
                        .eq(XianyuSalesOrder::getXianyuAccountId, account.getId())
                        .eq(XianyuSalesOrder::getOrderId, paidOrder.getOrderId()));
        SalesSummaryBO summary = salesOrderMapper.selectSummary(
                account.getId(), today.toString(), today.minusDays(6).toString(),
                today.minusDays(29).toString(), today.withDayOfMonth(1).toString(),
                today.withDayOfMonth(today.lengthOfMonth()).toString());

        assertEquals(2, storedOrder.getRefundedQuantity());
        assertEquals(2500L, storedOrder.getRefundedAmountCents());
        assertEquals(0L, summary.getTotalQuantity());
        assertEquals(0L, summary.getTotalAmountCents());
    }

    @Test
    void shouldRotateStatusRefreshAfterAttempt() {
        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("status-refresh-rotation-test-" + System.nanoTime());
        account.setStatus(1);
        accountMapper.insert(account);

        XianyuSalesOrder firstOrder = buildOrder(
                account.getId(), "REFRESH-FIRST-" + System.nanoTime(), LocalDate.of(2026, 7, 1),
                "completed", 1, 1000L, 0, 0L);
        firstOrder.setLastSyncedAt("2026-07-01 00:00:00");
        XianyuSalesOrder secondOrder = buildOrder(
                account.getId(), "REFRESH-SECOND-" + System.nanoTime(), LocalDate.of(2026, 7, 2),
                "completed", 1, 1000L, 0, 0L);
        secondOrder.setLastSyncedAt("2026-07-02 00:00:00");
        salesOrderMapper.upsert(firstOrder);
        salesOrderMapper.upsert(secondOrder);

        assertEquals(firstOrder.getOrderId(),
                salesOrderMapper.selectOrderIdsForStatusRefresh(account.getId(), 1).getFirst());

        salesOrderMapper.markStatusRefreshAttempt(
                account.getId(), firstOrder.getOrderId(), "2026-07-03 00:00:00");

        assertEquals(secondOrder.getOrderId(),
                salesOrderMapper.selectOrderIdsForStatusRefresh(account.getId(), 1).getFirst());
    }

    @Test
    void shouldDeleteSalesFactsWithAccount() {
        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("sales-delete-test-" + System.nanoTime());
        account.setStatus(1);
        accountMapper.insert(account);

        XianyuSalesOrder order = buildOrder(
                account.getId(), "DELETE-" + System.nanoTime(), LocalDate.of(2026, 7, 22),
                "completed", 1, 1000L, 0, 0L);
        salesOrderMapper.upsert(order);
        salesSyncStateMapper.markSyncing(account.getId(), order.getLastSyncedAt());

        accountService.deleteAccountAndRelatedData(account.getId());

        assertEquals(0, salesOrderMapper.selectCount(
                new LambdaQueryWrapper<XianyuSalesOrder>()
                        .eq(XianyuSalesOrder::getXianyuAccountId, account.getId())));
        assertEquals(null, salesSyncStateMapper.selectByAccountId(account.getId()));
    }

    @Test
    void shouldTrackIncrementalAndFullSyncTimesSeparately() {
        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("sales-sync-state-test-" + System.nanoTime());
        account.setStatus(1);
        accountMapper.insert(account);

        salesSyncStateMapper.markSyncing(account.getId(), "2026-07-22 03:20:00");
        salesSyncStateMapper.markSuccess(account.getId(), "2026-07-22 03:21:00", 30, 0, false);
        salesSyncStateMapper.markSyncing(account.getId(), "2026-07-22 03:25:00");
        salesSyncStateMapper.markSuccess(account.getId(), "2026-07-22 03:26:00", 10, 0, true);

        XianyuSalesSyncState state = salesSyncStateMapper.selectByAccountId(account.getId());
        assertEquals("2026-07-22 03:26:00", state.getLastSuccessAt());
        assertEquals("2026-07-22 03:26:00", state.getLastIncrementalSuccessAt());
        assertEquals("2026-07-22 03:21:00", state.getLastFullSuccessAt());
        assertEquals(0, state.getDataQualityErrorCount());
    }

    private XianyuSalesOrder buildOrder(
            Long accountId,
            String orderId,
            LocalDate paidDate,
            String platformStatus,
            int quantity,
            long amountCents,
            int refundedQuantity,
            long refundedAmountCents
    ) {
        String syncedAt = LocalDateTime.now().format(DB_DATE_TIME);
        XianyuSalesOrder order = new XianyuSalesOrder();
        order.setXianyuAccountId(accountId);
        order.setOrderId(orderId);
        order.setPlatformStatus(platformStatus);
        order.setRawStatus(platformStatus);
        order.setPaidAt(paidDate.atStartOfDay().format(DB_DATE_TIME));
        order.setPaidDate(paidDate.toString());
        order.setQuantity(quantity);
        order.setGrossAmountCents(amountCents);
        order.setRefundedQuantity(refundedQuantity);
        order.setRefundedAmountCents(refundedAmountCents);
        order.setLastSyncedAt(syncedAt);
        return order;
    }

}
