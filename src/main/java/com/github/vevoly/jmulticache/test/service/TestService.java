package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestUser;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.annotation.JMultiCacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestService {

    private final JMultiCache jMultiCache;

    // --- 模拟 DB 查询 ---
    TestUser mockDbQuery(Long id) {
        log.info(">>>>>> [DB Query] 正在查询数据库 id={}...", id);
        try { Thread.sleep(50); } catch (InterruptedException e) {}
        return new TestUser(id, "tenant001", 1L, "User-" + id, 18);
    }

    // --- 场景 1: 测试注解 ---
    // 假设你还没生成枚举，先用字符串
    @JMultiCacheable(configName = "TEST_USER_CACHE")
    public TestUser getUserByIdAnnotation(Long id) {
        return mockDbQuery(id);
    }

    // --- 场景 2: 测试手动调用 ---
    public TestUser getUserByIdManual(Long id) {
        return jMultiCache.fetchData("TEST_USER_CACHE", () -> mockDbQuery(id), String.valueOf(id));
    }

    @JMultiCacheable(configName = "TEST_USER_CACHE_BY_TENANT_ID")
    public TestUser getUserByTenantIdIdAnnotation(String tenantId, Long id) {
        return mockDbQuery(id);
    }

    public TestUser getUserByTenantIdIdManual(String tenantId, Long id) {
        return jMultiCache.fetchData("TEST_USER_CACHE_BY_TENANT_ID", () -> mockDbQuery(id), tenantId, String.valueOf(id));
    }
}
