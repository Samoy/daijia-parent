package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.order.client.OrderMonitorFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MonitorServiceImpl implements MonitorService {

    @Resource
    private FileService fileService;

    @Resource
    private OrderMonitorFeignClient orderMonitorFeignClient;

    @Override
    public Boolean upload(MultipartFile file, OrderMonitorForm orderMonitorForm) {
        String url = fileService.upload(file);
        OrderMonitorRecord orderMonitorRecord = new OrderMonitorRecord();
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        orderMonitorRecord.setFileUrl(url);
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        Result<Boolean> result = orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
