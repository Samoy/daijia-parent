package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.repository.OrderServiceLocationRepository;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocationServiceImpl implements LocationService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

    @Resource
    private OrderServiceLocationRepository orderServiceLocationRepository;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;

    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(),
                updateDriverLocationForm.getLatitude().doubleValue());
        Long added = redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point,
                updateDriverLocationForm.getDriverId().toString());
        return added != null && added > 0;
    }

    @Override
    public Boolean removeDriverLocation(Long driverId) {
        Long removed = redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        // 返回是否删除成功
        return removed != null && removed > 0;
    }

    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        Circle circle = new Circle(new Point(searchNearByDriverForm.getLongitude().doubleValue(),
                searchNearByDriverForm.getLatitude().doubleValue()),
                new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeCoordinates().includeDistance().sortAscending();
        GeoResults<RedisGeoCommands.GeoLocation<String>> radius = redisTemplate.opsForGeo()
                .radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);
        if (radius == null) {
            return Collections.emptyList();
        }

        List<String> driverIds = radius.getContent().stream()
                .map(geoResult -> geoResult.getContent().getName())
                .toList();

        // 批量查询司机信息
        Result<List<DriverSet>> driverSetListResult = driverInfoFeignClient.getDriverSetBatch(StringUtils.join(driverIds, ","));
        if (!Objects.equals(driverSetListResult.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            throw new GuiguException(ResultCodeEnum.FEIGN_FAIL);
        }

        // 存储司机信息
        Map<Long, DriverSet> driverSetMap = driverSetListResult.getData().stream().collect(
                Collectors.toMap(DriverSet::getDriverId, Function.identity())
        );

        // 处理司机信息
        List<NearByDriverVo> list = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : radius.getContent()) {
            Long driverId = Long.parseLong(item.getContent().getName());
            DriverSet driverSet = driverSetMap.get(driverId);
            if (driverSet == null) {
                continue; // 如果没有找到司机信息，则跳过
            }

            BigDecimal orderDistance = driverSet.getOrderDistance();
            if (orderDistance.doubleValue() != 0
                    && orderDistance.subtract(searchNearByDriverForm.getMileageDistance()).doubleValue() < 0) {
                continue;
            }

            BigDecimal currentDistance =
                    BigDecimal.valueOf(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);

            BigDecimal acceptDistance = driverSet.getAcceptDistance();
            if (acceptDistance.doubleValue() != 0
                    && acceptDistance.subtract(currentDistance).doubleValue() < 0) {
                continue;
            }

            NearByDriverVo nearByDriverVo = new NearByDriverVo();
            nearByDriverVo.setDriverId(driverId);
            nearByDriverVo.setDistance(currentDistance);
            list.add(nearByDriverVo);
        }
        return list;
    }

    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        redisTemplate.opsForValue().set(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId(),
                JSONObject.toJSONString(orderLocationVo));
        return true;
    }

    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        return JSONObject.parseObject(redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + orderId),
                OrderLocationVo.class);
    }

    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> list = orderLocationServiceFormList.stream().map(orderServiceLocationForm -> {
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(orderServiceLocationForm, orderServiceLocation);
            orderServiceLocation.setId(ObjectId.get().toString());
            orderServiceLocation.setCreateTime(new Date());
            return orderServiceLocation;
        }).toList();
        return !CollectionUtils.isEmpty(orderServiceLocationRepository.saveAll(list));
    }

    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);
        OrderServiceLocation orderServiceLocation =
                mongoTemplate.findOne(query, OrderServiceLocation.class);
        OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
        if (!ObjectUtils.isEmpty(orderServiceLocation)) {
            BeanUtils.copyProperties(orderServiceLocation, orderServiceLastLocationVo);
        }
        return orderServiceLastLocationVo;
    }

    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {
        // 1.根据订单id获取订单服务位置信息，根据创建时间升序排列
        List<OrderServiceLocation> list = orderServiceLocationRepository.findAllByOrderIdOrderByCreateTimeAsc(orderId);
        // 2. 距离相加
        BigDecimal totalDistance = BigDecimal.ZERO;
        if (!CollectionUtils.isEmpty(list)) {
            for (int i = 0; i < list.size() - 1; i++) {
                OrderServiceLocation currentLocation = list.get(i);
                OrderServiceLocation nextLocation = list.get(i + 1);
                double distance = LocationUtil.getDistance(
                        currentLocation.getLatitude().doubleValue(),
                        currentLocation.getLongitude().doubleValue(),
                        nextLocation.getLatitude().doubleValue(),
                        nextLocation.getLongitude().doubleValue()
                );
                totalDistance = totalDistance.add(BigDecimal.valueOf(distance));
            }
        }

        // FIXME: 为了测试，不好测试实际代驾距离，模拟数据  实际距离=预估距离+5公里
        if(totalDistance.equals(BigDecimal.ZERO)) {
            return orderInfoFeignClient.getOrderInfo(orderId).getData().getExpectDistance().add(new BigDecimal("5"));
        }
        // 3. 返回总距离
        return totalDistance;
    }

}
