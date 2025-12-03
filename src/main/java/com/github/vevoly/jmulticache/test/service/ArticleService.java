package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.LongArticle;
import io.github.vevoly.jmulticache.api.annotation.JMultiCacheable;
import org.springframework.stereotype.Service;

@Service
public class ArticleService {
    @JMultiCacheable(configName = "TEST_GZIP_CACHE")
    public LongArticle getArticle(Long id) {
        // 模拟一个超大对象
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            content.append("This is a very long text repeated to test compression efficiency. 这是一个超大的重复文本用以测试压缩效果。");
        }
        return new LongArticle(id, "Big News", content.toString());
    }
}
