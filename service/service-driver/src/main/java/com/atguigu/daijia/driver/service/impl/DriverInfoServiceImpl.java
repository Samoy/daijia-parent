package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.enums.AuthStatus;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.*;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
    @Resource
    private TencentCloudProperties tencentCloudProperties;
    @Resource
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;

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
        try {
            IaiClient client = createIaiClient();
            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();
            //设置相关值
            req.setGroupId(tencentCloudProperties.getPersionGroupId());
            //基本信息
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setQualityControl(4L);
            req.setUniquePersonControl(4L);
            req.setPersonName(driverInfo.getName());
            req.setImage(driverFaceModelForm.getImageBase64());

            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);
            // 输出json格式的字符串回包
            System.out.println(AbstractModel.toJsonString(resp));
            String faceId = resp.getFaceId();
            if (StringUtils.hasText(faceId)) {
                driverInfo.setFaceModelId(faceId);
                driverInfoMapper.updateById(driverInfo);
            }
        } catch (TencentCloudSDKException e) {
            log.error("创建人脸模型失败", e);
            return false;
        }
        return true;
    }

    @Override
    public DriverSet getDriverSet(Long driverId) {
        return driverSetMapper.selectOne(new LambdaQueryWrapper<DriverSet>().eq(DriverSet::getDriverId, driverId));
    }

    @Override
    public List<DriverSet> getDriverSetBatch(String driverIdList) {
        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        List<Long> driverIds = Arrays.stream(driverIdList.split(",")).map(Long::parseLong).toList();
        wrapper.in(DriverSet::getDriverId, driverIds);
        return driverSetMapper.selectList(wrapper);
    }

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        //根据司机id + 当日日期进行查询
        LambdaQueryWrapper<DriverFaceRecognition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverFaceRecognition::getDriverId, driverId);
        // 年-月-日 格式
        wrapper.eq(DriverFaceRecognition::getFaceDate, new DateTime().toString("yyyy-MM-dd"));
        //调用mapper方法
        Long count = driverFaceRecognitionMapper.selectCount(wrapper);

        return count > 0;
    }

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        // 1. 照片比对
        boolean compared = this.compareFace(driverFaceModelForm);
        // 2. 活体检测
        if (compared) {
            boolean detected = this.detectLiveFace(driverFaceModelForm.getImageBase64());
            if (detected) {
                // 3. 入库
                DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                driverFaceRecognition.setFaceDate(new Date());
                int inserted = driverFaceRecognitionMapper.insert(driverFaceRecognition);
                return inserted > 0;
            }
        }
        return false;
    }

    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {
        LambdaQueryWrapper<DriverSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = new DriverSet();
        driverSet.setServiceStatus(status);
        return driverSetMapper.update(driverSet, wrapper) > 0;
    }

    @Override
    public DriverInfoVo getDriverInfoOrder(Long driverId) {
        //司机id获取基本信息
        DriverInfo driverInfo = driverInfoMapper.selectById(driverId);

        //封装DriverInfoVo
        DriverInfoVo driverInfoVo = new DriverInfoVo();
        BeanUtils.copyProperties(driverInfo, driverInfoVo);

        //计算驾龄
        //获取当前年
        int currentYear = new DateTime().getYear();
        //获取驾驶证初次领证日期
        int firstYear = new DateTime(driverInfo.getDriverLicenseIssueDate()).getYear();
        int driverLicenseAge = currentYear - firstYear;
        driverInfoVo.setDriverLicenseAge(driverLicenseAge);

        return driverInfoVo;
    }

    @Override
    public String getDriverOpenId(Long driverId) {
        LambdaQueryWrapper<DriverInfo> wrapper = new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverId)
                .select(DriverInfo::getWxOpenId);
        DriverInfo driverInfo = this.getOne(wrapper);
        return driverInfo.getWxOpenId();
    }

    private @NotNull IaiClient createIaiClient() {
        Credential cred = new Credential(tencentCloudProperties.getSecretId(),
                tencentCloudProperties.getSecretKey());
        // 实例化一个http选项，可选的，没有特殊需求可以跳过
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("iai.tencentcloudapi.com");
        // 实例化一个client选项，可选的，没有特殊需求可以跳过
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 实例化要请求产品的client对象,clientProfile是可选的
        return new IaiClient(cred, tencentCloudProperties.getRegion(),
                clientProfile);
    }

    private boolean compareFace(DriverFaceModelForm driverFaceModelForm) {
        try {
            IaiClient client = createIaiClient();
            // 实例化一个请求对象,每个接口都会对应一个request对象
            VerifyFaceRequest req = new VerifyFaceRequest();
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(String.valueOf(driverFaceModelForm.getDriverId()));
            // 返回的resp是一个CompareFaceResponse的实例，与请求对象对应
            VerifyFaceResponse resp = client.VerifyFace(req);
            return resp.getIsMatch();
        } catch (TencentCloudSDKException e) {
            log.error("人脸比对失败", e);
        }
        return false;
    }

    private boolean detectLiveFace(String imageBase64) {
        try {
            IaiClient client = createIaiClient();
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            req.setImage(imageBase64);
            // 返回的resp是一个DetectLiveFaceResponse的实例，与请求对象对应
            DetectLiveFaceResponse resp = client.DetectLiveFace(req);
            if (resp.getIsLiveness()) {
                return true;
            }
        } catch (TencentCloudSDKException e) {
            log.error("活体检测失败", e);
        }
        return false;
    }
}