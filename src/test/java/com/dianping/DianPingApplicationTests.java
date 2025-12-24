package com.dianping;

import com.dianping.entity.Shop;
import com.dianping.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dianping.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class DianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void localShopTest() {
        //1. 查询店铺信息
        List<Shop> list = shopService.list();
        //2. 把店铺分组，按typeId分组，typeId一样的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3. 分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1. 获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2. 获取同类型的店铺列表
            List<Shop> value = entry.getValue();
            //3.3. 写入redis GEOADD key longitude latitude member
            for (Shop shop : value) {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        }
    }
}