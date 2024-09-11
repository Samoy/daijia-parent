package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Resource
    private CouponInfoMapper couponInfoMapper;

    @Resource
    private CustomerCouponMapper customerCouponMapper;

    @Resource
    private RedissonClient redissonClient;


    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }


    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public Boolean receive(Long customerId, Long couponId) {
        // 查询优惠券信息
        CouponInfo couponInfo = getById(couponId);
        if (couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 判断优惠券是否过期
        if (couponInfo.getExpireTime().before(new Date())) {
            throw new GuiguException(ResultCodeEnum.COUPON_EXPIRE);
        }
        // 检查库存
        if (couponInfo.getPublishCount() != 0 && couponInfo.getReceiveCount() >= couponInfo.getPublishCount()) {
            throw new GuiguException(ResultCodeEnum.COUPON_LESS);
        }
        RLock lock = null;
        try {
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean flag = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (flag) {
                // 检查每个人限制领取数量
                if (couponInfo.getPerLimit() > 0) {
                    LambdaQueryWrapper<CustomerCoupon> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CustomerCoupon::getCustomerId, customerId)
                            .eq(CustomerCoupon::getCouponId, couponId);
                    Long count = customerCouponMapper.selectCount(wrapper);
                    if (count >= couponInfo.getPerLimit()) {
                        throw new GuiguException(ResultCodeEnum.COUPON_USER_LIMIT);
                    }
                }
                // 优惠券领取
                // 更新领取数量
                int rows = couponInfoMapper.updateReceiveCount(couponId);
                // 添加领取记录
                if (rows == 1) {
                    this.saveCustomerCoupon(customerId, couponId, couponInfo.getExpireTime());
                }
                return true;
            }
        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.SERVICE_ERROR);
        } finally {
            if (lock != null && lock.isLocked()) {
                lock.unlock();
            }
        }
        return true;
    }

    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        List<AvailableCouponVo> list = new ArrayList<>();
        List<NoUseCouponVo> noUseList = couponInfoMapper.findNoUseList(customerId);
        List<NoUseCouponVo> moneyList = noUseList.stream().filter(item -> item.getCouponType() == 1).toList(); // 现金券
        for (NoUseCouponVo noUseCouponVo : moneyList) {
            BigDecimal amount = noUseCouponVo.getAmount();
            if (noUseCouponVo.getConditionAmount().compareTo(BigDecimal.ZERO) == 0 && orderAmount.compareTo(amount) > 0) {
                list.add(this.buildBestNoUseCouponVo(noUseCouponVo, amount));
            }
            if (noUseCouponVo.getConditionAmount().compareTo(BigDecimal.ZERO) > 0
                    && orderAmount.compareTo(noUseCouponVo.getConditionAmount()) > 0) {
                list.add(this.buildBestNoUseCouponVo(noUseCouponVo, amount));
            }
        }
        List<NoUseCouponVo> discountList = noUseList.stream().filter(item -> item.getCouponType() == 2).toList(); // 折扣券
        for (NoUseCouponVo noUseCouponVo : discountList) {
            //折扣之后金额
            // 100 打8折  = 100 * 8 /10= 80
            BigDecimal discountAmount = orderAmount.multiply(noUseCouponVo.getDiscount())
                    .divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);

            BigDecimal amount = orderAmount.subtract(discountAmount);
            //2.2.1.没门槛
            if (noUseCouponVo.getConditionAmount().compareTo(BigDecimal.ZERO) == 0) {
                list.add(this.buildBestNoUseCouponVo(noUseCouponVo, amount));
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (noUseCouponVo.getConditionAmount().compareTo(BigDecimal.ZERO) > 0
                    && discountAmount.compareTo(noUseCouponVo.getConditionAmount()) > 0) {
                list.add(this.buildBestNoUseCouponVo(noUseCouponVo, amount));
            }
        }
        if (!CollectionUtils.isEmpty(list)) {
            list.sort(Comparator.comparing(AvailableCouponVo::getReduceAmount));
        }
        return list;
    }

    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        CustomerCoupon customerCoupon = customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if (customerCoupon == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if (couponInfo == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        if (!Objects.equals(customerCoupon.getCustomerId(), useCouponForm.getCustomerId())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        BigDecimal reduceAmount = null;
        if (couponInfo.getCouponType() == 1) {
            //没有门槛，订单金额大于优惠减免金额
            if (couponInfo.getConditionAmount().doubleValue() == 0
                    && useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue() > 0) {
                reduceAmount = couponInfo.getAmount();
            }

            //有门槛，订单金额大于优惠卷门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0
                    && useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                reduceAmount = couponInfo.getAmount();
            }
        } else if (couponInfo.getCouponType() == 2) {
            BigDecimal discountOrderAmount = useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount())
                    .divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
            //订单优惠金额
            //2.2.1.没门槛
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
        }
        if (reduceAmount != null && reduceAmount.compareTo(BigDecimal.ZERO) > 0) {
            //更新coupon_info使用数量
            //根据id查询优惠卷对象
            Integer useCountOld = couponInfo.getUseCount();
            couponInfo.setUseCount(useCountOld + 1);
            couponInfoMapper.updateById(couponInfo);

            //更新customer_coupon
            CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
            updateCustomerCoupon.setId(customerCoupon.getId());
            updateCustomerCoupon.setUsedTime(new Date());
            updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
            customerCouponMapper.updateById(updateCustomerCoupon);

            return reduceAmount;
        }
        return BigDecimal.ZERO;
    }

    private void saveCustomerCoupon(Long customerId, Long couponId, Date expireTime) {
        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setCouponId(couponId);
        customerCoupon.setExpireTime(expireTime);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setStatus(1); // 1表示未使用
        customerCouponMapper.insert(customerCoupon);
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal amount) {
        AvailableCouponVo bestNoUseCouponVo = new AvailableCouponVo();
        BeanUtils.copyProperties(noUseCouponVo, bestNoUseCouponVo);
        bestNoUseCouponVo.setCouponId(noUseCouponVo.getId());
        bestNoUseCouponVo.setReduceAmount(amount);
        return bestNoUseCouponVo;
    }
}
