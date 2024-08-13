package com.atguigu.daijia.model.enums;

import lombok.Getter;

/**
 * ServiceStatus
 *
 * @author Samoy
 * @date 2024/8/13
 */
@Getter
public enum ServiceStatus {
    /**
     * 开启接单
     */
    START_SERVICE(1, "开启接单"),
    /**
     * 关闭接单
     */
    STOP_SERVICE(0, "关闭接单"),
    ;

    private final Integer status;
    private final String comment;

    ServiceStatus(Integer status, String comment) {
        this.status = status;
        this.comment = comment;
    }
}
