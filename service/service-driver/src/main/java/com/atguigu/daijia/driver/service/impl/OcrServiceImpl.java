package com.atguigu.daijia.driver.service.impl;

import com.alibaba.nacos.common.codec.Base64;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    @Resource
    private TencentCloudProperties tenantCloudProperties;
    @Resource
    private CosService cosService;

    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        try {
            byte[] base64 = Base64.encodeBase64(file.getBytes());
            IDCardOCRResponse resp = getIdCardOCRResponse(base64);
            IdCardOcrVo idCardOcrVo = new IdCardOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                // 身份证正面
                idCardOcrVo.setName(resp.getName());
                idCardOcrVo.setGender("男".equals(resp.getSex()) ? "1" : "2");
                idCardOcrVo.setBirthday(DateTimeFormat.forPattern("yyyy/MM/dd")
                        .parseDateTime(resp.getBirth()).toDate());
                // 上传身份证正面图片到COS
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardFrontUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                // 身份证反面
                String idcardExpire = resp.getValidDate().split("-")[1];
                idCardOcrVo.setIdcardExpire(DateTimeFormat.forPattern("yyyy.MM.dd")
                        .parseDateTime(idcardExpire).toDate());
                // 上传身份证反面图片到COS
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardBackUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardBackShowUrl(cosUploadVo.getShowUrl());
            }
            return idCardOcrVo;
        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    private IDCardOCRResponse getIdCardOCRResponse(byte[] base64) throws TencentCloudSDKException {
        String fileBase64 = new String(base64);
        Credential cred = new Credential(tenantCloudProperties.getSecretId(), tenantCloudProperties.getSecretKey());
        // 实例化一个http选项，可选的，没有特殊需求可以跳过
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ocr.tencentcloudapi.com");
        // 实例化一个client选项，可选的，没有特殊需求可以跳过
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 实例化要请求产品的client对象,clientProfile是可选的
        OcrClient client = new OcrClient(cred, tenantCloudProperties.getRegion(), clientProfile);
        // 实例化一个请求对象,每个接口都会对应一个request对象
        IDCardOCRRequest req = new IDCardOCRRequest();
        // 设置文件
        req.setImageBase64(fileBase64);
        // 返回的resp是一个IDCardOCRResponse的实例，与请求对象对应
        return client.IDCardOCR(req);
    }
}
