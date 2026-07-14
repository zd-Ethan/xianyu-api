package com.feijimiao.xianyuassistant.service.order;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DeliveryLockManager {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public String key(Long accountId, String orderId, Long recordId) {
        String orderPart = orderId == null || orderId.isBlank() ? "record:" + recordId : "order:" + orderId;
        return accountId + ":" + orderPart;
    }

    public boolean tryLock(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        return lock.tryLock();
    }

    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock == null) {
            return;
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            locks.remove(key, lock);
        }
    }
}
