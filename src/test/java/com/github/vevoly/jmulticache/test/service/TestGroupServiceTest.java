package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestGroup;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import jmulticache.generated.JMultiCacheName;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class TestGroupServiceTest {

    @Autowired
    private TestGroupService testGroupService;

    @Autowired
    private JMultiCacheOps jMultiCacheOps;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 清理环境
    @BeforeEach
    void setUp() {
        Set<String> keys = stringRedisTemplate.keys("test:group:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ==========================================
    // 1. 测试缓存预热 (Preload)
    // ==========================================
    @Test
    @DisplayName("测试缓存预热：数据应批量写入 Redis")
    void testPreloadMultiCache() {

        // 1. 启动时，缓存自动预热
        // 2. 验证 Redis 中是否存在对应的 Key
        // mockDbQueryAll 包含 tenant001, tenant002, tenant003
        // key 应该是 namespace + tenantId
        Boolean hasTenant1 = stringRedisTemplate.hasKey("test:group:list:tenantId:tenant001");
        Boolean hasTenant2 = stringRedisTemplate.hasKey("test:group:list:tenantId:tenant002");
        Boolean hasTenant3 = stringRedisTemplate.hasKey("test:group:list:tenantId:tenant003");

        assertThat(hasTenant1).as("租户1的缓存应该存在").isTrue();
        assertThat(hasTenant2).as("租户2的缓存应该存在").isTrue();
        assertThat(hasTenant3).as("租户3的缓存应该存在").isTrue();

        // 3. 验证数据内容 (可选)
        String jsonValue = stringRedisTemplate.opsForValue().get("test:group:list:tenantId:tenant001");
        log.info("Redis Value for tenant001: {}", jsonValue);
        assertThat(jsonValue).contains("group1").contains("group2");
    }

    // ==========================================
    // 2. 测试注解缓存 (List)
    // ==========================================
    @Test
    @DisplayName("测试列表缓存：第一次查DB，第二次查缓存")
    void testListByTenantId() {
        String tenantId = "tenant001";

        // 1. 第一次查询 (Cache Miss -> DB -> Cache Put)
        long start1 = System.currentTimeMillis();
        List<TestGroup> list1 = testGroupService.listByTenantId(tenantId);
        long end1 = System.currentTimeMillis();

        assertThat(list1).hasSize(4); // 你的 mock 数据有 4 条
        log.info("第1次查询耗时: {} ms (应包含 sleep 50ms)", end1 - start1);
        // 因为有 Thread.sleep(50)，所以耗时应该 > 50ms
        assertThat(end1 - start1).isGreaterThanOrEqualTo(50);

        // 2. 验证 Redis 是否写入
        assertThat(stringRedisTemplate.hasKey("test:group:list:tenantId:" + tenantId)).isTrue();

        // 3. 第二次查询 (Cache Hit)
        long start2 = System.currentTimeMillis();
        List<TestGroup> list2 = testGroupService.listByTenantId(tenantId);
        long end2 = System.currentTimeMillis();

        assertThat(list2).hasSize(4);
        assertThat(list2.get(0).getName()).isEqualTo("group1");

        log.info("第2次查询耗时: {} ms (应极快)", end2 - start2);
        // 应该极快，肯定小于 50ms (因为没走 sleep)
        assertThat(end2 - start2).isLessThan(50);
    }

    // ==========================================
    // 3. 测试基于缓存的业务逻辑
    // ==========================================
    @Test
    @DisplayName("测试 getByName：应当复用 listByTenantId 自动挡的缓存")
    void testGetByName() {
        // 1. 先把数据预热进去

        // 2. 调用 getByName
        // 既然已经预热了，这里应该直接走内存/Redis，不会触发 sleep(50)
        long start = System.currentTimeMillis();
        TestGroup group3 = testGroupService.getByName("group3");
        long end = System.currentTimeMillis();

        // 3. 验证结果
        assertThat(group3).isNotNull();
        assertThat(group3.getId()).isEqualTo(3L);
        assertThat(group3.getName()).isEqualTo("group3");

        // 4. 验证性能 (证明走了缓存)
        log.info("getByName 耗时: {} ms", end - start);
        assertThat(end - start).isLessThan(200);
    }

    // ==========================================
    // 4. 测试单个对象缓存 (getById)
    // ==========================================
    @Test
    @DisplayName("测试 getById 注解缓存")
    void testGetById() {
        Long id = 888L;

        // 第一次调用
        TestGroup g1 = testGroupService.getById(id);
        assertThat(g1.getName()).isEqualTo("group888");

        // 第二次调用
        TestGroup g2 = testGroupService.getById(id);

        // 验证对象相等性 (取决于序列化机制，通常值相等)
        assertThat(g2.getName()).isEqualTo(g1.getName());
    }

    @Test
    @DisplayName("开发辅助：查看本地缓存命中率")
    void testJMultiCacheEnumGenerator2() {
        for (int i = 0; i < 100; i++) {
            testGroupService.getById(100L);
        }
        for (int i = 0; i < 88; i++) {
            testGroupService.getById(101L);
        }

        // JMultiCacheName.TEST_GROUP 手动生成，请先调用手动生成缓存枚举类工具
        String stats = jMultiCacheOps.getL1Stats(JMultiCacheName.TEST_GROUP.name());
        System.out.println(stats);
    }
}