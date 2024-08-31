package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.driver.service.CiService;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import org.springframework.stereotype.Service;

// FIXME: 不想花钱开通审核服务，默认全部通过
@Service
public class CiServiceImpl implements CiService {


    @Override
    public Boolean imageAudit(String imageUrl) {
        return true;
    }

    @Override
    public TextAuditingVo textAuditing(String content) {
        TextAuditingVo textAuditingVo = new TextAuditingVo();
        textAuditingVo.setResult("0");
        return textAuditingVo;
    }
}
