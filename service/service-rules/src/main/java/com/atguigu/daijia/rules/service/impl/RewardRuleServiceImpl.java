package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.RewardRuleRequest;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponse;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.rules.service.RewardRuleService;
import com.atguigu.daijia.rules.utils.DroolsHelper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RewardRuleServiceImpl implements RewardRuleService {

    private static final String RULE_NAME = "rules/RewardRule.drl";


    @Override
    public RewardRuleResponseVo calculateOrderRewardFee(RewardRuleRequestForm rewardRuleRequestForm) {
        RewardRuleRequest request = new RewardRuleRequest();
        request.setOrderNum(rewardRuleRequestForm.getOrderNum());
        KieSession kieSession = DroolsHelper.loadForRule(RULE_NAME);
        RewardRuleResponse response = new RewardRuleResponse();
        kieSession.setGlobal("rewardRuleResponse", response);
        kieSession.insert(request);
        kieSession.fireAllRules();
        kieSession.dispose();
        RewardRuleResponseVo result = new RewardRuleResponseVo();
        result.setRewardAmount(response.getRewardAmount());
        return result;
    }
}
