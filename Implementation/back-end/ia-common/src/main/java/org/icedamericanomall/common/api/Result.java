package org.icedamericanomall.common.api;

/**
 * @ClassName: Result
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 15:12
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.common.api
 */

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.CommonException;

/**
 * 统一响应体：{@code code} / {@code msg} / {@code data}。
 *
 * <p>成功码 {@value #SUCCESS_CODE}；{@code ok(...)} 与 {@code success(...)} 两族
 * 静态工厂<b>完全等价</b>（后者内部委托前者）—— 项目文档（AGENTS.md / 05-API-Specification）
 * 以 {@code success} 命名契约，而存量控制器大量使用 {@code ok}，两族并存以避免破坏性改名。
 *
 * <p>约定：失败一律用 {@code error(...)} 或直接抛 {@code CommonException}
 * （由 {@code GlobalExceptionHandler} 转换），禁止把 {@code code != 200} 当作业务分支的返回值。
 */
@Data
public class Result<T> {

    /** 成功业务码（与 HTTP 200 对齐）。 */
    public static final int SUCCESS_CODE = 200;

    /** 未指定提示信息时的失败码（保留历史语义：非业务码的通用失败）。 */
    private static final int GENERIC_ERROR_CODE = 500;

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
        return new Result<>(SUCCESS_CODE, msg,null);
    }
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }
    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(SUCCESS_CODE, msg, data);
    }

    // 成功静态方法（AGENTS.md 契约命名；与 ok(...) 等价，内部委托保持单一实现）
    //
    // 说明：契约里的 Result.success(data) 已提供；无参形式<b>不存在</b> —— 本类的实例方法
    // boolean success()（判定是否成功）占用了该签名，静态无参工厂与之冲突。
    // 无数据场景请用 Result.ok() 或 Result.success((Object) null)。

    /**
     * 成功响应（自定义提示、无数据）。
     *
     * @param msg 提示信息
     * @return {@code code = 200}、{@code data = null}
     */
    public static Result<Void> success(String msg) {
        return ok(msg);
    }

    /**
     * 成功响应（携带数据）。
     *
     * @param data 业务数据，可为 {@code null}
     * @return {@code code = 200}、{@code msg = "success"}
     */
    public static <T> Result<T> success(T data) {
        return ok(data);
    }

    /**
     * 成功响应（自定义提示 + 携带数据）。
     *
     * @param msg  提示信息
     * @param data 业务数据，可为 {@code null}
     * @return {@code code = 200}
     */
    public static <T> Result<T> success(String msg, T data) {
        return ok(msg, data);
    }

    // 失败静态方法
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(GENERIC_ERROR_CODE, msg, null);
    }

    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
    @SuppressWarnings("unchecked")
    public static <T> Result<T> error(CommonException e) {
        ErrorCode errorCode = e.getErrorCode();
        String displayMessage = e.getDisplayMessage();
        int code = errorCode.getCode();
        T payload = (T) e.getData();
        return new Result<>(code, displayMessage, payload);
    }
    public boolean success(){
        return code == SUCCESS_CODE;
    }

}
