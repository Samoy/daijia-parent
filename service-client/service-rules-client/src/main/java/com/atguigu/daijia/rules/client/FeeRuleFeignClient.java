package com.atguigu.daijia.rules.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.common.result.Result;


@FeignClient(value = "service-rules")
public interface FeeRuleFeignClient {

    /**
     * 计算订单费用
     *
     * @param calculateOrderFeeForm 计算费用表单
     * @return 计算费用结果
     */
    @PostMapping("/rules/fee/calculateOrderFee")
    Result<FeeRuleResponseVo> calculateOrderFee(@RequestBody FeeRuleRequestForm calculateOrderFeeForm);

}