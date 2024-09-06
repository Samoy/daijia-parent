package com.atguigu.daijia.model.enums;

import lombok.Getter;

@Getter
public enum PayWay {
    WECHAT(1, "微信"),
    ALIPAY(2, "支付宝"),
    ;

    private final Integer code;
    private final String comment;

    PayWay(Integer code, String comment) {
        this.code = code;
        this.comment = comment;
    }
}
