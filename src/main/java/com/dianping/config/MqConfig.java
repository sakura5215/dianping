package com.dianping.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：秒杀订单的队列、交换机、绑定与消息转换器。
 *
 * 用 direct 交换机 + 路由键 seckill.order，队列 seckill.order.queue。
 * 生产者发消息到 seckill.order.exchange，路由键 seckill.order；
 * 消费者监听 seckill.order.queue。
 *
 * 消息用 Jackson2JsonMessageConverter 序列化为 JSON，避免 Java 原生序列化的
 * 反序列化白名单限制（Spring Boot 3 默认拒绝反序列化未授权类）。
 */
@Configuration
public class MqConfig {

    public static final String SECKILL_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange seckillExchange() {
        return ExchangeBuilder.directExchange(SECKILL_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }
}
