package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequest;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponse;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.rules.service.ProfitsharingRuleService;
import com.atguigu.daijia.rules.utils.DroolsHelper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProfitsharingRuleServiceImpl implements ProfitsharingRuleService {
    private static final String RULE_FILE_NAME = "rules/ProfitsharingRule.drl";


    @Override
    public ProfitsharingRuleResponseVo calculateOrderProfitsharingFee(ProfitsharingRuleRequestForm profitsharingRuleRequestForm) {
        ProfitsharingRuleRequest request = new ProfitsharingRuleRequest();
        request.setOrderAmount(profitsharingRuleRequestForm.getOrderAmount());
        request.setOrderNum(profitsharingRuleRequestForm.getOrderNum());
        KieSession kieSession = DroolsHelper.loadForRule(RULE_FILE_NAME);
        ProfitsharingRuleResponse response = new ProfitsharingRuleResponse();
        kieSession.setGlobal("profitsharingRuleResponse", response);
        kieSession.insert(request);
        kieSession.fireAllRules();
        kieSession.dispose();

        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = new ProfitsharingRuleResponseVo();
        BeanUtils.copyProperties(response, profitsharingRuleResponseVo);

        return profitsharingRuleResponseVo;
    }
}
