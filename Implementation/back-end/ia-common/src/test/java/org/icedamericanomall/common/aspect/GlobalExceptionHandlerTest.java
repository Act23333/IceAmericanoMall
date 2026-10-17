package org.icedamericanomall.common.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
 *
 * <p><b>2026-10-18 批次 ⑤</b>：内部错误体由 {@code {"code","msg"}} 扩为
 * {@link ErrorInfo}（+ {@code category / retryable / traceId}），依据
 * {@code collaboration/09-constraints.md} §三·八·3。同一批新增了「对外 {@code /api/**}
 * 零变化」的守卫（{@link #shouldKeepPublicErrorBodyUntouched()}）—— 两件事必须同时被锁住，
 * 否则「内部加字段」很容易顺手把外部信封也改了。
 */
@DisplayName("GlobalExceptionHandler — 内部裸状态码 / 外部 Result 包装")
class GlobalExceptionHandlerTest {

    private static final String INTERNAL_URI = "/internal/balance/deduct";

    private static final String PUBLIC_URI = "/api/order/1001";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        MDC.remove(TraceIdFilter.TRACE_ID);
    }

    /** 绑定一次模拟请求到当前线程（handler 依赖 RequestContextHolder 取 URI）。 */
    private void bindRequest(String requestUri) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", requestUri)));
    }

    @Test
    @DisplayName("内部接口 + 业务码（HTTP 200）→ 200 + ErrorInfo，绝不包 Result")
    void shouldReturnRawStatus_whenInternalBizExceptionWithOkHttpCode() {
        bindRequest(INTERNAL_URI);

        Object response = handler.handleBizException(new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isInstanceOf(ErrorInfo.class).isNotInstanceOf(Result.class);
        ErrorInfo body = (ErrorInfo) entity.getBody();
        assertThat(body.code()).isEqualTo(ErrorCode.BUSINESS_EXECUTION_EXCEPTION.getCode());
        // 200 + 业务 code → RULE（业务规则拒绝的既有形态）
        assertThat(body.category()).isEqualTo(ErrorCategory.RULE);
        assertThat(body.retryable()).isFalse();
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

    /**
     * 登录失败对外承载（2026-10-18 批次 ④，抗账号枚举）。
     *
     * <p><b>锁死的缺陷（修复前会失败）</b>：{@code PASSWORD_ERROR} 原配 {@code HttpStatus.OK}，登录失败
     * 返回 <b>HTTP 200 + body code</b>；而「账号不存在」走 {@code USER_NOT_FOUND} 返回 <b>404</b>。
     * 攻击者不看文案、只看状态码即可枚举账号。现在三条失败路径在 HTTP 层完全同形：{@code 401 + Result}。
     */
    @Test
    @DisplayName("外部登录失败 → HTTP 401 + Result(3007) + 中性 msg（不得用 200 与 404 形成差异）")
    void shouldReturn401_whenPublicLoginFails() {
        bindRequest("/api/auth/web/login");

        Object response = handler.handleBizException(new BizException(ErrorCode.PASSWORD_ERROR));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(entity.getBody()).isInstanceOf(Result.class);
        Result<?> body = (Result<?>) entity.getBody();
        assertThat(body.getCode()).isEqualTo(3007);
        assertThat(body.getMsg()).isEqualTo("账号或密码不正确");
    }

    // ==================== 批次 ⑤：内部错误体类型化（§三·八·3）====================

    @Test
    @DisplayName("内部错误体 — 键集合恰为 code/msg/category/retryable/traceId（真状态码不变）")
    void shouldExposeTypedErrorBody_whenInternal() throws Exception {
        bindRequest(INTERNAL_URI);
        MDC.put(TraceIdFilter.TRACE_ID, "trace-int-1");

        Object response = handler.handleCommonException(new BadRequestException(ErrorCode.NO_RESOURCE_FOUND));

        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(entity.getBody()));
        Set<String> keys = new TreeSet<>();
        json.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("category", "code", "msg", "retryable", "traceId");
        assertThat(json.get("code").asInt()).isEqualTo(ErrorCode.NO_RESOURCE_FOUND.getCode());
        // ★ 404 → CLIENT：调用方据此「原样透传」，而不是改写成本域 SYSTEM_BUSY
        assertThat(json.get("category").asText()).isEqualTo("CLIENT");
        assertThat(json.get("retryable").asBoolean()).isFalse();
        // traceId 复用网关 X-Trace-Id → TraceIdFilter → MDC 这条现成链路
        assertThat(json.get("traceId").asText()).isEqualTo("trace-int-1");
    }

    @Test
    @DisplayName("内部错误体 — 429 → TRANSIENT + retryable=true（调用方可退避重试）")
    void shouldMarkRateLimitRetryable_whenInternal() {
        bindRequest(INTERNAL_URI);

        ErrorInfo body = (ErrorInfo) ((ResponseEntity<?>) handler.handleBizException(
                new BizException(ErrorCode.FREQUENT_ERROR))).getBody();

        assertThat(body.category()).isEqualTo(ErrorCategory.TRANSIENT);
        assertThat(body.retryable()).isTrue();
    }

    @Test
    @DisplayName("traceId — 取不到时置 null，绝不抛异常、绝不因此 500")
    void shouldReturnNullTraceId_whenMdcAbsent() {
        bindRequest(INTERNAL_URI);
        MDC.remove(TraceIdFilter.TRACE_ID);

        ResponseEntity<?> entity = (ResponseEntity<?>) handler.handleCommonException(
                new BadRequestException(ErrorCode.NO_RESOURCE_FOUND));

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ErrorInfo) entity.getBody()).traceId()).isNull();
    }

    // ==================== 批次 ⑤：/api/** 报文零变化 ====================

    /**
     * ★ 硬约束守卫：外部 {@code /api/**} 的错误响应<b>一个字都不许改</b>。
     *
     * <p>本测试从三个角度同时钉死：
     * <ol>
     *   <li>{@code Result} 的实例字段<b>恰好</b>是 {@code code / msg / data} —— 新增一个
     *       {@code traceId} 字段是最容易发生的「顺手统一」，而它会立刻改变所有对外报文；</li>
     *   <li>外部错误体的 JSON 键集合与改动前<b>逐字一致</b>（{@code code,msg,data}），
     *       且<b>不含</b> {@code category / retryable / traceId}；</li>
     *   <li>同一个 {@code ErrorCode} 在外部支上序列化出的 JSON 与改动前完全相同的字面量。</li>
     * </ol>
     * <p>注：{@code data} 为 {@code null} 但键仍存在，这是<b>改动前既有形态</b>
     * （部分服务全局 {@code non_null} 会省略它，那是各服务既有配置，本批未触碰）。
     */
    @Test
    @DisplayName("外部接口 — Result 字段仍恰为 code/msg/data，且不含 category/retryable/traceId")
    void shouldKeepPublicErrorBodyUntouched() throws Exception {
        bindRequest(PUBLIC_URI);
        MDC.put(TraceIdFilter.TRACE_ID, "trace-public-1");

        // ① Result 类的实例字段集合（结构性守卫：任何人给 Result 加字段都会红）
        List<String> resultFields = new ArrayList<>();
        for (Field field : Result.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                resultFields.add(field.getName());
            }
        }
        resultFields.sort(String::compareTo);
        assertThat(resultFields).containsExactly("code", "data", "msg");

        // ② 外部错误响应 JSON 与改动前逐字一致（含 null 的 data，且无新增字段）
        Object response = handler.handleBizException(new BizException(ErrorCode.STOCK_INSUFFICIENT));
        String json = new ObjectMapper().writeValueAsString(((ResponseEntity<?>) response).getBody());
        assertThat(json).isEqualTo("{\"code\":3012,\"msg\":\"库存不足\",\"data\":null}");
    }
}
