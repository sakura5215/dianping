package com.dianping.service;

import com.dianping.dto.Result;
import com.dianping.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IShopTypeService extends IService<ShopType> {

    /**
     * 使用redis查询所有商铺类型
     * @return 商铺类型列表
     */
    Result queryList();
}
