package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CustomerServiceImpl implements CustomerService {

    @Resource
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public String login(String code) {
        Result<Long> result = customerInfoFeignClient.login(code);
        if (!ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        Long id = result.getData();
        if (id == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, id.toString(), RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public CustomerLoginVo getCustomerLoginInfo(String token) {
        String customerId = redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);
        if (!StringUtils.hasText(customerId)) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        Result<CustomerLoginVo> customerLoginInfo = customerInfoFeignClient.getCustomerLoginInfo(Long.parseLong(customerId));
        if (!ResultCodeEnum.SUCCESS.getCode().equals(customerLoginInfo.getCode())) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        CustomerLoginVo data = customerLoginInfo.getData();
        if (data == null) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        return data;
    }
}
