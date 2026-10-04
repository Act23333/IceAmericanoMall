package org.icedamericanomall.common.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code @OperationLog} 切面测试 —— 锁定审计日志的「一行 JSON」契约（ADR-032 管道一）。
 *
 * <p>不变量：专用 logger {@code AUDIT}；成功/失败各恰好一行 JSON；
 * {@code method} 为「类名#方法名」；{@code user} 取 UserContext，缺失记 {@code anonymous}；
 * 失败只记异常类名（不记 message）；参数值默认不落日志（PII 安全），开启后仅 id 类摘要且截断 ≤200。
 */
@DisplayName("OperationLogAspect — 管理操作审计单行 JSON")
class OperationLogAspectTest {

    private static final String AUDIT_LOGGER_NAME = "AUDIT";
    private static final Long ADMIN_USER_ID = 1001L;

    private final OperationLogAspect aspect = new OperationLogAspect();

    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void setUp() {
        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn((Class) RoleController.class);
        when(signature.getName()).thenReturn("createRole");

        auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(auditAppender);
        auditAppender.stop();
        UserContext.removeUser();
    }

    @Test
    @DisplayName("成功路径 — 恰好一行 JSON，含 module/action/method/user/success/costMs，默认无 params")
    void shouldEmitSingleSuccessJsonLine_whenMethodReturnsNormally() throws Throwable {
        UserContext.setUser(adminUser());
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, operationLog("RBAC", "CREATE_ROLE", false));

