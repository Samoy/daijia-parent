package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private NewOrderFeignClient newOrderFeignClient;

    @Resource
    private MapFeignClient mapFeignClient;

    @Resource
    private LocationFeignClient locationFeignClient;

    @Resource
    private FeeRuleFeignClient feeRuleFeignClient;

    @Resource
    private RewardRuleFeignClient rewardRuleFeignClient;

    @Resource
    private ProfitsharingRuleFeignClient profilesharingRuleFeignClient;

    @Override
    public Integer getOrderStatus(Long orderId) {
        Result<Integer> result = orderInfoFeignClient.getOrderStatus(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        Result<List<NewOrderDataVo>> newOrderVOListResult = newOrderFeignClient.findNewOrderQueueData(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(newOrderVOListResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return newOrderVOListResult.getData();
    }

    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        Result<Boolean> result = orderInfoFeignClient.robNewOrder(driverId, orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        Result<CurrentOrderInfoVo> result = orderInfoFeignClient.searchDriverCurrentOrder(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderInfo orderInfo = orderInfoResult.getData();
        if (!Objects.equals(orderInfo.getDriverId(), driverId)) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        OrderInfoVo orderInfoVo = new OrderInfoVo();
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderId(orderId);
        return orderInfoVo;
    }

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        Result<DrivingLineVo> result = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderInfo orderInfo = orderInfoResult.getData();
        Result<OrderLocationVo> orderLocationVoResult = locationFeignClient.getCacheOrderLocation(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderLocationVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderLocationVo orderLocationVo = orderLocationVoResult.getData();
        double distance = LocationUtil.getDistance(orderInfo.getStartPointLatitude().doubleValue(), orderInfo.getStartPointLongitude().doubleValue(),
                orderLocationVo.getLatitude().doubleValue(), orderLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_START_LOCATION_DISTION_ERROR);
        }
        Result<Boolean> result = orderInfoFeignClient.driverArriveStartLocation(orderId, driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        Result<Boolean> result = orderInfoFeignClient.updateOrderCart(updateOrderCartForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        Result<Boolean> result = orderInfoFeignClient.startDrive(startDriveForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean endDrive(OrderFeeForm orderFeeForm) {
        // 1. 根据订单id获取订单信息
        Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderInfo orderInfo = orderInfoResult.getData();
        if (!Objects.equals(orderInfo.getDriverId(), orderFeeForm.getDriverId())) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        // 防止刷单
        Result<OrderServiceLastLocationVo> locationVoResult = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(locationVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderServiceLastLocationVo orderServiceLastLocationVo = locationVoResult.getData();
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(), orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(), orderServiceLastLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_END_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }
        // 2. 计算实际里程
        Result<BigDecimal> distanceResult = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(distanceResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        BigDecimal realDistance = distanceResult.getData();
        // 3. 计算实际费用
        FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(realDistance);
        feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
        feeRuleRequestForm.setWaitMinute(DateUtils.truncatedCompareTo(orderInfo.getArriveTime(), orderInfo.getAcceptTime(), Calendar.MINUTE));
        Result<FeeRuleResponseVo> feeRuleResponseVoResult = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(feeRuleResponseVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoResult.getData();

        BigDecimal totalAmount = feeRuleResponseVo.getTotalAmount();
        if (Objects.isNull(totalAmount)) {
            totalAmount = BigDecimal.ZERO;
        }
        BigDecimal tollFee = orderFeeForm.getTollFee() == null ? BigDecimal.ZERO : orderFeeForm.getTollFee();
        BigDecimal parkingFee = orderFeeForm.getParkingFee() == null ? BigDecimal.ZERO : orderFeeForm.getParkingFee();
        BigDecimal otherFee = orderFeeForm.getOtherFee() == null ? BigDecimal.ZERO : orderFeeForm.getOtherFee();
        BigDecimal favoriteFee = orderInfo.getFavourFee() == null ? BigDecimal.ZERO : orderInfo.getFavourFee();
        totalAmount = totalAmount
                .add(tollFee)
                .add(parkingFee)
                .add(otherFee)
                .add(favoriteFee);
        feeRuleResponseVo.setTotalAmount(totalAmount);
        // 4. 计算奖励金额
        String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
        String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 23:59:59";
        Result<Long> orderNumResult = orderInfoFeignClient.getOrderNumByTime(orderInfo.getDriverId(), startTime, endTime);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderNumResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        Long orderNum = orderNumResult.getData();
        RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
        rewardRuleRequestForm.setOrderNum(orderNum);
        rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
        Result<RewardRuleResponseVo> rewardRuleResponseVoResult = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(rewardRuleResponseVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleResponseVoResult.getData();
        // 5. 计算分润金额
        ProfitsharingRuleRequestForm profilesharingRuleRequestForm = new ProfitsharingRuleRequestForm();
        profilesharingRuleRequestForm.setOrderAmount(totalAmount);
        profilesharingRuleRequestForm.setOrderNum(orderNum);
        Result<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoResult = profilesharingRuleFeignClient.calculateOrderProfitsharingFee(profilesharingRuleRequestForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(profitsharingRuleResponseVoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleResponseVoResult.getData();
        // 6. 结束订单
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(tollFee);
        updateOrderBillForm.setParkingFee(parkingFee);
        updateOrderBillForm.setOtherFee(otherFee);
        //乘客好处费
        updateOrderBillForm.setFavourFee(favoriteFee);

        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);

        Result<Boolean> endResult = orderInfoFeignClient.endDrive(updateOrderBillForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(endResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return endResult.getData();
    }
}
