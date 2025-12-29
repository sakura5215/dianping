package com.dianping.service.impl;

import com.dianping.config.MqConfig;
import com.dianping.dto.Result;
import com.dianping.entity.Voucher;
import com.dianping.entity.VoucherOrder;
import com.dianping.mapper.VoucherOrderMapper;
import com.dianping.service.ISeckillVoucherService;
import com.dianping.service.IVoucherOrderService;
import com.dianping.service.IVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.utils.RedisIdWorker;
import com.dianping.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 秒杀下单服务实现。
 *
 * 流程：Lua 原子判资格+扣库存 → 成功返回 0 → Java 侧构造订单消息发 RabbitMQ →
 * 消费者（SeckillOrderListener）异步落库。
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    @Transactional
    public Result buyVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1. 查询优惠券
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || voucher.getStatus() != 1) {
            return Result.fail("优惠券不存在或已下架");
        }
        if (voucher.getType() != null && voucher.getType() == 1) {
            // 秒杀券走专门的抢购入口，库存由 Redis Lua 维护
            return Result.fail("秒杀券请通过限时抢购入口下单");
        }
        // 2. 生成订单。普通券表无库存列（不限量），同步落库即可，无需 MQ
        VoucherOrder order = new VoucherOrder()
                .setId(redisIdWorker.nextId("order"))
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setPayType(1)
                .setStatus(1)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        save(order);
        return Result.ok(order.getId());
    }

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");

        // 1. 执行 Lua 脚本：判断购买资格并扣减库存（原子操作）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 2. 资格通过、库存已扣减，构造订单消息发送到 RabbitMQ
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        rabbitTemplate.convertAndSend(MqConfig.SECKILL_EXCHANGE, MqConfig.SECKILL_ROUTING_KEY, voucherOrder);

        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 一人一单兜底（Lua 已判，这里二次校验防止 MQ 重复消费）
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("用户已经购买过一次: userId={}, voucherId={}", userId, voucherId);
            return;
        }

        // 乐观锁扣减数据库库存（Lua 只扣了 Redis 库存，DB 这里再扣）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            log.error("数据库库存不足: voucherId={}", voucherId);
            return;
        }

        save(voucherOrder);
    }
}
