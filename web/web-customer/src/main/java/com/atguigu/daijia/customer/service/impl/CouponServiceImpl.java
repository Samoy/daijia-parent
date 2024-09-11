package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.client.CouponFeignClient;
import com.atguigu.daijia.customer.service.CouponService;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.atguigu.daijia.model.vo.order.OrderBillVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    @Resource
    private CouponFeignClient couponFeignClient;

    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;

    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Long customerId, Long page, Long limit) {
        Result<PageVo<NoReceiveCouponVo>> result = couponFeignClient.findNoReceivePage(customerId, page, limit);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Long customerId, Long page, Long limit) {
        Result<PageVo<NoUseCouponVo>> result = couponFeignClient.findNoUsePage(customerId, page, limit);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public Boolean receive(Long customerId, Long couponId) {
        Result<Boolean> result = couponFeignClient.receive(customerId, couponId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, Long orderId) {
        Result<OrderBillVo> orderBillInfoResult = orderInfoFeignClient.getOrderBillInfo(orderId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderBillInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        OrderBillVo orderBillVo = orderBillInfoResult.getData();
        Result<List<AvailableCouponVo>> result = couponFeignClient.findAvailableCoupon(customerId, orderBillVo.getPayAmount());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    @Override
    public PageVo<UsedCouponVo> findUsedPage(Long customerId, Long page, Long limit) {
        Result<PageVo<UsedCouponVo>> result = couponFeignClient.findUsedPage(customerId, page, limit);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
