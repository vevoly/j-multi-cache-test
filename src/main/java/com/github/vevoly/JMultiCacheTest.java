package com.github.vevoly;

import io.github.vevoly.jmulticache.api.annotation.EnableJMultiCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableJMultiCache(preload = true)
@SpringBootApplication
public class JMultiCacheTest {
    public static void main(String[] args) {
        SpringApplication.run(JMultiCacheTest.class, args);
        System.out.println("Hello JMultiCache's World!");
    }
}