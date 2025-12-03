package com.github.vevoly.jmulticache.test.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vevoly.jmulticache.api.config.ResolvedJMultiCacheConfig;
import io.github.vevoly.jmulticache.api.redis.RedisClient;
import io.github.vevoly.jmulticache.api.redis.batch.BatchOperation;
import io.github.vevoly.jmulticache.api.strategy.RedisStorageStrategy;
import io.github.vevoly.jmulticache.api.utils.JMultiCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 用户自定义策略：GZIP 压缩存储。
 * <p>
 * 适用于存储超大文本或对象，以空间换时间（CPU）。
 * User-defined strategy: GZIP compressed storage.
 * Suitable for storing large text or objects, trading CPU for space.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GzipStringStorageStrategy implements RedisStorageStrategy<Object> {

    private final ObjectMapper objectMapper;

    // 自定义类型名称
    public static final String TYPE_NAME = "gzip";

    @Override
    public String getStorageType() {
        return TYPE_NAME;
    }

    @Override
    public Object read(RedisClient redisClient, String key, TypeReference<Object> typeRef, ResolvedJMultiCacheConfig config) {
        // 1. 从 Redis 获取 Base64 字符串
        String base64Str = (String) redisClient.get(key);

        if (!StringUtils.hasText(base64Str)) {
            return null;
        }
        // 2. 如果是空值占位符，直接返回 null 对象 (这里假设空值不压缩，或者压缩了也能解开)
        if (config.getEmptyValueMark().equals(base64Str)) {
            return null;
        }

        try {
            // 3. 解压: Base64 -> Gzip -> JSON
            String json = decompress(base64Str);
            // 4. 反序列化
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("Gzip 解压/反序列化失败 key={}", key, e);
            return null;
        }
    }

    @Override
    public <V> Map<String, CompletableFuture<Optional<V>>> readMulti(BatchOperation batch, List<String> keysToRead, TypeReference<V> typeRef, ResolvedJMultiCacheConfig config) {
        return null;
    }

    @Override
    public void write(RedisClient redisClient, String key, Object value, ResolvedJMultiCacheConfig config) {
        // 1. 防穿透处理
        if (JMultiCacheHelper.isSpecialEmptyData(value, config)) {
            // 这里为了简单，空值占位符直接存明文，不压缩
            redisClient.set(key, value, config.getEmptyCacheTtl());
            return;
        }

        try {
            // 2. 序列化: Object -> JSON
            String json = objectMapper.writeValueAsString(value);
            // 3. 压缩: JSON -> Gzip -> Base64
            String compressedStr = compress(json);
            // 4. 写入 Redis
            redisClient.set(key, compressedStr, config.getRedisTtl());
            log.info(">>> [GzipStrategy] 压缩写入成功. 原长: {}, 压缩后: {}, Key: {}",
                    json.length(), compressedStr.length(), key);
        } catch (Exception e) {
            log.error("Gzip 压缩/写入失败 key={}", key, e);
        }
    }

    @Override
    public void writeMulti(BatchOperation batch, Map<String, Object> dataToCache, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("Gzip batch read not supported yet");
    }

    @Override
    public void writeMultiEmpty(BatchOperation batch, List<String> keysToMarkEmpty, ResolvedJMultiCacheConfig config) {
        throw new UnsupportedOperationException("Gzip batch write not supported yet");
    }

    // --- 辅助方法：GZIP 压缩 ---
    private String compress(String str) throws IOException {
        if (!StringUtils.hasText(str)) return str;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(str.getBytes());
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    // --- 辅助方法：GZIP 解压 ---
    private String decompress(String str) throws IOException {
        if (!StringUtils.hasText(str)) return str;
        byte[] bytes = Base64.getDecoder().decode(str);
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString();
        }
    }
}
