package org.noLazy.common.domain;

/**
 * @ClassName: Result
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 15:12
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.domain
 */

import lombok.Data;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.CommonException;

@Data
public class Result<T> {
    private int code;       // 状态码，通常 200 表示成功
    private String msg;      // 提示信息
    private T data;          // 返回的数据

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 成功静态方法
    public static Result<Void> ok() {
        return ok(null);
    };
    public static Result<Void> ok(String msg) {
        return new Result<>(200, msg,null);
    }
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }
    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    // 失败静态方法
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> error(CommonException e) {
        ErrorCode errorCode = e.getErrorCode();
        String displayMessage = e.getDisplayMessage();
        int code = errorCode.getCode();
        return new Result<>(code, displayMessage, null);
    }

    public boolean success(){
        return code == 200;
    }

}
