package com.github.vevoly.jmulticache.test.service;


import com.github.vevoly.jmulticache.test.entity.LongArticle;
import io.github.vevoly.jmulticache.api.JMultiCacheOps;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
class CustomGzipTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JMultiCacheOps jMultiCacheAdmin;

    @BeforeEach
    void setUp() {
        // 清理所有测试相关的 Redis Key，防止干扰
        Set<String> keys = stringRedisTemplate.keys("test:gzip:article:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("测试自定义 Gzip 策略：验证压缩效果与数据还原")
    void testGzipStorage() {

        Long id = 888L;
        String redisKey = "test:gzip:article:" + id;

        // 1. 第一次查询 (DB -> Gzip -> Redis)
        LongArticle article = articleService.getArticle(id);

        assertThat(article).isNotNull();
        assertThat(article.getContent().length()).isGreaterThan(1000); // 确保原始内容很长

        // 2. 验证 Redis 中的数据是否被压缩
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        log.info("Redis 存储内容: {}", redisValue);

        assertThat(redisValue).isNotNull();
        // 验证它不是明文 JSON (不以 "{" 开头)
        assertThat(redisValue).doesNotStartWith("{");
        // 验证它是 Base64 字符 (简单验证)
        assertThat(redisValue).matches("^[A-Za-z0-9+/=]+$");

        // 3. 验证压缩率 (粗略对比)
        // 原始 JSON 长度大概是 content 长度，Base64 也会增加约 33% 开销，但 Gzip 对重复文本压缩率极高
        // 预期：Redis 里的字符串长度 远小于 原始内容长度
        assertThat(redisValue.length()).isLessThan(article.getContent().length() / 2);

        // 4. 第二次查询 (Redis -> Gunzip -> Object)
        // 验证能正确还原数据
        LongArticle cachedArticle = articleService.getArticle(id);
        assertThat(cachedArticle.getContent()).isEqualTo(article.getContent());
        assertThat(cachedArticle.getId()).isEqualTo(id);
    }
}
