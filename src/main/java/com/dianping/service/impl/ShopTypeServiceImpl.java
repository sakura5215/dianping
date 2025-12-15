package com.dianping.service.impl;

import cn.hutool.json.JSONUtil;
import com.dianping.dto.Result;
import com.dianping.entity.ShopType;
import com.dianping.mapper.ShopTypeMapper;
import com.dianping.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 使用redis查询所有商铺类型
     *
     * @return 商铺类型列表
     */
    @Override
    public Result queryList() {
        //todo 检查：获取redis中的商铺类型列表

        // 1.从redis中获取商铺类型缓存
        List<String> shopList = redisTemplate.opsForList().range(CACHE_SHOP_KEY, 0, -1);
        // 2.判断是否存在
        if(shopList != null && !shopList.isEmpty()){
            // 3.存在，直接返回
            List<ShopType> shopTypeList = shopList.stream()
                            .map(shop -> JSONUtil.toBean(shop, ShopType.class))
                            .toList();
            return Result.ok(shopTypeList);
        }

        // 4.不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 5.数据库不存在，返回错误
        if(shopTypeList == null){
            return Result.fail("店铺不存在");
        }
        // 6.存在，写入redis
        List<String> shopTypeStringList = shopTypeList.stream()
                .map(shop -> JSONUtil.toJsonStr(shop))
                .toList();
        redisTemplate.opsForList().leftPushAll(CACHE_SHOP_KEY, shopTypeStringList);
        return Result.ok(shopTypeList);
    }
}
