package com.atguigu.daijia.driver.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "service-driver")
public interface DriverInfoFeignClient {

    /**
     * 小程序授权登录
     *
     * @param code 授权码
     * @return 登录成功返回司机id
     */
    @GetMapping("/driver/info/login/{code}")
    Result<Long> login(@PathVariable String code);

    /**
     * 获取司机登录信息
     *
     * @param driverId 司机id
     * @return 登录信息
     */
    @GetMapping("/driver/info/getDriverLoginInfo/{driverId}")
    Result<DriverLoginVo> getDriverLoginInfo(@PathVariable("driverId") Long driverId);

    /**
     * 获取司机认证信息
     *
     * @param driverId 司机id
     * @return 认证信息
     */
    @GetMapping("/driver/info/getDriverAuthInfo/{driverId}")
    Result<DriverAuthInfoVo> getDriverAuthInfo(@PathVariable("driverId") Long driverId);

    /**
     * 更新司机认证信息
     *
     * @param updateDriverAuthInfoForm 更新认证信息表单
     * @return 更新结果
     */
    @PostMapping("/driver/info/updateDriverAuthInfo")
    Result<Boolean> updateDriverAuthInfo(@RequestBody UpdateDriverAuthInfoForm updateDriverAuthInfoForm);

    /**
     * 创建司机人脸模型
     *
     * @param driverFaceModelForm 人脸模型表单
     * @return 创建结果
     */
    @PostMapping("/driver/info/creatDriverFaceModel")
    Result<Boolean> creatDriverFaceModel(@RequestBody DriverFaceModelForm driverFaceModelForm);


    /**
     * 获取司机设置信息
     *
     * @param driverId 司机id
     * @return 司机设置信息
     */
    @GetMapping("/driver/info/getDriverSet/{driverId}")
    Result<DriverSet> getDriverSet(@PathVariable("driverId") Long driverId);

    /**
     * 批量查询司机设置信息
     *
     * @param driverIdList 司机id列表，用英文逗号隔开
     * @return 司机设置信息列表
     */
    @GetMapping("/driver/info/getDriverSetBatch")
    Result<List<DriverSet>> getDriverSetBatch(@RequestParam String driverIdList);

    /**
     * 判断司机当日是否进行过人脸识别
     *
     * @param driverId 司机id
     * @return 是否进行过人脸识别
     */
    @GetMapping("/driver/info/isFaceRecognition/{driverId}")
    Result<Boolean> isFaceRecognition(@PathVariable("driverId") Long driverId);

    /**
     * 验证司机人脸
     *
     * @param driverFaceModelForm 人脸模型表单
     * @return 是否验证成功
     */
    @PostMapping("/driver/info/verifyDriverFace")
    Result<Boolean> verifyDriverFace(@RequestBody DriverFaceModelForm driverFaceModelForm);

    /**
     * 更新接单状态
     *
     * @param driverId 司机id
     * @param status   司机状态
     * @return 是否更新成功
     */
    @GetMapping("/driver/info/updateServiceStatus/{driverId}/{status}")
    Result<Boolean> updateServiceStatus(@PathVariable("driverId") Long driverId, @PathVariable("status") Integer status);
}