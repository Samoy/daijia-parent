package com.atguigu.daijia.map.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * MapConfig
 *
 * @author Samoy
 * @date 2024/8/8
 */
@Configuration
public class MapConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
