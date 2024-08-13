package com.atguigu.daijia.model.enums;

import lombok.Getter;

/**
 * AuthStatus
 *
 * @author Samoy
 * @date 2024/8/8
 */
@Getter
public enum AuthStatus {

    WAITING_AUTH(0, "未认证"),
    AUTH_AUDITING(1, "审核中"),
    AUTH_SUCCESS(2, "认证通过"),
    AUTH_FAIL(-1, "认证不通过"),
    ;

    private final Integer status;
    private final String comment;

    AuthStatus(Integer status, String comment) {
        this.status = status;
        this.comment = comment;
    }
}
