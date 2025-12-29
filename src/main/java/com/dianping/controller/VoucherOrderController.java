package com.dianping.controller;


import com.dianping.dto.Result;
import com.dianping.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 购买普通优惠券（非秒杀）
     */
    @PostMapping("buy/{id}")
    public Result buyVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.buyVoucher(voucherId);
    }
}
