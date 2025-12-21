package com.dianping.config;

import com.dianping.entity.Shop;
import com.dianping.service.IShopService;
import com.dianping.utils.CacheClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.dianping.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * 店铺缓存预热。
 *
 * 店铺详情读路径 L2 采用「逻辑过期」方案：Redis miss 时直接返回 null（不打 DB，
 * 避免缓存击穿）。因此生产上需在应用启动时把店铺数据预热到 Redis（带逻辑过期时间），
 * 让第一波请求也能命中缓存。
 *
 * 实现为 ApplicationRunner，在 Spring 上下文就绪后异步执行；预热失败仅告警，
 * 不影响应用启动（未命中的请求会走 queryById 里的 DB 兜底降级）。
 */
@Component
@Slf4j
public class ShopCacheWarmer implements ApplicationRunner {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Shop> shops = shopService.list();
            for (Shop shop : shops) {
                cacheClient.setWithLogicalExpire(
                        CACHE_SHOP_KEY + shop.getId(), shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
            log.info("店铺缓存预热完成，共 {} 家店铺写入 Redis（逻辑过期）", shops.size());
        } catch (Exception e) {
            log.warn("店铺缓存预热失败（不影响启动，未命中请求将走 DB 兜底），原因：{}", e.getMessage());
        }
    }
}
