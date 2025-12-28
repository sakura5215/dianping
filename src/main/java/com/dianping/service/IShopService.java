package com.dianping.service;

import com.dianping.dto.Result;
import com.dianping.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy);

    Result queryShopByGeo(Double x, Double y);
}
