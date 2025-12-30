package com.dianping.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    // 哨兵模式（激活 sentinel profile 或设置环境变量时）
    @Value("${REDIS_SENTINEL_NODES:}")
    private String sentinelNodes;

    @Value("${REDIS_SENTINEL_MASTER:mymaster}")
    private String sentinelMaster;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        if (StringUtils.hasText(sentinelNodes)) {
            // 高可用模式：走哨兵，主从切换对 Redisson 透明
            List<String> nodes = Arrays.stream(sentinelNodes.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            config.useSentinelServers()
                    .setMasterName(sentinelMaster)
                    .addSentinelAddress(nodes.stream().map(n -> "redis://" + n).toArray(String[]::new));
        } else {
            // 单机模式（默认）
            config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        }
        return Redisson.create(config);
    }
}
