package com.dianping.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 接口文档配置。
 * 文档访问地址：/swagger-ui.html（UI）、/v3/api-docs（JSON）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("点评社交平台 API")
                .description("店铺查询 / 优惠券秒杀 / 达人探店 / 社交关注 / 附近店铺 五条业务线接口")
                .version("1.0.0"));
    }
}
