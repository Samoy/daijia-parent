package com.atguigu.daijia.driver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TencentCloudProperties
 * 不想花钱，所以将所有腾讯云存储都换成了MinIO。
 *
 * @author Samoy
 * @date 2024/8/6
 */
@ConfigurationProperties(prefix = "minio")
@Data
@Component
public class TencentCloudProperties {
    private String endpointUrl;
    private String accessKey;
    private String secretKey;
    private String bucketName;
}
