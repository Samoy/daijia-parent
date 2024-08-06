package com.atguigu.daijia.driver.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * WxConfigOperator
 *
 * @author Samoy
 * @date 2024/8/5
 */
@Component
public class WxConfigOperator {
    @Resource
    private WxConfigProperties wxConfigProperties;

    @Bean
    public WxMaService wxService() {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(wxConfigProperties.getAppId());
        config.setSecret(wxConfigProperties.getSecret());
        WxMaService wxMaService = new WxMaServiceImpl();
        wxMaService.setWxMaConfig(config);
        return wxMaService;
    }
}
