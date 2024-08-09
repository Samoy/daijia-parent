package com.atguigu.daijia.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(value = "service-order")
public interface OrderInfoFeignClient {

    /**
     * 保存订单信息
     *
     * @param orderInfoForm 订单信息表单
     * @return 订单id
     */
    @PostMapping("/order/info/saveOrderInfo")
    Result<Long> saveOrderInfo(@RequestBody OrderInfoForm orderInfoForm);
}