package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.MinioProperties;
import com.atguigu.daijia.driver.service.CiService;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CosServiceImpl implements CosService {

    @Resource
    private MinioProperties minioProperties;
    @Resource
    private CiService ciService;

    private MinioClient getMinioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpointUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    @Override
    public CosUploadVo upload(MultipartFile file, String path) {
        String uploadPath = "";
        // 创建一个Minio的客户端对象
        MinioClient minioClient = getMinioClient();
        try {
            // 判断桶是否存在
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucketName()).build());
            if (!found) {       // 如果不存在，那么此时就创建一个新的桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
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
                        .bucket(minioProperties.getBucketName())
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
        // 图片图片审核功能
        Boolean audited = ciService.imageAudit(uploadPath);
        if (!audited) {
            // 删除图片
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(uploadPath)
                        .build());
            } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                     InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                     XmlParserException e) {
                throw new RuntimeException(e);
            }
            throw new GuiguException(ResultCodeEnum.IMAGE_AUDITION_FAIL);
        }
        CosUploadVo cosUploadVo = new CosUploadVo();
        cosUploadVo.setUrl(uploadPath);
        String imageUrl = getImageUrl(uploadPath);
        cosUploadVo.setShowUrl(imageUrl);
        return cosUploadVo;
    }

    @Override
    public String getImageUrl(String path) {
        MinioClient minioClient = getMinioClient();
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioProperties.getBucketName())
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
