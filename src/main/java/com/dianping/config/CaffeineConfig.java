package com.dianping.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.dianping.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存（L1）配置。
 * 与 Redis（L2）组成多级缓存：Caffeine 进程内 -> Redis 分布式 -> MySQL。
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<Long, Shop> shopLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(200)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}