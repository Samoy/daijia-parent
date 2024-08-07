package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.OcrFeignClient;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    @Resource
    private OcrFeignClient ocrFeignClient;

    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        Result<IdCardOcrVo> result = ocrFeignClient.idCardOcr(file);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }
}