        assertThat(result).isEqualTo("ok");
        String line = onlyAuditLine();
        assertThat(line).startsWith("{").endsWith("}");
        assertThat(line).contains("\"module\":\"RBAC\"");
        assertThat(line).contains("\"action\":\"CREATE_ROLE\"");
        assertThat(line).contains("\"method\":\"RoleController#createRole\"");
        assertThat(line).contains("\"user\":" + ADMIN_USER_ID);
        assertThat(line).contains("\"success\":true");
        assertThat(line).containsPattern("\"costMs\":\\d+");
        assertThat(line).containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}\"");
        assertThat(line).doesNotContain("\"params\"");
        assertThat(line).doesNotContain("\"exception\"");
    }

    @Test
    @DisplayName("失败路径 — success=false + 仅异常类名（不记 message），且异常原样上抛")
    void shouldEmitFailureJsonWithExceptionClassNameOnly_whenMethodThrows() throws Throwable {
        UserContext.setUser(adminUser());
        IllegalArgumentException failure = new IllegalArgumentException("内部细节-不得外泄");
        when(joinPoint.proceed()).thenThrow(failure);

        OperationLog operationLog = operationLog("RBAC", "DELETE_ROLE", false);
        assertThatThrownBy(() -> aspect.around(joinPoint, operationLog)).isSameAs(failure);

        String line = onlyAuditLine();
        assertThat(line).contains("\"success\":false");
        assertThat(line).contains("\"exception\":\"java.lang.IllegalArgumentException\"");
        assertThat(line).doesNotContain("内部细节-不得外泄");
    }

    @Test
    @DisplayName("无登录态 — user 记为 anonymous（不抛异常）")
    void shouldMarkAnonymousUser_whenNoUserContext() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, operationLog("ITEM_HOME", "DELETE_HOME_CONFIG", false));

        assertThat(onlyAuditLine()).contains("\"user\":\"" + OperationLogAspect.ANONYMOUS_USER + "\"");
    }

    @Test
    @DisplayName("recordParams=true — 只记 id 类字段摘要，请求体其他值不入日志")
    void shouldRecordIdSummaryOnly_whenRecordParamsEnabled() throws Throwable {
        UserContext.setUser(adminUser());
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{new CouponCreateReq("满100减20-秘密文案", 42L)});
        when(signature.getParameterNames()).thenReturn(new String[]{"req"});

        aspect.around(joinPoint, operationLog("MARKETING_COUPON", "CREATE_COUPON", true));

        String line = onlyAuditLine();
        assertThat(line).contains("\"params\":\"CouponCreateReq{orderId=42}\"");
        assertThat(line).doesNotContain("秘密文案");
    }

    @Test
    @DisplayName("recordParams=true — id 类标量参数记「名=值」，非 id 标量只记类型名")
    void shouldRecordIdLikeScalarValue_whenRecordParamsEnabled() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, 1});
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "status"});

        aspect.around(joinPoint, operationLog("TRADE_AFTER_SALE", "REVIEW_AFTER_SALE", true));

        assertThat(onlyAuditLine()).contains("\"params\":\"id=7, Integer\"");
    }

    @Test
    @DisplayName("recordParams=true — 超长摘要截断，params 值不超过 200 字符且带截断后缀")
    void shouldTruncateParamSummary_whenRecordParamsEnabled() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x".repeat(300)});
        when(signature.getParameterNames()).thenReturn(new String[]{"couponId"});

        aspect.around(joinPoint, operationLog("MARKETING_COUPON", "DISABLE_COUPON", true));

        String paramsValue = extractParamsValue(onlyAuditLine());
        assertThat(paramsValue).startsWith("couponId=x");
        assertThat(paramsValue).endsWith(OperationLogAspect.TRUNCATED_SUFFIX);
        assertThat(paramsValue.length()).isLessThanOrEqualTo(OperationLogAspect.MAX_PARAM_SUMMARY_LENGTH);
    }

    @Test
    @DisplayName("recordParams=true — 参数值含引号/换行时转义，审计日志仍为单行合法 JSON")
    void shouldEscapeSpecialCharacters_whenParamValueContainsQuotes() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"a\"b\nc"});
        when(signature.getParameterNames()).thenReturn(new String[]{"couponId"});

        aspect.around(joinPoint, operationLog("MARKETING_COUPON", "DISABLE_COUPON", true));

        assertThat(onlyAuditLine()).contains("\"params\":\"couponId=a\\\"b\\nc\"");
    }

    @Test
    @DisplayName("注解默认 — recordParams 默认 false（PII 安全默认），module/action/description 有默认值")
    void shouldDefaultToPiiSafeAttributes() throws Exception {
        assertThat(defaultOf("recordParams")).isEqualTo(false);
        assertThat(defaultOf("module")).isEqualTo("");
        assertThat(defaultOf("action")).isEqualTo("");
        assertThat(defaultOf("description")).isEqualTo("");
    }

    private static Object defaultOf(String attribute) throws NoSuchMethodException {
        Method method = OperationLog.class.getDeclaredMethod(attribute);
        return method.getDefaultValue();
    }

    /** 取出本次调用产生的唯一一条审计日志行；多于/少于一行即失败。 */
    private String onlyAuditLine() {
        assertThat(auditAppender.list).hasSize(1);
        ILoggingEvent event = auditAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getLoggerName()).isEqualTo(AUDIT_LOGGER_NAME);
        assertThat(event.getFormattedMessage()).doesNotContain("\n");
        return event.getFormattedMessage();
    }

    /** 从审计日志行中取出 {@code params} 字段的字符串值。 */
    private static String extractParamsValue(String line) {
        String prefix = "\"params\":\"";
        int from = line.indexOf(prefix) + prefix.length();
        return line.substring(from, line.lastIndexOf("\"}"));
    }

    private static OperationLog operationLog(String module, String action, boolean recordParams) {
        OperationLog operationLog = Mockito.mock(OperationLog.class);
        when(operationLog.module()).thenReturn(module);
        when(operationLog.action()).thenReturn(action);
        when(operationLog.recordParams()).thenReturn(recordParams);
        return operationLog;
    }

    private static UserInfo adminUser() {
        return UserInfo.builder().userId(ADMIN_USER_ID).username("admin").clientIp("203.0.113.9").build();
    }

    /** 切面用到的「声明类」占位（只需简单类名参与 method 字段）。 */
    private static final class RoleController {
    }

    /** 参数摘要用例载体：一个普通字段 + 一个 id 类字段。 */
    private record CouponCreateReq(String secret, Long orderId) {
    }
}
