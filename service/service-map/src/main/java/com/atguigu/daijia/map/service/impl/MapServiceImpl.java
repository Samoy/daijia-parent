package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.map.service.MapService;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MapServiceImpl implements MapService {

    @Value("${tencent.map.key}")
    private String key;
    @Resource
    private RestTemplate restTemplate;

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        String url = "https://apis.map.qq.com/ws/direction/v1/driving/?from={from}&to={to}&key={key}";
        //封装传递参数
        Map<String, String> map = new HashMap<>();
        //开始位置
        map.put("from", calculateDrivingLineForm.getStartPointLatitude() + ","
                + calculateDrivingLineForm.getStartPointLongitude());
        //结束位置
        map.put("to", calculateDrivingLineForm.getEndPointLatitude() + ","
                + calculateDrivingLineForm.getEndPointLongitude());
        //key
        map.put("key", key);
        JSONObject result = restTemplate.getForObject(url, JSONObject.class, map);
        //创建vo对象
        DrivingLineVo drivingLineVo = new DrivingLineVo();
        if (result != null) {
            int status = result.getIntValue("status");
            // 失败
            if (status != 0) {
                log.error("地图服务调用失败：{}", result.toJSONString());
                throw new GuiguException(ResultCodeEnum.MAP_FAIL);
            }
            // 获取返回路线信息
            JSONObject route =
                    result.getJSONObject("result").getJSONArray("routes").getJSONObject(0);
            // 预估时间
            drivingLineVo.setDuration(route.getBigDecimal("duration"));
            // 预估距离(米->千米，保留两位小数)
            drivingLineVo.setDistance(route.getBigDecimal("distance")
                    .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP));
            // 路线
            drivingLineVo.setPolyline(route.getJSONArray("polyline"));
        }
        return drivingLineVo;
    }
}
