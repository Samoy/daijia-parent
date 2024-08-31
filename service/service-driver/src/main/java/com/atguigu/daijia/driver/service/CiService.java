package com.atguigu.daijia.driver.service;

import com.atguigu.daijia.model.vo.order.TextAuditingVo;

public interface CiService {

    // 图片审核
    Boolean imageAudit(String imageUrl);

    /**
     * 文本审核
     *
     * @param content 文本内容
     * @return 审核结果
     */
    TextAuditingVo textAuditing(String content);
}
