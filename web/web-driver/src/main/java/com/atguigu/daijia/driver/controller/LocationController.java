package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.login.GuiguLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "位置API接口管理")
@RestController
@RequestMapping(value = "/location")
public class LocationController {

    @Resource
    private LocationService locationService;

    @Operation(summary = "开启接单服务：更新司机经纬度位置")
    @GuiguLogin
    @PostMapping(value = "/updateDriverLocation")
    public Result<Boolean> updateDriverLocation(@RequestBody UpdateDriverLocationForm updateDriverLocationForm) {
        Long driverId = AuthContextHolder.getUserId();
        updateDriverLocationForm.setDriverId(driverId);
        return Result.ok(locationService.updateDriverLocation(updateDriverLocationForm));
    }


    //司机关闭接单，删除司机位置信息
    @Operation(summary = "关闭接单服务：删除司机经纬度位置")
    @DeleteMapping("/removeDriverLocation/{driverId}")
    public Result<Boolean> removeDriverLocation(@PathVariable Long driverId) {
        return Result.ok(locationService.removeDriverLocation(driverId));
    }

}

