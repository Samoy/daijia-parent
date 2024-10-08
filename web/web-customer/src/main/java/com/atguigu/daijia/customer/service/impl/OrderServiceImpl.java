package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.client.CouponFeignClient;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.OrderService;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.map.client.WxPayFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.enums.PayWay;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.payment.CreateWxPaymentForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private MapFeignClient mapFeignClient;

    @Resource
    private FeeRuleFeignClient feeRuleFeignClient;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private NewOrderFeignClient newOrderFeignClient;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private CustomerInfoFeignClient customerInfoFeignClient;

    @Resource
    private WxPayFeignClient wxPayFeignClient;

    @Resource
    private CouponFeignClient couponFeignClient;

    @Override
    public ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm) {
        // 获取驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(expectOrderForm, calculateDrivingLineForm);

        Result<DrivingLineVo> result = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        DrivingLineVo drivingLineVo = result.getData();

        // 获取订单费用
        FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(drivingLineVo.getDistance());
        feeRuleRequestForm.setStartTime(DateTime.now().toDate());
        feeRuleRequestForm.setWaitMinute(0);

        Result<FeeRuleResponseVo> feeRuleResponseVoResult = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(feeRuleResponseVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoResult.getData();
        // 预估订单实体
        ExpectOrderVo expectOrderVo = new ExpectOrderVo();
        expectOrderVo.setDrivingLineVo(drivingLineVo);
        expectOrderVo.setFeeRuleResponseVo(feeRuleResponseVo);
        return expectOrderVo;
    }

    @Override
    public Long submitOrder(SubmitOrderForm submitOrderForm) {
        // 重新计算驾驶路线
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(submitOrderForm, calculateDrivingLineForm);
        Result<DrivingLineVo> drivingLineVoResult = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(drivingLineVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        DrivingLineVo drivingLineVo = drivingLineVoResult.getData();

        // 重新计算费用
        FeeRuleRequestForm calculateOrderFeeForm = new FeeRuleRequestForm();
        calculateOrderFeeForm.setDistance(drivingLineVo.getDistance());
        calculateOrderFeeForm.setStartTime(DateTime.now().toDate());
        calculateOrderFeeForm.setWaitMinute(0);
        Result<FeeRuleResponseVo> feeRuleResponseVoResult = feeRuleFeignClient.calculateOrderFee(calculateOrderFeeForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(feeRuleResponseVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoResult.getData();

        // 封装数据
        OrderInfoForm orderInfoForm = new OrderInfoForm();
        BeanUtils.copyProperties(submitOrderForm, orderInfoForm);
        orderInfoForm.setExpectDistance(drivingLineVo.getDistance());
        orderInfoForm.setExpectAmount(feeRuleResponseVo.getTotalAmount());

        Result<Long> orderInfoResult = orderInfoFeignClient.saveOrderInfo(orderInfoForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        Long orderId = orderInfoResult.getData();
        // 查询附近可以接单的司机
        NewOrderTaskVo newOrderDispatchVo = new NewOrderTaskVo();
        BeanUtils.copyProperties(orderInfoForm, newOrderDispatchVo);
        newOrderDispatchVo.setOrderId(orderId);
        newOrderDispatchVo.setExpectTime(drivingLineVo.getDuration());
        newOrderDispatchVo.setCreateTime(new Date());
        Result<Long> taskResult = newOrderFeignClient.addAndStartTask(newOrderDispatchVo);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(taskResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return orderId;
    }

    @Override
    public Integer getOrderStatus(Long orderId) {
        Result<Integer> result = orderInfoFeignClient.getOrderStatus(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        Result<CurrentOrderInfoVo> result = orderInfoFeignClient.searchCustomerCurrentOrder(customerId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long customerId) {
        Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderInfo orderInfo = orderInfoResult.getData();
        if (!Objects.equals(orderInfo.getCustomerId(), customerId)) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        // 获取司机信息
        Long driverId = orderInfo.getDriverId();
        DriverInfoVo driverInfoVo = null;
        if (driverId != null) {
            Result<DriverInfoVo> driverInfoResult = driverInfoFeignClient.getDriverInfo(driverId);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(driverInfoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            driverInfoVo = driverInfoResult.getData();
        }
        // 获取账单信息
        OrderBillVo orderBillVo = null;
        if (orderInfo.getStatus() >= OrderStatus.UNPAID.getStatus()) {
            Result<OrderBillVo> orderBillInfoResult = orderInfoFeignClient.getOrderBillInfo(orderId);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(orderBillInfoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            orderBillVo = orderBillInfoResult.getData();
        }
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderId(orderId);
        orderInfoVo.setDriverInfoVo(driverInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        return orderInfoVo;
    }

    @Override
    public DriverInfoVo getDriverInfo(Long orderId, Long customerId) {
        Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderInfo orderInfo = orderInfoResult.getData();
        if (!Objects.equals(orderInfo.getCustomerId(), customerId)) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        Result<DriverInfoVo> result = driverInfoFeignClient.getDriverInfo(orderInfo.getDriverId());

        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        Result<OrderLocationVo> cacheOrderLocationResult = locationFeignClient.getCacheOrderLocation(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(cacheOrderLocationResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return cacheOrderLocationResult.getData();
    }

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        Result<DrivingLineVo> drivingLineVoResult = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(drivingLineVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return drivingLineVoResult.getData();
    }

    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        Result<OrderServiceLastLocationVo> result = locationFeignClient.getOrderServiceLastLocation(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public PageVo<OrderListVo> findCustomerOrderPage(Long customerId, Long page, Long limit) {
        Result<PageVo<OrderListVo>> result = orderInfoFeignClient.findCustomerOrderPage(customerId, page, limit);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public WxPrepayVo createWxPayment(CreateWxPaymentForm createWxPaymentForm) {
        // 获取订单支付信息
        Result<OrderPayVo> orderPayVoResult = orderInfoFeignClient.getOrderPayVo(createWxPaymentForm.getOrderNo(), createWxPaymentForm.getCustomerId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderPayVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderPayVo orderPayVo = orderPayVoResult.getData();
        if (!Objects.equals(orderPayVo.getStatus(), OrderStatus.UNPAID.getStatus())) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        // 获取乘客和司机openId
        Result<String> customerOpenIdResult = customerInfoFeignClient.getCustomerOpenId(orderPayVo.getCustomerId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(customerOpenIdResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        String customerOpenId = customerOpenIdResult.getData();
        Result<String> driverOpenIdResult = driverInfoFeignClient.getDriverOpenId(orderPayVo.getDriverId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(driverOpenIdResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        String driverOpenId = driverOpenIdResult.getData();
        // 处理优惠券
        BigDecimal couponAmount = null;
        if (null == orderPayVo.getCouponAmount()
                && null != createWxPaymentForm.getCustomerCouponId()
                && createWxPaymentForm.getCustomerCouponId() != 0) {
            UseCouponForm useCouponForm = new UseCouponForm();
            useCouponForm.setCustomerCouponId(createWxPaymentForm.getCustomerCouponId());
            useCouponForm.setOrderId(orderPayVo.getOrderId());
            useCouponForm.setCustomerId(createWxPaymentForm.getCustomerId());
            useCouponForm.setOrderAmount(orderPayVo.getPayAmount());
            Result<BigDecimal> useCouponResult = couponFeignClient.useCoupon(useCouponForm);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(useCouponResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            couponAmount = useCouponResult.getData();
        }
        BigDecimal payAmount = orderPayVo.getPayAmount();
        if (couponAmount != null && couponAmount.compareTo(BigDecimal.ZERO) != 0) {
            Result<Boolean> updateCouponAmountResult = orderInfoFeignClient.updateCouponAmount(orderPayVo.getOrderId(), couponAmount);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(updateCouponAmountResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            payAmount = payAmount.subtract(couponAmount);
        }

        // 发起微信支付
        PaymentInfoForm paymentInfoForm = new PaymentInfoForm();
        paymentInfoForm.setCustomerOpenId(customerOpenId);
        paymentInfoForm.setDriverOpenId(driverOpenId);
        paymentInfoForm.setAmount(payAmount);
        paymentInfoForm.setContent(orderPayVo.getContent());
        paymentInfoForm.setOrderNo(orderPayVo.getOrderNo());
        paymentInfoForm.setPayWay(PayWay.WECHAT.getCode());
        Result<WxPrepayVo> wxPaymentResult = wxPayFeignClient.createWxPayment(paymentInfoForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(wxPaymentResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return wxPaymentResult.getData();
    }

    @Override
    public Boolean queryPayStatus(String orderNo) {
        Result<Boolean> result = wxPayFeignClient.queryPayStatus(orderNo);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

}
