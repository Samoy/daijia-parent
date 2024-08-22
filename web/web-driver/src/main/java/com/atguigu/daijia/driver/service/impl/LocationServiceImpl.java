package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.enums.ServiceStatus;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocationServiceImpl implements LocationService {


    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;
    @Resource
    private RedisTemplate<String, String> redisTemplate;


    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        // 根据司机id获取司机个性化设置信息
        Result<DriverSet> driverSetResult = driverInfoFeignClient.getDriverSet(updateDriverLocationForm.getDriverId());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(driverSetResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        DriverSet driverSet = driverSetResult.getData();
        if (ServiceStatus.START_SERVICE.getStatus().equals(driverSet.getServiceStatus())) {
            Result<Boolean> result = locationFeignClient.updateDriverLocation(updateDriverLocationForm);
            if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
                throw new GuiguException(ResultCodeEnum.DATA_ERROR);
            }
            return result.getData();
        } else {
            throw new GuiguException(ResultCodeEnum.NO_START_SERVICE);
        }
    }

    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }
}
