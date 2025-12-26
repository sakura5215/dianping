package com.dianping.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.dianping.dto.Result;
import com.dianping.entity.Shop;
import com.dianping.mapper.ShopMapper;
import com.dianping.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.utils.CacheClient;
import com.dianping.utils.RedisData;
import com.dianping.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private Cache<Long, Shop> shopLocalCache;

    @Override
    public Result queryById(Long id) {
        // 1. L1: 查本地缓存（Caffeine），命中直接返回，避免网络与序列化开销
        Shop shop = shopLocalCache.getIfPresent(id);
        if (shop != null) {
            return Result.ok(shop);
        }
        // 2. L2: 查 Redis（带逻辑过期 + 互斥锁缓存击穿保护）
        shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop != null) {
            // L2 命中，回填 L1（下次走进程内内存）
            shopLocalCache.put(id, shop);
            return Result.ok(shop);
        }
        // 3. L2 未命中（冷启或缓存被清），降级查 DB 并回写 L2+L1，避免接口返回"不存在"
        shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        cacheClient.set(CACHE_SHOP_KEY + id, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        shopLocalCache.put(id, shop);
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存（旁路缓存模式 Cache Aside）：先删 Redis L2，再删 Caffeine L1
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        shopLocalCache.invalidate(shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy) {
        // 1. 无坐标，或指定了人气/评分排序（非距离排序）：直接按库查询并排序
        if (x == null || y == null || (sortBy != null && !sortBy.isEmpty())) {
            Page<Shop> page;
            if ("comments".equals(sortBy)) {
                page = query().eq("type_id", typeId).orderByDesc("comments")
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            } else if ("score".equals(sortBy)) {
                page = query().eq("type_id", typeId).orderByDesc("score")
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            } else {
                page = query().eq("type_id", typeId)
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            }
            return Result.ok(page.getRecords());
        }
        // 2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3. 查询redis、按照距离排序、分页。结果：shopId、distance
        // 用 GEORADIUS（Redis 3.2+ 即支持）而非 GEOSEARCH（需 Redis 6.2+），兼容低版本 Redis
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                key,
                new Circle(new Point(x, y), new Distance(5000)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end).sortAscending()
        );
        // 4. 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if(list.size() <= from){
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1. 截取从start到end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr)); //店铺id
            distanceMap.put(shopIdStr, result.getDistance()); //距离
        });
        // GEO 命中为空时直接返回，避免 IN () 非法 SQL
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 5. 根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
