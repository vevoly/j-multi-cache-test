package com.github.vevoly.jmulticache.test.service.impl;

import com.github.vevoly.jmulticache.test.entity.TestGroup;
import com.github.vevoly.jmulticache.test.service.TestGroupService;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import io.github.vevoly.jmulticache.api.JMultiCachePreload;
import io.github.vevoly.jmulticache.api.annotation.JMultiCachePreloadable;
import io.github.vevoly.jmulticache.api.annotation.JMultiCacheable;
import io.github.vevoly.jmulticache.api.utils.StreamUtils;
import jmulticache.generated.JMultiCacheName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@JMultiCachePreloadable
public class TestGroupServiceImpl implements TestGroupService, JMultiCachePreload {

    @Autowired
    private JMultiCache jMultiCache;
    @Autowired
    private JMultiCacheOps jMultiCacheOps;

    /**
     * 解决Aop自调用失效问题使用自我注入
     */
    @Lazy
    @Autowired
    private TestGroupService self;

    private static final String tenantId = "tenant001";

    private List<TestGroup> mockDbQueryList(String tenantId) {
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        List<TestGroup> list = List.of(
                new TestGroup(1L, tenantId, "group1"),
                new TestGroup(2L, tenantId, "group2"),
                new TestGroup(3L, tenantId, "group3"),
                new TestGroup(4L, tenantId, "group4")
        );
        return list;
    }

    private List<TestGroup> mockDbQueryAll() {
        List<TestGroup> testGroups = mockDbQueryList(tenantId);
        List<TestGroup> testGroups1 = mockDbQueryList("tenant002");
        List<TestGroup> testGroups2 = mockDbQueryList("tenant003");
        List<TestGroup> ret = new ArrayList<>();
        ret.addAll(testGroups);
        ret.addAll(testGroups1);
        ret.addAll(testGroups2);
        return ret;
    }

    @Override
    @JMultiCacheable
    public TestGroup getById(Long id) {
        return TestGroup.builder().id(id).name("group" + id).build();
    }

    @Override
    @JMultiCacheable(configName = "TEST_GROUP_LIST")
    public List<TestGroup> listByTenantId(String tenantId) {
        return mockDbQueryList(tenantId);
    }

    @Override
    public TestGroup getByName(String name) {
        // 解决Aop自调用失效问题使用自我注入
        List<TestGroup> list = self.listByTenantId(tenantId);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        // 过滤
        return list.stream().filter(item -> item.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * 模拟 My-Batis list方法
     * 自动档缓存预热会默认调用list方法，也可以指定缓存预热使用的方法，使用参数 fetchMethod = "list"
     * @return
     */
    @Override
    public List<TestGroup> list() {
        return mockDbQueryAll();
    }

    /**
     * 手动缓存预热
     * @return
     */
    @Override
    public int preloadMultiCache() {
        List<TestGroup> list = mockDbQueryAll();
        Map<String, List<TestGroup>> group = StreamUtils.group(list, TestGroup::getTenantId);
        jMultiCacheOps.preloadMultiCache("TEST_GROUP_LIST", group);
        return 0;
    }
}
