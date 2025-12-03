package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestUser;
import com.github.vevoly.jmulticache.test.entity.dto.UserRank;
import io.github.vevoly.jmulticache.api.JMultiCache;
import io.github.vevoly.jmulticache.api.annotation.JMultiCacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RankService {

    @Autowired
    private JMultiCache jMultiCache;

    // --- 模拟 DB: 获取排行榜 (只返回 ID 和 分数) ---
    @JMultiCacheable(configName = "TEST_GAME_RANK")
    public List<UserRank> getRankByRegion(String region) {
        log.info(">>>>>> [DB] 查询排行榜索引 region={}", region);
        // 模拟 DB 返回乱序数据，Redis ZSet 会自动排序
        return mockBatchQueryUsers();
    }

    public List<UserRank> getRankByRegionManual(String region) {
        return jMultiCache.fetchData("TEST_GAME_RANK",
                () -> mockBatchQueryUsers(), region);

    }

    private List<UserRank> mockBatchQueryUsers() {
        return Arrays.asList(
                new UserRank(1001L, 5000.0), // 第2名
                new UserRank(1002L, 8888.0), // 第1名
                new UserRank(1003L, 100.0)   // 第3名
        );
    }

    // --- 模拟 DB: 批量查询用户详情 ---
    public Map<Long, TestUser> mockBatchQueryUsers(Collection<Long> ids) {
        log.info(">>>>>> [DB] 批量查询用户详情 ids={}", ids);
        return ids.stream().collect(Collectors.toMap(
                id -> id,
                id -> new TestUser(id, "T1", 1L,"User-" + id, 18)
        ));
    }
}
