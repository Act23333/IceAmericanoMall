package org.icedamericanomall.constants;

import lombok.Getter;

@Getter
public enum FlashOrderStatusEnum {
    PENDING(0, "pending"),
    CREATED(1, "created"),
    FAILED(2, "failed");

    private final int code;
    private final String desc;

    FlashOrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
