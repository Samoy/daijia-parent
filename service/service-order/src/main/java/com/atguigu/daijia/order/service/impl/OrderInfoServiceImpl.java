package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.*;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private OrderInfoMapper orderInfoMapper;

    @Resource
    private OrderStatusLogMapper orderStatusLogMapper;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private OrderMonitorService orderMonitorService;

    @Resource
    private OrderBillMapper orderBillMapper;

    @Resource
    private OrderProfitsharingMapper orderProfitsharingMapper;

    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm, orderInfo);
        // 订单号
        orderInfo.setOrderNo(UUID.randomUUID().toString().replaceAll("-", ""));
        // 订单状态
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        orderInfoMapper.insert(orderInfo);
        // 发送延迟消息
        this.sendDelayMessage(orderInfo.getId());
        // 订单状态日志
        log(orderInfo.getId(), orderInfo.getStatus());

        // 向Redis中添加标识
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK, "0",
                RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);

        return orderInfo.getId();
    }

    private void sendDelayMessage(Long id) {
        try {
            RBoundedBlockingQueue<Object> blockingQueue = redissonClient.getBoundedBlockingQueue(SystemConstant.QUEUE_CANCEL_ORDER);
            RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
            delayedQueue.offer(id, SystemConstant.QUEUE_CANCEL_ORDER_DELAY_TIME, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
    }

    @Override
    public Integer getOrderStatus(Long orderId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId)
                .select(OrderInfo::getStatus);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        if (orderInfo == null) {
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        // 1. 判断订单是否存在
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK))) {
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        // 2. 创建锁
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);
        try {
            // 尝试获取锁，10秒内可重试，持有5秒
            boolean flag = lock.tryLock(10, 5, TimeUnit.SECONDS);
            if (flag) {
                // 再次检查订单是否存在，确保并发安全
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK))) {
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }
                // 3. 司机抢单
                LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(OrderInfo::getId, orderId);
                OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
                // 更新订单信息，设置司机ID和订单状态为已接受
                orderInfo.setDriverId(driverId);
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setAcceptTime(new Date());
                // 更新数据库中的订单信息
                int rows = orderInfoMapper.updateById(orderInfo);
                if (rows != 1) {
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }
            }
        } catch (InterruptedException e) {
            // 获取锁过程中被中断
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        } finally {
            // 释放锁
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
        // 删除Redis中标记订单存在的键
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
        return true;
    }

    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(), OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(), OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(), OrderStatus.UNPAID.getStatus()
        };
        wrapper.eq(OrderInfo::getCustomerId, customerId)
                .in(OrderInfo::getStatus, Arrays.asList(statusArray))
                .orderByDesc(OrderInfo::getId)
                .last(" LIMIT 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if (orderInfo != null) {
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        //封装条件
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(),
        };
        wrapper.eq(OrderInfo::getDriverId, driverId)
                .in(OrderInfo::getStatus, Arrays.asList(statusArray))
                .orderByDesc(OrderInfo::getId)
                .last(" LIMIT 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        //封装到vo
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if (null != orderInfo) {
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        // 更新订单状态和到达时间，条件：orderId + driverId
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getDriverId, driverId);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        orderInfo.setArriveTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if (rows == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        LambdaQueryWrapper<OrderInfo> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(OrderInfo::getId, updateOrderCartForm.getOrderId())
                .eq(OrderInfo::getDriverId, updateOrderCartForm.getDriverId());
        OrderInfo updateOrderInfo = new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm, updateOrderInfo);
        updateOrderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());
        int row = orderInfoMapper.update(updateOrderInfo, updateWrapper);
        if (row == 1) {
            this.log(updateOrderCartForm.getOrderId(), OrderStatus.UPDATE_CART_INFO.getStatus());
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, startDriveForm.getOrderId())
                .eq(OrderInfo::getDriverId, startDriveForm.getDriverId());
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        orderInfo.setStartServiceTime(new Date());
        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if (rows == 1) {
            this.log(startDriveForm.getOrderId(), OrderStatus.START_SERVICE.getStatus());
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        OrderMonitor orderMonitor = new OrderMonitor();
        orderMonitor.setOrderId(startDriveForm.getOrderId());
        return orderMonitorService.save(orderMonitor);
    }

    @Override
    public Long getOrderNumByTime(Long driverId, String startTime, String endTime) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getDriverId, driverId)
                .ge(OrderInfo::getStartServiceTime, startTime)
                .le(OrderInfo::getEndServiceTime, endTime);
        return orderInfoMapper.selectCount(wrapper);
    }

    @Override
    public Boolean endDrive(UpdateOrderBillForm updateOrderBillForm) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, updateOrderBillForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId, updateOrderBillForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        orderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        orderInfo.setFavourFee(updateOrderBillForm.getFavourFee());
        orderInfo.setRealDistance(updateOrderBillForm.getRealDistance());
        orderInfo.setEndServiceTime(new Date());

        int rows = orderInfoMapper.update(orderInfo, wrapper);

        if (rows == 1) {
            //添加账单数据
            OrderBill orderBill = new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm, orderBill);
            orderBill.setPayAmount(updateOrderBillForm.getTotalAmount());
            orderBillMapper.insert(orderBill);

            //添加分账信息
            OrderProfitsharing orderProfitsharing = new OrderProfitsharing();
            BeanUtils.copyProperties(updateOrderBillForm, orderProfitsharing);
            orderProfitsharing.setRuleId(new Date().getTime());
            orderProfitsharing.setStatus(1);
            orderProfitsharingMapper.insert(orderProfitsharing);

        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    @Override
    public PageVo<OrderListVo> findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectCustomerOrderPage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public PageVo<OrderListVo> findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectDriverOrderPage(pageParam, driverId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        LambdaQueryWrapper<OrderBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderBill::getOrderId, orderId);
        OrderBill orderBill = orderBillMapper.selectOne(wrapper);
        OrderBillVo orderBillVo = new OrderBillVo();
        BeanUtils.copyProperties(orderBill, orderBillVo);
        return orderBillVo;
    }

    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        LambdaQueryWrapper<OrderProfitsharing> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderProfitsharing::getOrderId, orderId);
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(wrapper);
        OrderProfitsharingVo orderProfitsharingVo = new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing, orderProfitsharingVo);
        return orderProfitsharingVo;
    }

    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
        OrderPayVo orderPayVo = orderInfoMapper.selectOrderPayVo(orderNo, customerId);
        if (orderPayVo != null) {
            String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
            orderPayVo.setContent(content);
        }
        return orderPayVo;
    }

    @Override
    public Boolean updateOrderPayStatus(Long orderNo) {
        //1 根据订单编号查询，判断订单状态
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wrapper);
        if (orderInfo == null || Objects.equals(orderInfo.getStatus(), OrderStatus.PAID.getStatus())) {
            return true;
        }

        //2 更新状态
        LambdaQueryWrapper<OrderInfo> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(OrderInfo::getOrderNo, orderNo);

        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.PAID.getStatus());
        updateOrderInfo.setPayTime(new Date());

        int rows = orderInfoMapper.update(updateOrderInfo, updateWrapper);

        if (rows == 1) {
            return true;
        } else {
            throw new GuiguException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    @Override
    public OrderRewardVo getOrderRewardFee(String orderNo) {
        //根据订单编号查询订单表
        OrderInfo orderInfo =
                orderInfoMapper.selectOne(
                        new LambdaQueryWrapper<OrderInfo>()
                                .eq(OrderInfo::getOrderNo, orderNo)
                                .select(OrderInfo::getId, OrderInfo::getDriverId));

        //根据订单id查询系统奖励表
        OrderBill orderBill =
                orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>()
                        .eq(OrderBill::getOrderId, orderInfo.getId())
                        .select(OrderBill::getRewardFee));

        //封装到vo里面
        OrderRewardVo orderRewardVo = new OrderRewardVo();
        orderRewardVo.setOrderId(orderInfo.getId());
        orderRewardVo.setDriverId(orderInfo.getDriverId());
        orderRewardVo.setRewardFee(orderBill.getRewardFee());
        return orderRewardVo;
    }

    @Override
    public void cancelOrder(Long orderId) {
        OrderInfo orderInfo = this.getById(orderId);
        if (Objects.equals(orderInfo.getStatus(), OrderStatus.WAITING_ACCEPT.getStatus())) {
            orderInfo.setStatus(OrderStatus.CANCEL_ORDER.getStatus());
            int rows = orderInfoMapper.updateById(orderInfo);
            if (rows == 1) {
                // 删除接单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }
        }
    }


    // 通过乐观锁实现抢单
    private Boolean robNewOrderByOptimisticLock(Long driverId, Long orderId) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK))) {
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        LambdaUpdateWrapper<OrderInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(OrderInfo::getId, orderId)
                // 此语句可用于实现乐观锁的简单实现，即仅当订单状态为待接单时，才更新订单状态为已接单
                // 其他状态的订单，将不会被更新
                .eq(OrderInfo::getStatus, OrderStatus.WAITING_ACCEPT.getStatus())
                .set(OrderInfo::getDriverId, driverId)
                .set(OrderInfo::getStatus, OrderStatus.ACCEPTED.getStatus())
                .set(OrderInfo::getAcceptTime, new Date());
        OrderInfo orderInfo = new OrderInfo();
        int rows = orderInfoMapper.update(orderInfo, updateWrapper);
        if (rows != 1) {
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
        return true;
    }

    private void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setCreateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
