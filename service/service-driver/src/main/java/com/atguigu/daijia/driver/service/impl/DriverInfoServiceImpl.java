package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.FaceUtil;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.model.FaceLibrary;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.mapper.DriverAccountMapper;
import com.atguigu.daijia.driver.mapper.DriverInfoMapper;
import com.atguigu.daijia.driver.mapper.DriverLoginLogMapper;
import com.atguigu.daijia.driver.mapper.DriverSetMapper;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.DriverAccount;
import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverLoginLog;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.enums.AuthStatus;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
@Service
public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {

    @Resource
    private WxMaService wxMaService;
    @Resource
    private DriverInfoMapper driverInfoMapper;
    @Resource
    private DriverSetMapper driverSetMapper;
    @Resource
    private DriverAccountMapper driverAccountMapper;
    @Resource
    private DriverLoginLogMapper driverLoginLogMapper;
    @Resource
    private CosService cosService;

    @Override
    public Long login(String code) {
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            String openid = sessionInfo.getOpenid();
            LambdaQueryWrapper<DriverInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(DriverInfo::getWxOpenId, openid);
            DriverInfo driverInfo = driverInfoMapper.selectOne(lambdaQueryWrapper);
            if (driverInfo == null) {
                driverInfo = new DriverInfo();
                driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
                driverInfo.setAvatarUrl("https://picsum.photos/200");
                driverInfo.setWxOpenId(openid);
                driverInfoMapper.insert(driverInfo);
                // 初始化司机设置
                DriverSet driverSet = new DriverSet();
                driverSet.setDriverId(driverInfo.getId());
                driverSet.setOrderDistance(new BigDecimal(0));
                driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));
                driverSet.setIsAutoAccept(0); // 不开启自动接单
                driverSetMapper.insert(driverSet);

                // 初始化司机账户信息
                DriverAccount driverAccount = new DriverAccount();
                driverAccount.setDriverId(driverInfo.getId());
                driverAccountMapper.insert(driverAccount);
            }
            // 记录登录日志
            DriverLoginLog driverLoginLog = new DriverLoginLog();
            driverLoginLog.setDriverId(driverInfo.getId());
            driverLoginLog.setMsg("小程序登录");
            driverLoginLogMapper.insert(driverLoginLog);
            return driverInfo.getId();
        } catch (WxErrorException e) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
    }

    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(driverInfo, driverLoginVo);
        // 是否建档人脸识别
        String faceModelId = driverInfo.getFaceModelId();
        boolean isArchiveFace = StringUtils.hasText(faceModelId);
        driverLoginVo.setIsArchiveFace(isArchiveFace);
        return driverLoginVo;
    }

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo, driverAuthInfoVo);
        String idcardBackUrl = driverAuthInfoVo.getIdcardBackUrl();
        String idcardFrontUrl = driverAuthInfoVo.getIdcardFrontUrl();
        String idcardHandUrl = driverAuthInfoVo.getIdcardHandUrl();
        String driverLicenseFrontUrl = driverAuthInfoVo.getDriverLicenseFrontUrl();
        String driverLicenseBackUrl = driverAuthInfoVo.getDriverLicenseBackUrl();
        String driverLicenseHandUrl = driverAuthInfoVo.getDriverLicenseHandUrl();
        if (StringUtils.hasText(idcardBackUrl)) {
            driverAuthInfoVo.setIdcardBackShowUrl(cosService.getImageUrl(idcardBackUrl));
        }
        if (StringUtils.hasText(idcardFrontUrl)) {
            driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(idcardFrontUrl));
        }
        if (StringUtils.hasText(idcardHandUrl)) {
            driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(idcardHandUrl));
        }
        if (StringUtils.hasText(driverLicenseFrontUrl)) {
            driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverLicenseFrontUrl));
        }
        if (StringUtils.hasText(driverLicenseBackUrl)) {
            driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverLicenseBackUrl));
        }
        if (StringUtils.hasText(driverLicenseHandUrl)) {
            driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverLicenseHandUrl));
        }
        return driverAuthInfoVo;
    }

    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Long driverId = updateDriverAuthInfoForm.getDriverId();
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(driverId);
        BeanUtils.copyProperties(updateDriverAuthInfoForm, driverInfo);
        // 更新司机认证信息，状态应该是审核中
        driverInfo.setAuthStatus(AuthStatus.AUTH_AUDITING.getStatus());
        return this.updateById(driverInfo);
    }

    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        //根据司机id获取司机信息
        DriverInfo driverInfo =
                driverInfoMapper.selectById(driverFaceModelForm.getDriverId());
        FaceLibrary face = FaceUtil.addFaceLibrary(driverFaceModelForm.getImageBase64(), driverInfo.getName());
        if (face != null) {
            driverInfo.setFaceModelId(face.getImage_id());
            return this.updateById(driverInfo);
        }
        return false;
    }
}