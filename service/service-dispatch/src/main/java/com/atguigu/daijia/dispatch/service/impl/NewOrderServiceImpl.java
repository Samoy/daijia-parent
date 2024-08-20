package com.atguigu.daijia.dispatch.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.mapper.OrderJobMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.dispatch.xxl.client.XxlJobClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.dispatch.OrderJob;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NewOrderServiceImpl implements NewOrderService {

    @Resource
    private XxlJobClient xxlJobClient;
    @Resource
    private OrderJobMapper orderJobMapper;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Long addAndStartTask(NewOrderTaskVo newOrderTaskVo) {
        LambdaQueryWrapper<OrderJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderJob::getOrderId, newOrderTaskVo.getOrderId());
        OrderJob orderJob = orderJobMapper.selectOne(wrapper);
        if (orderJob == null) {
            Long jobId = xxlJobClient.addJob("newOrderTaskHandler", "", "0 */1 * * * ?",
                    "新创建订单任务调度:" + newOrderTaskVo.getOrderId());
            orderJob = new OrderJob();
            orderJob.setOrderId(newOrderTaskVo.getOrderId());
            orderJob.setJobId(jobId);
            orderJob.setParameter(JSONObject.toJSONString(newOrderTaskVo));
            orderJobMapper.insert(orderJob);
        }
        return orderJob.getId();
    }

    @Override
    public void executeTask(long jobId) {
        // 根据JobId查询订单任务信息
        LambdaQueryWrapper<OrderJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderJob::getJobId, jobId);
        OrderJob orderJob = orderJobMapper.selectOne(wrapper);

        // 如果任务信息为空，则直接返回
        if (orderJob == null) {
            return;
        }

        // 解析任务参数为任务对象
        NewOrderTaskVo newOrderTaskVo = JSONObject.parseObject(orderJob.getParameter(), NewOrderTaskVo.class);

        // 调用远程服务获取订单状态
        Result<Integer> orderStatusResult = orderInfoFeignClient.getOrderStatus(newOrderTaskVo.getOrderId());

        // 如果订单状态查询失败，则抛出异常
        if (!ResultCodeEnum.SUCCESS.getCode().equals(orderStatusResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        // 获取订单状态
        Integer orderStatus = orderStatusResult.getData();

        // 如果订单状态不是已接受，则重新执行任务并返回
        if (!Objects.equals(orderStatus, OrderStatus.ACCEPTED.getStatus())) {
            xxlJobClient.startJob(jobId);
            return;
        }

        // 构建搜索附近司机的请求参数
        SearchNearByDriverForm searchNearByDriverForm = new SearchNearByDriverForm();
        searchNearByDriverForm.setLongitude(newOrderTaskVo.getStartPointLongitude());
        searchNearByDriverForm.setLatitude(newOrderTaskVo.getStartPointLatitude());
        searchNearByDriverForm.setMileageDistance(newOrderTaskVo.getExpectDistance());

        // 调用远程服务搜索附近司机
        Result<List<NearByDriverVo>> driverListResult = locationFeignClient.searchNearByDriver(searchNearByDriverForm);

        // 如果搜索附近司机失败，则抛出异常
        if (!ResultCodeEnum.SUCCESS.getCode().equals(driverListResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        // 获取附近司机列表
        List<NearByDriverVo> driverList = driverListResult.getData();

        // 遍历司机列表，向每位司机派发订单
        driverList.forEach(driver -> {
            // 构建Redis中用于防止重复派单的Key
            String repeatKey =
                    RedisConstant.DRIVER_ORDER_REPEAT_LIST + newOrderTaskVo.getOrderId();

            // 检查当前司机是否已经收到过此订单
            Boolean isExist = redisTemplate.opsForSet().isMember(repeatKey, driver.getDriverId());

            // 如果当前司机没有收到过此订单，则将其添加到去重集合，并设置过期时间
            if (Boolean.FALSE.equals(isExist)) {
                redisTemplate.opsForSet().add(repeatKey, driver.getDriverId());
                redisTemplate.expire(repeatKey, RedisConstant.DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME, TimeUnit.MINUTES);

                // 构建新的订单数据对象
                NewOrderDataVo newOrderDataVo = new NewOrderDataVo();
                BeanUtils.copyProperties(newOrderTaskVo, newOrderDataVo);
                newOrderDataVo.setDistance(driver.getDistance());
                // 构建Redis中临时存储订单信息的Key
                String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driver.getDriverId();

                // 将订单信息推送到司机的订单列表，并设置过期时间
                redisTemplate.opsForList().leftPush(key, JSONObject.toJSONString(newOrderDataVo));
                redisTemplate.expire(key, RedisConstant.DRIVER_ORDER_TEMP_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
            }
        });
    }

    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        // 初始化一个空的订单数据列表
        List<NewOrderDataVo> list = new ArrayList<>();
        // 构建Redis中司机订单临时列表的键
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        // 当Redis列表不为空时，继续从列表的尾部弹出元素
        while (Objects.requireNonNull(redisTemplate.opsForList().size(key)) > 0) {
            // 从Redis列表的尾部弹出一个订单数据的JSON字符串
            String json = (String) redisTemplate.opsForList().rightPop(key);
            // 将JSON字符串解析为NewOrderDataVo对象
            NewOrderDataVo newOrderDataVo = JSONObject.parseObject(json, NewOrderDataVo.class);
            // 将解析后的订单数据对象添加到列表中
            list.add(newOrderDataVo);
        }
        // 返回包含所有订单数据的列表
        return list;
    }

    @Override
    public Boolean clearNewOrderQueueData(Long driverId) {
        return redisTemplate.delete(RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId);
    }
}
