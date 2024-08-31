package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.CiFeignClient;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
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

    @Resource
    private CiFeignClient ciFeignClient;

    @Override
    public Boolean upload(MultipartFile file, OrderMonitorForm orderMonitorForm) {
        String url = fileService.upload(file);
        // 保存订单监控记录信息
        OrderMonitorRecord orderMonitorRecord = new OrderMonitorRecord();
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        orderMonitorRecord.setFileUrl(url);
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        // 增加文本审核
        Result<TextAuditingVo> textAuditingVoResult = ciFeignClient.textAuditing(orderMonitorRecord.getContent());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(textAuditingVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        // 记录审核结果
        TextAuditingVo textAuditingVo = textAuditingVoResult.getData();
        orderMonitorRecord.setKeywords(textAuditingVo.getKeywords());
        Result<Boolean> result = orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        // 更新订单监控统计
        Result<OrderMonitor> orderMonitorResult = orderMonitorFeignClient.getOrderMonitor(orderMonitorForm.getOrderId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderMonitorResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderMonitor orderMonitor = orderMonitorResult.getData();
        int fileNum = orderMonitor.getFileNum() + 1;
        orderMonitor.setFileNum(fileNum);
        // 0: 审核正常 1： 判定违规 2： 疑似违规
        if ("2".equals(orderMonitorRecord.getResult())) {
            orderMonitor.setAuditNum(orderMonitor.getAuditNum() + 1);
        }
        Result<Boolean> updateResult = orderMonitorFeignClient.updateOrderMonitor(orderMonitor);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(updateResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return updateResult.getData();
    }
}
