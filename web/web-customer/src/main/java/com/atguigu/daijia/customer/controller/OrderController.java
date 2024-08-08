package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "订单API接口管理")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Operation(summary = "查找乘客端当前订单")
    @GetMapping("/searchCustomerCurrentOrder")
    @GuiguLogin
    public Result<CurrentOrderInfoVo> searchCustomerCurrentOrder() {
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        // TODO:默认当前没有订单,后续完善
        currentOrderInfoVo.setIsHasCurrentOrder(false);
        return Result.ok(currentOrderInfoVo);
    }
}

