package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CosServiceImpl implements CosService {

    @Resource
    private TencentCloudProperties tencentCloudProperties;

    private MinioClient getMinioClient() {
        return MinioClient.builder()
                .endpoint(tencentCloudProperties.getEndpointUrl())
                .credentials(tencentCloudProperties.getAccessKey(), tencentCloudProperties.getSecretKey())
                .build();
    }

    @Override
    public CosUploadVo upload(MultipartFile file, String path) {
        String uploadPath = "";
        try {
            // 创建一个Minio的客户端对象
            MinioClient minioClient = getMinioClient();

            // 判断桶是否存在
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(tencentCloudProperties.getBucketName()).build());
            if (!found) {       // 如果不存在，那么此时就创建一个新的桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(tencentCloudProperties.getBucketName()).build());
            }

            // 设置元数据
            Multimap<String, String> meta = HashMultimap.create();
            meta.put(HttpHeaders.CONTENT_TYPE, file.getContentType());
            meta.put(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSize()));
            meta.put(HttpHeaders.CONTENT_ENCODING, "UTF-8");

            if (file.getOriginalFilename() != null) {
                String fileType = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
                // 设置路径
                uploadPath = "/driver/" + path + "/"
                        + UUID.randomUUID().toString().replaceAll("-", "") + fileType;
                PutObjectArgs args = PutObjectArgs.builder()
                        .bucket(tencentCloudProperties.getBucketName())
                        .object(uploadPath)
                        .userMetadata(meta)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build();
                minioClient.putObject(args);
            }
        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        CosUploadVo cosUploadVo = new CosUploadVo();
        cosUploadVo.setUrl(uploadPath);
        String imageUrl  = getImageUrl(uploadPath);
        cosUploadVo.setShowUrl(imageUrl);
        return cosUploadVo;
    }

    @Override
    public String getImageUrl(String path) {
        MinioClient minioClient = getMinioClient();
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(tencentCloudProperties.getBucketName())
                            .method(Method.GET)
                            .object(path)
                            // 设置有效时间15分钟
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }
}
