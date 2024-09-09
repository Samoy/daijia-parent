package com.atguigu.daijia.order.client;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import org.springframework.cloud.openfeign.FeignClient;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 查询订单状态
     *
     * @param orderId 订单id
     * @return 订单状态
     */
    @GetMapping("/order/info/getOrderStatus/{orderId}")
    Result<Integer> getOrderStatus(@PathVariable Long orderId);

    /**
     * 司机抢单
     *
     * @param driverId 司机id
     * @param orderId  订单id
     * @return 是否抢单成功
     */

    @GetMapping("/order/info/robNewOrder/{driverId}/{orderId}")
    Result<Boolean> robNewOrder(@PathVariable Long driverId, @PathVariable Long orderId);


    /**
     * 乘客端查找当前订单
     *
     * @param customerId 客户id
     * @return 乘客端当前订单
     */
    @GetMapping("/order/info/searchCustomerCurrentOrder/{customerId}")
    Result<CurrentOrderInfoVo> searchCustomerCurrentOrder(@PathVariable("customerId") Long customerId);


    /**
     * 司机端查找当前订单
     *
     * @param driverId 司机id
     * @return 司机端当前订单
     */
    @GetMapping("/order/info/searchDriverCurrentOrder/{driverId}")
    Result<CurrentOrderInfoVo> searchDriverCurrentOrder(@PathVariable("driverId") Long driverId);

    /**
     * 根据订单id获取订单信息
     *
     * @param orderId 订单id
     * @return 订单信息
     */
    @GetMapping("/order/info/getOrderInfo/{orderId}")
    Result<OrderInfo> getOrderInfo(@PathVariable("orderId") Long orderId);

    /**
     * 司机到达起始点
     *
     * @param orderId  订单id
     * @param driverId 司机id
     * @return 是否到达
     */
    @GetMapping("/order/info/driverArriveStartLocation/{orderId}/{driverId}")
    Result<Boolean> driverArriveStartLocation(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);

    /**
     * 更新代驾车辆信息
     *
     * @param updateOrderCartForm 更新代驾车辆信息表单
     * @return 是否更新成功
     */
    @PostMapping("/order/info//updateOrderCart")
    Result<Boolean> updateOrderCart(@RequestBody UpdateOrderCartForm updateOrderCartForm);


    /**
     * 开始代驾服务
     *
     * @param startDriveForm 开始服务表单
     * @return 是否更新成功
     */
    @PostMapping("/order/info/startDrive")
    Result<Boolean> startDrive(@RequestBody StartDriveForm startDriveForm);

    /**
     * 获取一段时间订单数量
     *
     * @param driverId  司机id
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 订单数量
     */
    @GetMapping("/order/info/getOrderNumByTime/{driverId}/{startTime}/{endTime}")
    Result<Long> getOrderNumByTime(@PathVariable Long driverId, @PathVariable String startTime, @PathVariable String endTime);


    /**
     * 结束代驾：服务更新订单账单
     *
     * @param updateOrderBillForm 更新订单账单表单
     * @return 是否成功
     */
    @PostMapping("/order/info/endDrive")
    Result<Boolean> endDrive(@RequestBody UpdateOrderBillForm updateOrderBillForm);


    /**
     * 获取乘客订单分页列表
     *
     * @param customerId 客户id
     * @param page       当前页面
     * @param limit      每页记录数
     * @return 分页列表
     */
    @GetMapping("/order/info/findCustomerOrderPage/{customerId}/{page}/{limit}")
    Result<PageVo<OrderListVo>> findCustomerOrderPage(@PathVariable("customerId") Long customerId,
                                                      @PathVariable("page") Long page,
                                                      @PathVariable("limit") Long limit);


    /**
     * 获取司机订单分页列表
     *
     * @param driverId 司机id
     * @param page     当前页码
     * @param limit    每页记录数
     * @return 分页列表
     */
    @GetMapping("/order/info/findDriverOrderPage/{driverId}/{page}/{limit}")
    Result<PageVo<OrderListVo>> findDriverOrderPage(@PathVariable("driverId") Long driverId,
                                                    @PathVariable("page") Long page,
                                                    @PathVariable("limit") Long limit);

    /**
     * 根据订单id获取实际账单信息
     *
     * @param orderId 订单id
     * @return 账单信息
     */
    @GetMapping("/order/info/getOrderBillInfo/{orderId}")
    Result<OrderBillVo> getOrderBillInfo(@PathVariable("orderId") Long orderId);

    /**
     * 根据订单id获取实际分账信息
     *
     * @param orderId 订单id
     * @return 分账信息
     */
    @GetMapping("/order/info/getOrderProfitsharing/{orderId}")
    Result<OrderProfitsharingVo> getOrderProfitsharing(@PathVariable("orderId") Long orderId);


    /**
     * 司机发送账单信息
     *
     * @param orderId  订单id
     * @param driverId 司机id
     * @return 是否成功
     */
    @GetMapping("/order/info/sendOrderBillInfo/{orderId}/{driverId}")
    Result<Boolean> sendOrderBillInfo(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);


    /**
     * 获取订单支付信息
     *
     * @param orderNo    订单号
     * @param customerId 客户id
     * @return 订单支付信息
     */
    @GetMapping("/order/info/getOrderPayVo/{orderNo}/{customerId}")
    Result<OrderPayVo> getOrderPayVo(@PathVariable("orderNo") String orderNo, @PathVariable("customerId") Long customerId);

    /**
     * 更改订单支付状态
     *
     * @param orderNo 订单号
     * @return 是否成功
     */
    @GetMapping("/order/info//updateOrderPayStatus/{orderNo}")
    Result<Boolean> updateOrderPayStatus(@PathVariable("orderNo") String orderNo);


    /**
     * 获取订单的系统奖励
     *
     * @param orderNo 订单号
     * @return 系统奖励
     */
    @GetMapping("/order/info/getOrderRewardFee/{orderNo}")
    Result<OrderRewardVo> getOrderRewardFee(@PathVariable("orderNo") String orderNo);


}