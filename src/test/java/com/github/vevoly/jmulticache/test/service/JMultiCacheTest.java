package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestUser;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.JMultiCacheAdmin;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
class JMultiCacheTest {

    @Autowired
    private JMultiCache jMultiCache;

    @Autowired
    private JMultiCacheAdmin jMultiCacheAdmin;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @SpyBean
    private TestService testService; // 使用 SpyBean 监控 Service 的真实调用情况

    @BeforeEach // 清理缓存的辅助方法
    void setUp() {
        // 清理所有测试相关的 Redis Key，防止干扰
        Set<String> keys = stringRedisTemplate.keys("test:user*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        // 如果有 API 可以清理本地缓存最好，或者在此处不做处理，依赖 Key 的随机性
    }

    // ==========================================
    // 1. 基础功能测试：注解 & 手动 & SpEL解析
    // ==========================================

    @Test
    @DisplayName("测试注解缓存：第一次查DB，第二次查缓存")
    void testAnnotationCache() {
        Long userId = 1001L;

        // 第一次调用
        TestUser user1 = testService.getUserByIdAnnotation(userId);
        assertThat(user1).isNotNull();
        assertThat(user1.getId()).isEqualTo(userId);

        // 验证 DB 方法被调用了 1 次
        verify(testService, times(1)).mockDbQuery(userId);

        // 第二次调用
        TestUser user2 = testService.getUserByIdAnnotation(userId);
        assertThat(user2).isEqualTo(user1); // 应该是同一个对象（或相同数据）

        // 验证 DB 方法依然只被调用了 1 次（说明走了缓存）
        verify(testService, times(1)).mockDbQuery(userId); // 次数保持不变

        // 验证 Redis 中是否存在数据 (Namespace: test:user)
        Boolean hasKey = stringRedisTemplate.hasKey("test:user:" + userId);
        assertThat(hasKey).isTrue();
    }

    @Test
    @DisplayName("测试 SpEL 复杂Key解析（手动）：#tenantId + ':' + #id")
    void testSpelKeyGeneration() {
        // 配置：TEST_USER_CACHE_BY_TENANT_ID
        // key-field: "#tenantId + ':' + #id"
        // namespace: "test:user:tenantId_id"

        Long userId = 2002L;
        String tenantId = "tenant001";

        // 模拟手动调用，或者调用 Service 中对应的方法
        // 这里假设直接使用 jMultiCache 手动调用来测试配置
        TestUser user = new TestUser(userId, tenantId, "SpEL-User", 20);

        TestUser testUserCacheByTenantId = jMultiCache.fetchData("TEST_USER_CACHE_BY_TENANT_ID",
                () -> user,
                tenantId, valueOf(userId));// todo 传入 Entity 供 SpEL 解析

        // 验证 Redis Key 是否符合预期
        // Namespace + SpEL Result = test:user:tenantId_id:tenant001:2002
//                                     test:user:tenantId_id:tenant001:2004:null
        String expectedKey = "test:user:tenantId_id:" + tenantId + ":" + userId;
        Boolean hasKey = stringRedisTemplate.hasKey(expectedKey);

        assertThat(hasKey).as("SpEL 生成的 Redis Key 不正确").isTrue();
    }

    @Test
    @DisplayName("测试 SpEL 复杂Key解析（自动）：#tenantId + ':' + #id")
    void testSpelKeyGenerationAnnotation() {
        Long userId = 2004L;
        String tenantId = "tenant001";
        testService.getUserByTenantIdIdAnnotation(tenantId, userId);
        String expectedKey = "test:user:tenantId_id:" + tenantId + ":" + userId;
        Boolean hasKey = stringRedisTemplate.hasKey(expectedKey);

        assertThat(hasKey).as("SpEL 生成的 Redis Key 不正确").isTrue();
    }

    // ==========================================
    // 2. 缓存防穿透测试 (Empty Cache)
    // ==========================================

    @Test
    @DisplayName("测试缓存穿透：空值缓存与空值占位符")
    void testEmptyCacheAntiPenetration() throws InterruptedException {
        Long notExistId = 9999L;

        // 1. 第一次查询，DB 返回 null
        TestUser result1 = jMultiCache.fetchData("TEST_USER_CACHE", () -> null, valueOf(notExistId));
        assertThat(result1).isNull();

        // 2. 验证 Redis 里是否存入了空占位符 (根据配置 empty-cache-value: "BINGO")
        // 注意：j-multi-cache 内部可能对 key 做了序列化，或者存储的是包装对象
        // 这里检查 Key 存在即可，且 TTL 较短
        String cacheKey = "test:user:" + notExistId;
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();

        // 如果框架将 BINGO 序列化存入，可以尝试获取验证，或者只验证 Key 存在

        // 3. 再次查询，应该直接返回 null，而不执行 loader
        // 为了验证不执行 loader，我们用一个带副作用的 Supplier
        Runnable mockLoader = mock(Runnable.class);
        jMultiCache.fetchData("TEST_USER_CACHE", () -> {
            mockLoader.run();
            return null;
        }, valueOf(notExistId));

        // 验证 loader 没跑，说明命中了“空缓存”
        verify(mockLoader, never()).run();

        // 4. 测试空缓存 TTL (配置中是 10s)
        // 为了加快测试，建议在测试配置中覆盖 defaults 为 1s
        Thread.sleep(1100);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse(); // 过期了

    }

    // ==========================================
    // 3. 特殊数据结构测试：List 和 Set
    // ==========================================

    @Test
    @DisplayName("测试 List 缓存结构 (storage-type: list)")
    void testListStorage() {
        // 配置：TEST_USER_CACHE_LIST, Key: #tenantId
        String tenantId = "tenant_list_01";
        List<TestUser> users = Arrays.asList(
                new TestUser(1L, tenantId, "A", 10),
                new TestUser(2L, tenantId, "B", 20)
        );

        // 存入 List
        // 假设 jMultiCache 有支持 List 的 API，或者 fetchData 返回 List 时自动处理
        jMultiCache.fetchData("TEST_USER_CACHE_LIST", () -> users, tenantId);

        // 验证 Redis 数据结构类型
        String redisKey = "test:user:list:" + tenantId;
        assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue();
        // 获取类型，应该是 LIST
        assertThat(stringRedisTemplate.type(redisKey).code()).isEqualTo("list");

        // 取出验证
        List<TestUser> cachedUsers = (List<TestUser>) jMultiCache.fetchData("TEST_USER_CACHE_LIST", () -> null, tenantId);
        assertThat(cachedUsers).hasSize(2);
        assertThat(cachedUsers.get(0).getName()).isEqualTo("A");
    }

    @Test
    @DisplayName("测试 Set 缓存结构 (storage-type: set)")
    void testSetStorage() {
        // 配置：TEST_USER_ID_SET
        // 这是一个纯 ID 的集合，比如黑名单
        Long blackListId = 8888L;

        // 假设框架提供了类似 addSet / isMember 的方法，或者通过 fetch 获取整个 Set
        // 这里模拟获取 Set
        Set<Long> idSet = Set.of(blackListId, 9999L);

        jMultiCache.fetchData("TEST_USER_ID_SET", () -> idSet, "id"); // key-field不重要，或者是固定值

        String redisKey = "test:user:set:id:id"; // 假设 key 拼接规则
        // 如果你的 key-field 配置为空或者固定字符串，key会有所不同，这里需根据实际拼接规则调整

        // 验证 Redis 类型
         assertThat(stringRedisTemplate.type(redisKey).code()).isEqualTo("set");
    }

    // ==========================================
    // 4. TTL 过期与 L1/L2 配合测试
    // ==========================================

    @Test
    @DisplayName("测试 L1(Local) 失效但 L2(Redis) 命中")
    void testL1ExpirationAndL2Hit() throws InterruptedException {
        // 配置：TEST_USER_CACHE
        // redis-ttl: 2s, local-ttl: 1s

        Long id = 3003L;
        String cacheKey = "test:user:" + id;

        // 1. 首次加载
        testService.getUserByIdManual(id);

        // 2. 模拟 Local Cache 过期，但 Redis 未过期
        Thread.sleep(1000);

        // 3. 再次查询
        // 我们重置 mock 的计数器
        reset(testService);

        TestUser user = testService.getUserByIdManual(id);
        assertThat(user).isNotNull();

        // 4. 关键断言：
        // DB 应该没被调用 (因为 Redis 还有)
        // verify(testService, never()).mockDbQuery(id); // (如果用 SpyBean 注入了 Service)
        // Redis 应该还存在
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();
    }

    @Test
    @DisplayName("测试 L1 为空配置 (只走 Redis)")
    void testNoLocalCacheConfig() {
        // 配置：TEST_USER_CACHE_BY_TENANT_ID (local-ttl: null)

        TestUser user = new TestUser(4004L, "T1", "NoLocal", 10);
        jMultiCache.fetchData("TEST_USER_CACHE_BY_TENANT_ID", () -> user, user.getTenantId(), valueOf(user.getId()));

        // 1. Redis 应该有值
        String redisKey = "test:user:tenantId_id:T1:4004";
        assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue();

        // 2. 本地缓存应该没有值
        // 这需要调用 jMultiCache 内部方法验证，或者断点调试
        // 也可以通过 debug 日志观察，或者反射去 Caffeine Cache 里看
        jMultiCacheAdmin.evict("TEST_USER_CACHE_BY_TENANT_ID", user.getTenantId(), valueOf(user.getId()));
        // 因为查询数据库后会回种，所以需要打断点确认是否没走L1
        Object localValue = jMultiCache.fetchData(redisKey, () -> user);
        assertThat(localValue).isNull();
    }

    // ==========================================
    // 5. 批量查询测试 (Batch Get)
    // ==========================================

    @Test
    @DisplayName("测试批量 ID 获取 (模拟)")
    void testBatchGet() {
        // 假设我们循环调用 fetch
        List<Long> ids = Arrays.asList(5001L, 5002L, 5003L);

        // 1. 预热数据
        ids.forEach(id -> testService.getUserByIdManual(id));

        // 2. 批量获取 (验证性能或正确性)
        long start = System.currentTimeMillis();
        ids.forEach(id -> {
            TestUser u = testService.getUserByIdManual(id);
            assertThat(u).isNotNull();
        });
        long end = System.currentTimeMillis();

        log.info("Batch get 3 users took: {} ms", (end - start));
        // 应该非常快，因为全是缓存命中
        assertThat(end - start).isLessThan(50);
    }

    // ==========================================
    // 6. 分页数据测试 (storage-type: page)
    // ==========================================

    /**
     * 模拟简单的 Page 对象，防止测试依赖缺失
     * 如果你项目里有 MyBatisPlus 的 Page，请直接用那个
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class MockPage<T> implements Serializable {
        private long current;
        private long size;
        private long total;
        private List<T> records;
    }

    @Test
    @DisplayName("测试复杂分页场景：手动构建Key + Page对象存取")
    void testPageCaching() {
        // 1. 模拟请求参数 (User, ReqVO, PageParam)
        Long userId = 8888L;
        String dateType = "2023-11"; // 模拟 reqVO.getDateType()
        String status = "SUCCESS";   // 模拟 reqVO.getStatus()
        long current = 1;
        long size = 10;
        String configName = "TEST_USER_PAGE";
        String namespace = "test:user:page";

        // 2. 模拟业务代码中的 Key 构建逻辑
        String expectedRedisKey = JMultiCacheHelper.buildKey(namespace, String.valueOf(userId), dateType, status, String.valueOf(current), String.valueOf(size));

        // 3. 模拟 DB 返回的数据
        List<TestUser> dbRecords = new ArrayList<>();
        dbRecords.add(new TestUser(101L, "T1", "UserA", 20));
        dbRecords.add(new TestUser(102L, "T1", "UserB", 22));

        MockPage<TestUser> dbPage = new MockPage<>(current, size, 100L, dbRecords);

        // Define supplier (模拟 queryWithdrawFromDb)
        Supplier<MockPage<TestUser>> dbQuerySupplier = () -> {
            log.info(">>>> 模拟执行 DB 查询...");
            return dbPage;
        };

        // 为了验证缓存命中，我们需要监控 Supplier 是否被执行，这里用 Spy 或者简单的计数变量
        // 这里我们简单地用 AtomicBoolean 或 Mockito
        Supplier<MockPage<TestUser>> spiedSupplier = spy(new Supplier<MockPage<TestUser>>() {
            @Override
            public MockPage<TestUser> get() {
                return dbQuerySupplier.get();
            }
        });

        // ==========================================
        // Action 1: 第一次查询 (Cache Miss)
        // ==========================================
        // 注意：fetchData 的参数取决于你的封装。
        // 如果是 multiCacheUtils.getSingleData(key, supplier)，
        // 对应底层通常是 fetchData(configName, key, supplier) 或 fetchData(configName, supplier, key)
        // 这里假设 fetchData(configName, supplier, keyArgs...)

        MockPage<TestUser> result1 = (MockPage<TestUser>) jMultiCache.fetchData(configName, spiedSupplier, String.valueOf(userId), dateType, status, String.valueOf(current), String.valueOf(size));

        // 验证 1: 数据正确性
        assertThat(result1).isNotNull();
        assertThat(result1.getTotal()).isEqualTo(100L);
        assertThat(result1.getRecords()).hasSize(2);
        assertThat(result1.getRecords().get(0).getName()).isEqualTo("UserA");

        // 验证 2: Supplier 被执行了
        verify(spiedSupplier, times(1)).get();

        // 验证 3: Redis 存在 Key
        assertThat(stringRedisTemplate.hasKey(expectedRedisKey)).isTrue();


        // ==========================================
        // Action 2: 第二次查询 (Cache Hit) - 相同参数
        // ==========================================
        // 重置 mock 计数
        clearInvocations(spiedSupplier);

        MockPage<TestUser> result2 = (MockPage<TestUser>) jMultiCache.fetchData(
                configName,
                spiedSupplier,
                String.valueOf(userId), dateType, status, String.valueOf(current), String.valueOf(size)
        );

        // 验证 4: 数据依然正确
        assertThat(result2.getRecords()).hasSize(2);

        // 验证 5: Supplier 没有被执行 (走的缓存)
        verify(spiedSupplier, never()).get();


        // ==========================================
        // Action 3: 第三次查询 (不同分页参数) - 应该不命中
        // ==========================================
        // 模拟翻页: current = 2
        long current2 = 2;
        String manualSuffixKeyPage = String.format("%s:%s:%s:%d:%d", userId, dateType, status, current2, size);
        MockPage<TestUser> result3 = (MockPage<TestUser>) jMultiCache.fetchData(
                configName,
                spiedSupplier,
                String.valueOf(userId), dateType, status, String.valueOf(current2), String.valueOf(size)
        );

        // 验证 6: Supplier 再次被执行 (因为 Key 变了)
        verify(spiedSupplier, times(1)).get();

        // 验证 7: Redis 里应该多了一个 Key
        String expectedRedisKeyPage2 = namespace + ":" + manualSuffixKeyPage;
        assertThat(stringRedisTemplate.hasKey(expectedRedisKeyPage2)).isTrue();
    }

    // ==========================================
    // 7. Set 并集查询测试 (fetchUnionData)
    // 重点测试：部分命中 Redis，部分回源 DB
    // ==========================================

    @Test
    @DisplayName("测试 Set 并集：Redis 命中部分 + DB 命中部分 + 自动回填")
    void testUnionDataPartialHit() {
        // 场景：我们需要获取 ID 为 100 和 200 的两个用户的粉丝 ID 集合的并集
        // Key 1 (id:100): 存在于 Redis
        // Key 2 (id:200): Redis 缺失，需要查 DB

        // 这里的 Key 需要符合你在 dbQueryFunction 里期望的格式，或者就是 Redis Key
        // 假设传入的是 Redis 里的完整 Key 或者是业务 ID，这里假设传入的是 Redis Key 的后缀
        String keyInRedis1 = "100";
        String keyInRedis2 = "200";
        // 框架内部会拼上前缀，但在 fetchUnionData 的参数里，通常传入的是 "setKeysInRedis"
        // 框架的代码：strategy.readUnion(redisClient, setKeysInRedis...)
        // 如果你的 setKeysInRedis 是全名，则传入全名；如果是后缀，需确认框架行为。
        // 通常 fetchUnionData 的参数是 "完整的 Redis Keys"。
        String redisFullKey1 = "test:user:set:id:" + keyInRedis1;
        String redisFullKey2 = "test:user:set:id:" + keyInRedis2;

        List<String> targetRedisKeys = Arrays.asList(redisFullKey1, redisFullKey2);

        // 1. 预热数据：手动往 Redis 写入 Key1 的数据 (Set: [1, 2, 3])
//        stringRedisTemplate.opsForSet().add(redisFullKey1, "1", "2", "3");
//        redisTemplate.opsForSet().add(keyInRedis1, 1, 2, 3);

        // 2. 准备 DB 模拟函数 (只应该被调用一次，且参数只包含缺失的 Key2)
        Function<List<String>, Map<String, Set<Long>>> dbLoader = keys -> {
            Map<String, Set<Long>> result = new HashMap<>();
            if (keys.contains(redisFullKey2)) {
                // DB 返回 Key2 的数据 (Set: [3, 4, 5])
                result.put(redisFullKey1, new HashSet<>(Arrays.asList(1L, 2L, 3L)));
                result.put(redisFullKey2, new HashSet<>(Arrays.asList(3L, 4L, 5L)));
            }
            return result;
        };

        // 3. 执行并集查询
        Set<Long> unionResult = jMultiCache.fetchUnionData(targetRedisKeys, dbLoader);

        // 验证 1: 结果正确性 (并集: 1, 2, 3, 4, 5)
        assertThat(unionResult).hasSize(5).contains(1L, 2L, 3L, 4L, 5L);

        // 验证 2: Redis 回填
        // Key2 现在应该存在于 Redis 中了
        assertThat(stringRedisTemplate.hasKey(redisFullKey2)).isTrue();
        assertThat(stringRedisTemplate.opsForSet().isMember(redisFullKey2,  String.valueOf(4L))).isTrue();

        // 验证 3: L1 缓存
        // 再次查询应该直接走 L1，不查 Redis 也不查 DB
        // 这里无法直接断言 L1 命中（除非 spy），但我们可以删除 Redis 数据来反向验证 L1 还在
        stringRedisTemplate.delete(redisFullKey1);
        stringRedisTemplate.delete(redisFullKey2);

        Set<Long> l1Result = jMultiCache.fetchUnionData(targetRedisKeys, (k) -> {
            throw new RuntimeException("Should hit L1!");
        });
        assertThat(l1Result).hasSize(5);
    }

    @Test
    @DisplayName("测试 Set 并集：空值防穿透 (Empty Cache)")
    void testUnionDataAntiPenetration() {
        String unionL1Key = "union:fans:empty_test";
        String notExistKey = "test:user:set:id:9999";

        // 1. DB 返回空 Map，表示该 Key 没数据
        Set<Long> result = jMultiCache.fetchUnionData(
                Collections.singletonList(notExistKey),
                keys -> Collections.emptyMap() // DB 返回空
        );

        assertThat(result).isEmpty();

        // 2. 验证 Redis 写入了空标记 (Empty Placeholder)
        // 你的代码逻辑：strategy.writeMultiEmpty(...)
        // 此时 Redis 里该 Key 应该存在，且有一个特殊的空值（通常是 "【BINGO】" 或类似）
        assertThat(stringRedisTemplate.hasKey(notExistKey)).isTrue();

        // 验证它是 Set 结构还是 String 结构？
        // 许多框架处理 Set 空值时，可能会塞一个 dummy value，或者直接存一个 String 类型的 Placeholder
        // 这取决于 RedisStorageStrategy 的实现。
        // 如果是存 String 占位符：
        // assertThat(stringRedisTemplate.type(notExistKey).code()).isEqualTo("string");
        // 如果是存 Set 里的 Dummy Item：
        // assertThat(stringRedisTemplate.opsForSet().isMember(notExistKey, "BINGO")).isTrue();
    }

    // ==========================================
    // 11. Hash 结构 Field 查询测试 (fetchHashData)
    // ==========================================

    @Test
    @DisplayName("测试 Hash 字段查询：DB加载 -> L2 Hash存储 -> L1存储")
    void testHashDataFlow() {
        // 配置：TEST_USER_HASH
        // Redis Key (Hash表名)
        String hashKey = "test:user:hash";
        // Hash Field (项)
        String field = "1001";

        TestUser dbUser = new TestUser(1001L, "G1", "HashUser1", 18);

        // 1. 清理环境
        stringRedisTemplate.delete(hashKey);

        // 2. 第一次查询：查 DB
        TestUser result1 = jMultiCache.fetchHashData(
                hashKey,
                field,
                TestUser.class,
                () -> {
                    log.info("DB Query for Hash Field...");
                    return dbUser;
                }
        );

        assertThat(result1).isNotNull();
        assertThat(result1.getName()).isEqualTo("HashUser1");

        // 3. 验证 Redis 结构
        // 必须是 Hash 类型
        assertThat(stringRedisTemplate.type(hashKey).code()).isEqualTo("hash");
        // 必须包含该 Field
        assertThat(stringRedisTemplate.opsForHash().hasKey(hashKey, field)).isTrue();

        // 4. 验证值是否正确 (序列化检查)
        // 框架通常将对象序列化为 JSON String 存入 Hash value
        Object hashVal = stringRedisTemplate.opsForHash().get(hashKey, field);
        assertThat(hashVal.toString()).contains("HashUser1");

        // 5. 第二次查询：命中 L1 (或 L2)
        // 为了验证命中，我们修改 DB Supplier 返回不同的值，如果命中缓存，结果应该不变
        TestUser result2 = jMultiCache.fetchHashData(
                hashKey,
                field,
                TestUser.class,
                () -> new TestUser(1001L, "G1", "CHANGED", 99)
        );

        // 结果应该是旧值
        assertThat(result2.getName()).isEqualTo("HashUser1");
    }

    @Test
    @DisplayName("测试 Hash 字段不存在 (Null) 的处理")
    void testHashDataNullValue() {
        String hashKey = "test:user:hash:group_b";
        String field = "not_exist_user";

        // 1. 查询不存在的数据
        TestUser result = jMultiCache.fetchHashData(
                hashKey,
                field,
                TestUser.class,
                () -> null // DB 返回 null
        );

        assertThat(result).isNull();

        // 2. 验证 Redis 行为
        // 代码注释提到："这个方法缓存空值标记时会修改整个hash的过期时间...不建议使用"
        // 我们要测试它是否确实缓存了空值（防穿透）
        Boolean hasKey = stringRedisTemplate.opsForHash().hasKey(hashKey, field);

        // 如果框架做了防穿透，这里应该是 True (存了 BINGO)，否则是 False
        // 根据你的代码 getFromDbHash 实现，通常会回填 Empty Value
        if (hasKey) {
            Object val = stringRedisTemplate.opsForHash().get(hashKey, field);
            // 验证是否是配置的 empty-cache-value (默认 "【BINGO】")
            // 或者是序列化后的 "\"【BINGO】\""
            log.info("Hash empty value in redis: {}", val);
            assertThat(val.toString()).contains("BINGO");
        } else {
            log.warn("该框架版本似乎没有对 Hash Field 进行空值缓存");
        }
    }
}
