package com.github.vevoly;

import com.github.vevoly.jmulticache.test.strategy.GzipStringStorageStrategy;
import io.github.vevoly.jmulticache.api.annotation.EnableJMultiCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@EnableJMultiCache(preload = true)
@SpringBootApplication
@Import(GzipStringStorageStrategy.class)
public class JMultiCacheTest {
    public static void main(String[] args) {
        SpringApplication.run(JMultiCacheTest.class, args);
        System.out.println("Hello JMultiCache's World!");
    }
}