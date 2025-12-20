package com.dianping.service.impl;

import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.VoucherOrder;
import com.dianping.service.ISeckillVoucherService;
import com.dianping.utils.RedisIdWorker;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.dianping.config.MqConfig.SECKILL_EXCHANGE;
import static com.dianping.config.MqConfig.SECKILL_ROUTING_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 秒杀下单单元测试。
 * 覆盖 Lua 通过后发 MQ / 库存不足 / 一人一单重复下单 三条分支。
 * 全部 Mock 依赖，不依赖真实 Redis/RabbitMQ。
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock
    private ISeckillVoucherService seckillVoucherService;

    @Mock
    private RedisIdWorker redisIdWorker;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private VoucherOrderServiceImpl voucherOrderService;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO();
        user.setId(1L);
        user.setNickName("test");
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void seckill_Lua通过发送MQ并返回订单号() {
        when(redisIdWorker.nextId("order")).thenReturn(100L);
        // Lua 返回 0 表示资格通过、库存已扣
        when(stringRedisTemplate.execute(any(), anyList(), any(), any())).thenReturn(0L);

        Result result = voucherOrderService.seckillVoucher(10L);

        assertTrue(result.getSuccess());
        assertEquals(100L, result.getData());
        verify(rabbitTemplate).convertAndSend(eq(SECKILL_EXCHANGE), eq(SECKILL_ROUTING_KEY), any(VoucherOrder.class));
    }

    @Test
    void seckill_库存不足返回失败() {
        when(redisIdWorker.nextId("order")).thenReturn(100L);
        when(stringRedisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Result result = voucherOrderService.seckillVoucher(10L);

        assertFalse(result.getSuccess());
        assertEquals("库存不足", result.getErrorMsg());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void seckill_重复下单返回失败() {
        when(redisIdWorker.nextId("order")).thenReturn(100L);
        when(stringRedisTemplate.execute(any(), anyList(), any(), any())).thenReturn(2L);

        Result result = voucherOrderService.seckillVoucher(10L);

        assertFalse(result.getSuccess());
        assertEquals("不能重复下单", result.getErrorMsg());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
