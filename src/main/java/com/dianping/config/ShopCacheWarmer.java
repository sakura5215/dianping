package com.dianping.config;

import com.dianping.entity.Shop;
import com.dianping.service.IShopService;
import com.dianping.utils.CacheClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.dianping.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.dianping.utils.RedisConstants.SHOP_GEO_ALL_KEY;
import static com.dianping.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 店铺缓存预热。
 *
 * 1. 店铺详情读路径 L2 采用「逻辑过期」方案：Redis miss 时直接返回 null（不打 DB，
 *    避免缓存击穿）。因此需在应用启动时把店铺数据预热到 Redis（带逻辑过期时间），
 *    让第一波请求也能命中缓存。
 * 2. 附近店铺查询走 Redis GEO（shop:geo:{typeId}），启动时按类型 GEOADD 预热，
 *    避免 GEO 无数据导致按距离排序查询报错。
 *
 * 实现为 ApplicationRunner，在 Spring 上下文就绪后执行；预热失败仅告警，
 * 不影响应用启动（未命中的请求会走 queryById 里的 DB 兜底降级）。
 */
@Component
@Slf4j
public class ShopCacheWarmer implements ApplicationRunner {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Shop> shops = shopService.list();
            // 1. 店铺详情预热：写 Redis 逻辑过期缓存
            for (Shop shop : shops) {
                cacheClient.setWithLogicalExpire(
                        CACHE_SHOP_KEY + shop.getId(), shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
            // 2. GEO 预热：按类型分批 GEOADD，支撑附近店铺按距离排序查询
            shops.stream()
                    .filter(s -> s.getTypeId() != null && s.getX() != null && s.getY() != null)
                    .collect(Collectors.groupingBy(Shop::getTypeId))
                    .forEach((typeId, list) -> {
                        List<RedisGeoCommands.GeoLocation<String>> locations = list.stream()
                                .map(s -> new RedisGeoCommands.GeoLocation<>(s.getId().toString(), new Point(s.getX(), s.getY())))
                                .collect(Collectors.toList());
                        stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY + typeId, locations);
                    });
            // 3. 全量 GEO 预热：地图页跨类型查附近店铺
            List<RedisGeoCommands.GeoLocation<String>> allLocations = shops.stream()
                    .filter(s -> s.getX() != null && s.getY() != null)
                    .map(s -> new RedisGeoCommands.GeoLocation<>(s.getId().toString(), new Point(s.getX(), s.getY())))
                    .collect(Collectors.toList());
            stringRedisTemplate.opsForGeo().add(SHOP_GEO_ALL_KEY, allLocations);
            log.info("店铺缓存预热完成，共 {} 家店铺写入 Redis（逻辑过期 + 分类 GEO + 全量 GEO）", shops.size());
        } catch (Exception e) {
            log.warn("店铺缓存预热失败（不影响启动，未命中请求将走 DB 兜底），原因：{}", e.getMessage());
        }
    }
}
