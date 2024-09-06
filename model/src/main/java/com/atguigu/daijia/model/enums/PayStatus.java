package com.atguigu.daijia.model.enums;

import lombok.Getter;

@Getter
public enum PayStatus {
    /**
     * 开启接单
     */
    PAYED(1, "已支付"),
    /**
     * 关闭接单
     */
    UN_PAYED(0, "未支付"),
    ;

    private final Integer status;
    private final String comment;

    PayStatus(Integer status, String comment) {
        this.status = status;
        this.comment = comment;
    }
}
