package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.enums.AuthStatus;
import com.atguigu.daijia.model.enums.ServiceStatus;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DriverServiceImpl implements DriverService {

    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private NewOrderFeignClient newOrderFeignClient;


    @Override
    public String login(String code) {
        Result<Long> result = driverInfoFeignClient.login(code);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        Long driverId = result.getData();
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, driverId + "",
                RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        Result<DriverLoginVo> result = driverInfoFeignClient.getDriverLoginInfo(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        Result<DriverAuthInfoVo> result = driverInfoFeignClient.getDriverAuthInfo(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Result<Boolean> result = driverInfoFeignClient.updateDriverAuthInfo(updateDriverAuthInfoForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> result = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        Result<Boolean> result = driverInfoFeignClient.isFaceRecognition(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> result = driverInfoFeignClient.verifyDriverFace(driverFaceModelForm);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    @Override
    public Boolean startService(Long driverId) {
        // 1. 判断是否完成认证
        Result<DriverLoginVo> driverAuthInfoResult = driverInfoFeignClient.getDriverLoginInfo(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(driverAuthInfoResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        if (!AuthStatus.AUTH_SUCCESS.getStatus().equals(driverAuthInfoResult.getData().getAuthStatus())) {
            throw new GuiguException(ResultCodeEnum.AUTH_ERROR);
        }
        // 2. 判断当日是否开始人脸识别
        Result<Boolean> faceRecognitionResult = driverInfoFeignClient.isFaceRecognition(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(faceRecognitionResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        if (!faceRecognitionResult.getData()) {
            throw new GuiguException(ResultCodeEnum.FACE_ERROR);
        }
        // 3. // 更新服务状态：开始接单
        Result<Boolean> updateServiceStatusResult = driverInfoFeignClient.updateServiceStatus(driverId, ServiceStatus.START_SERVICE.getStatus());
        if (!ResultCodeEnum.SUCCESS.getCode().equals(updateServiceStatusResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 4. 删除redis 中的司机位置信息
        Result<Boolean> removeDriverLocationResult = locationFeignClient.removeDriverLocation(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(removeDriverLocationResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        // 5. 清空司机临时队列数据
        Result<Boolean> clearNewOrderQueueDataResult = newOrderFeignClient.clearNewOrderQueueData(driverId);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(clearNewOrderQueueDataResult.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return true;
    }

    @Override
    public Boolean stopService(Long driverId) {
        // 更新服务状态：停止接单
        driverInfoFeignClient.updateServiceStatus(driverId, ServiceStatus.STOP_SERVICE.getStatus());

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //清空司机临时队列
        newOrderFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }
}
