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
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
        // 获取账单和分账数据
        OrderBillVo orderBillVo = null;
        OrderProfitsharingVo orderProfitsharingVo = null;
        if (orderInfo.getStatus() >= OrderStatus.END_SERVICE.getStatus()) {
            Result<OrderBillVo> orderBillInfoResult = orderInfoFeignClient.getOrderBillInfo(orderId);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(orderBillInfoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            orderBillVo = orderBillInfoResult.getData();
            Result<OrderProfitsharingVo> orderProfitsharingResult = orderInfoFeignClient.getOrderProfitsharing(orderId);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(orderProfitsharingResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            orderProfitsharingVo = orderProfitsharingResult.getData();
        }

        OrderInfoVo orderInfoVo = new OrderInfoVo();
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderId(orderId);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setOrderProfitsharingVo(orderProfitsharingVo);
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


    @SneakyThrows
    public Boolean endDriveThread(OrderFeeForm orderFeeForm) {
        BigDecimal tollFee = orderFeeForm.getTollFee() == null ? BigDecimal.ZERO : orderFeeForm.getTollFee();
        BigDecimal parkingFee = orderFeeForm.getParkingFee() == null ? BigDecimal.ZERO : orderFeeForm.getParkingFee();
        BigDecimal otherFee = orderFeeForm.getOtherFee() == null ? BigDecimal.ZERO : orderFeeForm.getOtherFee();

        // 1. 根据订单id获取订单信息
        CompletableFuture<OrderInfo> orderInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId());
            if (!ResultCodeEnum.SUCCESS.getCode().equals(orderInfoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            OrderInfo orderInfo = orderInfoResult.getData();
            if (!Objects.equals(orderInfo.getDriverId(), orderFeeForm.getDriverId())) {
                throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
            }
            return orderInfo;
        });

        // 防止刷单
        CompletableFuture<OrderServiceLastLocationVo> orderServiceLastLocationVoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Result<OrderServiceLastLocationVo> locationVoResult = locationFeignClient.getOrderServiceLastLocation(orderFeeForm.getOrderId());
            if (!ResultCodeEnum.SUCCESS.getCode().equals(locationVoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            return locationVoResult.getData();
        });

        CompletableFuture.allOf(orderInfoCompletableFuture, orderServiceLastLocationVoCompletableFuture).join();

        OrderInfo orderInfo = orderInfoCompletableFuture.get();
        OrderServiceLastLocationVo orderServiceLastLocationVo = orderServiceLastLocationVoCompletableFuture.get();

        // 2. 计算实际里程
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(), orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(), orderServiceLastLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_END_LOCATION_DISTION) {
            throw new GuiguException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        CompletableFuture<BigDecimal> realDistanceCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Result<BigDecimal> distanceResult = locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId());
            if (!ResultCodeEnum.SUCCESS.getCode().equals(distanceResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            return distanceResult.getData();
        });


        // 3. 计算实际费用
        CompletableFuture<FeeRuleResponseVo> feeRuleResponseVoCompletableFuture = realDistanceCompletableFuture.thenApplyAsync(realDistance -> {
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
            totalAmount = totalAmount
                    .add(tollFee)
                    .add(parkingFee)
                    .add(otherFee)
                    .add(orderInfo.getFavourFee() == null ? BigDecimal.ZERO : orderInfo.getFavourFee());
            feeRuleResponseVo.setTotalAmount(totalAmount);
            return feeRuleResponseVo;
        });

        // 4. 计算奖励金额
        CompletableFuture<Long> orderNumCompletableFuture = CompletableFuture.supplyAsync(() -> {
            String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
            String endTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 23:59:59";
            Result<Long> orderNumResult = orderInfoFeignClient.getOrderNumByTime(orderInfo.getDriverId(), startTime, endTime);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(orderNumResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            return orderNumResult.getData();
        });

        CompletableFuture<RewardRuleResponseVo> rewardRuleResponseVoCompletableFuture = orderNumCompletableFuture.thenApplyAsync(orderNum -> {
            RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
            rewardRuleRequestForm.setOrderNum(orderNum);
            rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            Result<RewardRuleResponseVo> rewardRuleResponseVoResult = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(rewardRuleResponseVoResult.getCode())) {
                throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
            }
            return rewardRuleResponseVoResult.getData();
        });


        // 5. 计算分润金额
        CompletableFuture<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoCompletableFuture = feeRuleResponseVoCompletableFuture
                .thenCombineAsync(orderNumCompletableFuture, (feeRuleResponseVo, orderNum) -> {
                    ProfitsharingRuleRequestForm profilesharingRuleRequestForm = new ProfitsharingRuleRequestForm();
                    profilesharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
                    profilesharingRuleRequestForm.setOrderNum(orderNum);
                    Result<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoResult = profilesharingRuleFeignClient.calculateOrderProfitsharingFee(profilesharingRuleRequestForm);
                    if (!ResultCodeEnum.SUCCESS.getCode().equals(profitsharingRuleResponseVoResult.getCode())) {
                        throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
                    }
                    return profitsharingRuleResponseVoResult.getData();
                });

        CompletableFuture.allOf(
                orderInfoCompletableFuture,
                realDistanceCompletableFuture,
                feeRuleResponseVoCompletableFuture,
                orderNumCompletableFuture,
                rewardRuleResponseVoCompletableFuture,
                profitsharingRuleResponseVoCompletableFuture).join();


        // 6. 结束订单
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(updateOrderBillForm.getTollFee() == null ? BigDecimal.ZERO : updateOrderBillForm.getTollFee());
        updateOrderBillForm.setParkingFee(updateOrderBillForm.getParkingFee() == null ? BigDecimal.ZERO : updateOrderBillForm.getParkingFee());
        updateOrderBillForm.setOtherFee(updateOrderBillForm.getOtherFee() == null ? BigDecimal.ZERO : updateOrderBillForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee() == null ? BigDecimal.ZERO : orderInfo.getFavourFee());

        //实际里程
        updateOrderBillForm.setRealDistance(realDistanceCompletableFuture.get());
        BeanUtils.copyProperties(rewardRuleResponseVoCompletableFuture.get(), updateOrderBillForm);
        BeanUtils.copyProperties(feeRuleResponseVoCompletableFuture.get(), updateOrderBillForm);
        BeanUtils.copyProperties(profitsharingRuleResponseVoCompletableFuture.get(), updateOrderBillForm);

        Result<Boolean> endResult = orderInfoFeignClient.endDrive(updateOrderBillForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(endResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return endResult.getData();
    }

    @Override
    public PageVo<OrderListVo> findDriverOrderPage(Long driverId, Long page, Long limit) {
        Result<PageVo<OrderListVo>> result = orderInfoFeignClient.findDriverOrderPage(driverId, page, limit);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        Result<Boolean> result = orderInfoFeignClient.sendOrderBillInfo(orderId, driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
