package com.dianping.controller;


import com.dianping.dto.Result;
import com.dianping.entity.ShopType;
import com.dianping.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        //List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        return typeService.queryList();
    }
}
