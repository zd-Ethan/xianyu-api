package com.feijimiao.xianyuassistant.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailNotifyServiceImplTest {

    @Test
    void shouldReserveEachDisconnectCycleBeforeAsyncSending() {
        EmailNotifyServiceImpl service = new EmailNotifyServiceImpl();

        Long firstToken = service.reserveWsDisconnectNotify(12L);
        assertNotNull(firstToken);
        assertNull(service.reserveWsDisconnectNotify(12L));

        service.resetWsDisconnectNotifyState(12L);
        Long secondToken = service.reserveWsDisconnectNotify(12L);
        assertNotNull(secondToken);
        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void shouldIgnoreQueuedNotificationAfterConnectionRecovered() {
        EmailNotifyServiceImpl service = new EmailNotifyServiceImpl();
        Long token = service.reserveWsDisconnectNotify(21L);

        service.resetWsDisconnectNotifyState(21L);

        assertDoesNotThrow(() -> service.sendWsDisconnectNotifyEmail(21L, "测试账号", token));
    }
}
