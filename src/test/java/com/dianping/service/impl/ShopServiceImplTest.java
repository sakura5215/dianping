package com.dianping.service.impl;

import com.dianping.dto.Result;
import com.dianping.entity.Shop;
import com.dianping.utils.CacheClient;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopServiceImpl 多级缓存读路径单元测试。
 * 覆盖 L1(Caffeine) 命中 / L2(Redis) 命中回填 L1 / L2 未命中降级 DB 并回写 / 店铺不存在 四条路径。
 * 全部 Mock 依赖，不依赖真实 Redis/MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    @Mock
    private Cache<Long, Shop> shopLocalCache;

    @Mock
    private CacheClient cacheClient;

    @Spy
    @InjectMocks
    private ShopServiceImpl shopService;

    private Shop shop(long id) {
        Shop s = new Shop();
        s.setId(id);
        s.setName("shop-" + id);
        return s;
    }

    @Test
    void queryById_L1命中直接返回() {
        Shop shop = shop(1L);
        when(shopLocalCache.getIfPresent(1L)).thenReturn(shop);

        Result result = shopService.queryById(1L);

        assertTrue(result.getSuccess());
        assertSame(shop, result.getData());
        // 命中 L1 后不应再走 L2 和 DB
        verify(cacheClient, never()).queryWithLogicalExpire(any(), any(), any(), any(), any(), any());
        verify(shopService, never()).getById(anyLong());
    }

    @Test
    void queryById_L2命中回填L1() {
        Shop shop = shop(1L);
        when(shopLocalCache.getIfPresent(1L)).thenReturn(null);
        when(cacheClient.queryWithLogicalExpire(eq(CACHE_SHOP_KEY), eq(1L), eq(Shop.class), any(), anyLong(), any()))
                .thenReturn(shop);

        Result result = shopService.queryById(1L);

        assertTrue(result.getSuccess());
        assertSame(shop, result.getData());
        // L2 命中后回填 L1，且不再查 DB
        verify(shopLocalCache).put(1L, shop);
        verify(shopService, never()).getById(anyLong());
    }

    @Test
    void queryById_L2未命中降级查DB并回写两级() {
        Shop shop = shop(1L);
        when(shopLocalCache.getIfPresent(1L)).thenReturn(null);
        when(cacheClient.queryWithLogicalExpire(eq(CACHE_SHOP_KEY), eq(1L), eq(Shop.class), any(), anyLong(), any()))
                .thenReturn(null);
        doReturn(shop).when(shopService).getById(1L);

        Result result = shopService.queryById(1L);

        assertTrue(result.getSuccess());
        assertSame(shop, result.getData());
        // 降级查 DB 后，回写 L2 与 L1
        verify(cacheClient).set(eq(CACHE_SHOP_KEY + 1L), eq(shop), anyLong(), any(TimeUnit.class));
        verify(shopLocalCache).put(1L, shop);
    }

    @Test
    void queryById_店铺不存在返回失败() {
        when(shopLocalCache.getIfPresent(1L)).thenReturn(null);
        when(cacheClient.queryWithLogicalExpire(eq(CACHE_SHOP_KEY), eq(1L), eq(Shop.class), any(), anyLong(), any()))
                .thenReturn(null);
        doReturn(null).when(shopService).getById(1L);

        Result result = shopService.queryById(1L);

        assertFalse(result.getSuccess());
        assertEquals("店铺不存在", result.getErrorMsg());
        // 未命中任何缓存且 DB 无数据，不写缓存
        verify(cacheClient, never()).set(any(), any(), any(), any());
        verify(shopLocalCache, never()).put(any(), any());
    }
}
