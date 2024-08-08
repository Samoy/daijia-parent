package com.atguigu.daijia.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.Setter;

/**
 * AuthStatus
 *
 * @author Samoy
 * @date 2024/8/8
 */

public enum AuthStatus {

    WAITING_AUTH(0, "未认证"),
    AUTH_AUDITING(1, "审核中"),
    AUTH_SUCCESS(2, "认证通过"),
    AUTH_FAIL(-1, "认证不通过"),
    ;

    @EnumValue
    @Getter
    @Setter
    private Integer status;
    @Getter
    @Setter
    private String comment;

    AuthStatus(Integer status, String comment) {
        this.status = status;
        this.comment = comment;
    }
}
