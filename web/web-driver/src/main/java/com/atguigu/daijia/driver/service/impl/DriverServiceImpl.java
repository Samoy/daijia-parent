package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DriverServiceImpl implements DriverService {

    private final DriverInfoFeignClient driverInfoFeignClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public DriverServiceImpl(DriverInfoFeignClient driverInfoFeignClient, RedisTemplate<String, String> redisTemplate) {
        this.driverInfoFeignClient = driverInfoFeignClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String login(String code) {
        Result<Long> result = driverInfoFeignClient.login(code);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        Long driverId = result.getData();
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, driverId.toString(),
                RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        return token;
    }
}
