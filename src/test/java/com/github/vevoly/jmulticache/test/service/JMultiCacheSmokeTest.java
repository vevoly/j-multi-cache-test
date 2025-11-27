package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestUser;
import io.github.vevoly.jmulticache.api.JMultiCacheAdmin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JMultiCacheSmokeTest {

    @Autowired
    private TestService testService;

    @Autowired
    private JMultiCacheAdmin jMultiCacheAdmin;

    @Test
    void testCacheFlow() {
        Long userId = 1001L;

        // 0. 清理环境
        jMultiCacheAdmin.evict("TEST_USER_CACHE", userId);
        System.out.println("--------------------------------------------------");

        // 1. 第一次查询：应该走 DB，并回填 L2 + L1
        System.out.println("Step 1: 第一次查询 (Expect: DB hit)");
        TestUser user1 = testService.getUserByIdAnnotation(userId);
        Assertions.assertNotNull(user1);

        // 2. 第二次查询：应该走 L1 (Caffeine)
        System.out.println("\nStep 2: 第二次查询 (Expect: L1 hit)");
        TestUser user2 = testService.getUserByIdAnnotation(userId);
        Assertions.assertEquals(user1.getName(), user2.getName());

        // 3. 手动调用测试：应该也能命中缓存 (因为 Key 生成规则一致)
        System.out.println("\nStep 3: 手动 API 查询 (Expect: L1 hit)");
        TestUser user3 = testService.getUserByIdManual(userId);
        Assertions.assertEquals(user1.getName(), user3.getName());

        // 4. 模拟 缓存 失效
        jMultiCacheAdmin.evict("TEST_USER_CACHE", userId);
        System.out.println("--------------------------------------------------");
        System.out.println("\nStep 4: 手动 API 查询 (Expect: DB hit)");
        TestUser user4 = testService.getUserByIdManual(userId);
        Assertions.assertEquals(user1.getName(), user4.getName());

        // 5. 模拟 缓存L1 失效
        jMultiCacheAdmin.evictL1("TEST_USER_CACHE", userId);
        System.out.println("--------------------------------------------------");
        System.out.println("\nStep 5: 手动 API 查询 (Expect: L2 hit)");
        TestUser user5 = testService.getUserByIdManual(userId);
        Assertions.assertEquals(user1.getName(), user5.getName());


        System.out.println("--------------------------------------------------");
    }
}
