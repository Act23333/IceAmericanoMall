package org.icedamericanomall.common.aspect;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理回归测试（ADR-004 修正，P1）。
 *
 * <p>核心不变量：{@code /internal/**} 的异常必须让 Feign 调用方拿到
 * {@code ErrorCode} 的<b>真实 HTTP 状态码</b>且<b>不带 Result 信封</b>；
 * 外部 {@code /api/**} 的异常仍包装 {@code Result}（前端体验不变）。
 *
 * <p>此前实现在 {@code @ExceptionHandler} 里 {@code throw e;}，Spring 不会二次解析，
 * 容器对一切内部异常兜底成 500 —— 本测试锁死该缺陷不复发。
 */
@DisplayName("GlobalExceptionHandler — 内部裸状态码 / 外部 Result 包装")
class GlobalExceptionHandlerTest {

    private static final String INTERNAL_URI = "/internal/balance/deduct";

    private static final String PUBLIC_URI = "/api/order/1001";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 绑定一次模拟请求到当前线程（handler 依赖 RequestContextHolder 取 URI）。 */
    private void bindRequest(String requestUri) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", requestUri)));
    }

    @Test
    @DisplayName("内部接口 + 业务码（HTTP 200）→ 200 + 极简错误体，绝不包 Result")
    void shouldReturnRawStatus_whenInternalBizExceptionWithOkHttpCode() {
        bindRequest(INTERNAL_URI);

        Object response = handler.handleBizException(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isInstanceOf(Map.class).isNotInstanceOf(Result.class);
        Map<?, ?> body = (Map<?, ?>) entity.getBody();
        assertThat(body.get("code")).isEqualTo(ErrorCode.BUSINESS_EXECUTION_EXCEPTION.getCode());
    }

    @Test
    @DisplayName("内部接口 + 限流码 1014 → 真实 HTTP 429（回归：此前被容器兜底成 500）")
    void shouldReturnRealHttpStatus_whenInternalRateLimited() {
        bindRequest(INTERNAL_URI);

        Object response = handler.handleBizException(new BizException(ErrorCode.FREQUENT_ERROR));

        assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("内部接口 + 非 200 业务码 → 状态码与 ErrorCode 一致且不带 Result")
    void shouldReturnErrorCodeStatus_whenInternalNotFound() {
        bindRequest(INTERNAL_URI);

        Object response = handler.handleCommonException(new BadRequestException(ErrorCode.USER_NOT_FOUND));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getHttpStatus());
        assertThat(entity.getBody()).isNotInstanceOf(Result.class);
    }

    @Test
    @DisplayName("外部接口 + 业务异常 → 仍包装 Result（前端统一体验不变）")
    void shouldWrapResult_whenPublicBizException() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleBizException(new BizException(ErrorCode.STOCK_INSUFFICIENT));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT.getHttpStatus());
        assertThat(entity.getBody()).isInstanceOf(Result.class);
        assertThat(((Result<?>) entity.getBody()).getCode()).isEqualTo(ErrorCode.STOCK_INSUFFICIENT.getCode());
    }

    @Test
    @DisplayName("@Validated 方法参数校验失败 → PARAM_ERROR(400) 而非 INTERNAL_ERROR(500)（回归：marketing /internal/points 实测缺口）")
    void shouldMapConstraintViolation_toParamError() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleConstraintViolationException(
                new ConstraintViolationException("amount: 充值金额必须大于0", Set.of()));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody()).isInstanceOf(Result.class);
        Result<?> body = (Result<?>) entity.getBody();
        assertThat(body.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(body.getMsg()).contains("充值金额必须大于0");
    }

    @Test
    @DisplayName("@Validated 参数校验失败 → 违反明细按 propertyPath: message 拼接（多条以；分隔）")
    void shouldJoinViolationDetails_whenMultipleViolations() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleConstraintViolationException(
                new ConstraintViolationException(Set.of(
                        violation("recharge.amount", "充值金额必须大于0"),
                        violation("recharge.channel", "充值渠道不能为空"))));

        Result<?> body = (Result<?>) ((ResponseEntity<?>) response).getBody();
        assertThat(body.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(body.getMsg())
                .contains("recharge.amount: 充值金额必须大于0")
                .contains("recharge.channel: 充值渠道不能为空");
    }

    /** 构造一条约束违反（避免依赖 hibernate-validator 实现，用 Mockito 造接口对象）。 */
    private ConstraintViolation<?> violation(String path, String message) {
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn(path);
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    // ==================== 缺参 / 类型不符 / 请求体不可读（2026-10-15 补出口）====================

    @Test
    @DisplayName("缺必填参数 → 400 PARAM_ERROR（回归：此前落兜底 500；ADR-055 实测缺口）")
    void shouldReturn400_whenMissingServletRequestParameter() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("ids", "Long"));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Result<?>) entity.getBody()).getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("参数类型不匹配 → 400 PARAM_ERROR（回归：此前落兜底 500）")
    void shouldReturn400_whenMethodArgumentTypeMismatch() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleMethodArgumentTypeMismatchException(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, null));

        assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("请求体不可读 → 400 PARAM_ERROR（回归：此前落兜底 500）")
    void shouldReturn400_whenHttpMessageNotReadable() {
        bindRequest(PUBLIC_URI);

        Object response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("bad json"));

        assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
