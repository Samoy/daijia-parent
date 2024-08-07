package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TencentCloudProperties
 *
 * @author Samoy
 * @date 2024/8/7
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.cloud")
public class TencentCloudProperties {
    private String secretId;
    private String secretKey;
    private String region;
}
