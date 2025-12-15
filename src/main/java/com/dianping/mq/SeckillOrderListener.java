package com.dianping.mq;

import com.dianping.config.MqConfig;
import com.dianping.entity.VoucherOrder;
import com.dianping.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 MQ 消费者：监听 seckill.order.queue，落库。
 *
 * 处理流程：抢分布式锁（防并发重复消费同一用户）→ 调用 Service 落库（自带 @Transactional 与一人一单兜底）。
 * MQ 的 auto-ack 失败重试由 spring-rabbit 的 retry 配置兜底（max-attempts=3）。
 */
@Slf4j
@Component
public class SeckillOrderListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedissonClient redissonClient;

    @RabbitListener(queues = MqConfig.SECKILL_QUEUE)
    public void handleSeckillOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.error("不允许重复下单: userId={}", userId);
                return;
            }
            // Spring 注入的 voucherOrderService 是代理对象，@Transactional 生效
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("处理订单异常: orderId={}", voucherOrder.getId(), e);
            throw e;
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
