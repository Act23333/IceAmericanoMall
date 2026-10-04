package org.icedamericanomall.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@code @OperationLog} 切面 —— 管理操作审计的<b>日志管道</b>（ADR-032 管道一）。
 *
 * <p>每拦截一次管理端写操作，向专用 logger <b>{@code AUDIT}</b> 输出<b>一行 JSON</b>：
 * <pre>
 * {"timestamp":"2026-10-03T10:15:30.123+08:00","module":"RBAC","action":"CREATE_ROLE",
 *  "method":"RoleController#createRole","user":1001,"success":true,"costMs":12}
 * </pre>
 * 字段说明：{@code timestamp} ISO-8601 带偏移、{@code module}/{@code action} 取自注解、
 * {@code method} 为「类名#方法名」、{@code user} 取 {@link UserContext} 的 userId
 * （取不到写字符串 {@code anonymous}）、{@code success} 布尔、{@code costMs} 耗时毫秒。
 * 失败路径追加 {@code exception}（<b>仅异常类全名，不含 message</b>，防止内部细节外泄）；
 * 仅当 {@link OperationLog#recordParams()} 为 {@code true} 时追加 {@code params} 摘要（截断 ≤200 字符）。
 *
 * <p><b>零业务依赖</b>：本切面不注入任何服务 Bean（不用 Mapper/Redis/Feign），
 * userId 通过静态 {@link UserContext} 读取，因此可被所有服务无条件加载；
 * 若注入具体服务 Bean，共享内核就会反向耦合业务实现。切面自身异常全部吞掉并降级为
 * 普通 WARN 日志（审计不得中断业务主流程）。
 *
 * <p><b>顺序</b>：{@link Ordered#LOWEST_PRECEDENCE}（最内层）。审计切面无需参与事务编排，
 * 也不得改变事务切面的语义，故显式声明为最低优先级。
 *
 * <p><b>PII 安全</b>：默认不记录任何参数值；{@code params} 摘要只含「参数类型 + id 类字段值」，
 * 字符串/数字类的非 id 参数一律只记类型名。日志中的金额、手机号、密文永不出现。
 */
@Aspect
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j(topic = OperationLogAspect.AUDIT_LOGGER_NAME)
public class OperationLogAspect {

    /** 专用审计 logger 名 —— ELK / Logstash 侧按 logger 名过滤审计事件。 */
    public static final String AUDIT_LOGGER_NAME = "AUDIT";

    /** 审计降级日志 logger（审计管道自身故障时使用，避免污染 AUDIT 的 JSON 流）。 */
    private static final Logger FALLBACK_LOG = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 无法从 {@link UserContext} 取到 userId 时写入的占位值。 */
    static final String ANONYMOUS_USER = "anonymous";

    /** 参数摘要最大长度（字符）。 */
    static final int MAX_PARAM_SUMMARY_LENGTH = 200;

    /** 参数摘要截断后缀。 */
    static final String TRUNCATED_SUFFIX = "...";

    /** 时间戳格式：ISO-8601 带毫秒与偏移，便于 ELK 直接识别为 date 类型。 */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_MODULE = "module";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_USER = "user";
    private static final String FIELD_SUCCESS = "success";
    private static final String FIELD_COST_MS = "costMs";
    private static final String FIELD_EXCEPTION = "exception";
    private static final String FIELD_PARAMS = "params";

    /**
     * 环绕通知：方法正常返回或抛出异常后各输出一条审计日志，异常原样向上抛出。
     *
     * @param joinPoint    被拦截的管理端方法
     * @param operationLog 方法上的审计注解
     * @return 原方法返回值（不做任何包装或改写）
     * @throws Throwable 原方法抛出的任何异常（审计不改变异常语义）
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            emit(joinPoint, operationLog, true, null, startNanos);
            return result;
        } catch (Throwable throwable) {
            emit(joinPoint, operationLog, false, throwable, startNanos);
            throw throwable;
        }
    }

    /** 输出审计日志；任何输出期异常都被降级处理，绝不影响业务结果。 */
    private void emit(ProceedingJoinPoint joinPoint, OperationLog operationLog, boolean success,
                      Throwable throwable, long startNanos) {
        try {
            log.info(buildJson(joinPoint, operationLog, success, throwable, startNanos));
        } catch (RuntimeException e) {
            FALLBACK_LOG.warn("审计日志输出失败，已忽略（不影响业务主流程）", e);
        }
    }

    /**
     * 组装单行 JSON 审计日志（包可见，便于单测锁定字段与格式）。
     *
     * @param joinPoint    被拦截方法
     * @param operationLog 审计注解
     * @param success      是否成功
     * @param throwable    失败时的异常（成功传 {@code null}）
     * @param startNanos   进入切面时的 {@link System#nanoTime()}
     * @return 单行 JSON 字符串
     */
    static String buildJson(ProceedingJoinPoint joinPoint, OperationLog operationLog, boolean success,
                            Throwable throwable, long startNanos) {
        StringBuilder json = new StringBuilder(192);
        json.append('{');
        appendStringField(json, FIELD_TIMESTAMP, OffsetDateTime.now().format(TIMESTAMP_FORMATTER));
        appendStringField(json, FIELD_MODULE, operationLog.module());
        appendStringField(json, FIELD_ACTION, operationLog.action());
        appendStringField(json, FIELD_METHOD, methodDimension(joinPoint));
        appendUserField(json);
        appendRawField(json, FIELD_SUCCESS, Boolean.toString(success));
        appendRawField(json, FIELD_COST_MS, Long.toString((System.nanoTime() - startNanos) / NANOS_PER_MILLI));
        if (throwable != null) {
            appendStringField(json, FIELD_EXCEPTION, throwable.getClass().getName());
        }
        if (operationLog.recordParams()) {
            appendStringField(json, FIELD_PARAMS, buildParamSummary(joinPoint));
        }
        return json.append('}').toString();
    }

    /**
     * 构造参数摘要：只保留参数类型与 id 类字段值（不含其他业务值），整体截断至 200 字符。
     *
     * @param joinPoint 被拦截方法
     * @return 摘要文本；无参数时为空串
     */
    static String buildParamSummary(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }
        String[] names = parameterNames(joinPoint);
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            String parameterName = names != null && i < names.length ? names[i] : null;
            String described = describeArg(args[i], parameterName);
            if (described == null) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(described);
        }
        return truncate(summary.toString());
    }

    /** 描述单个参数：id 类标量记「名=值」，其余标量只记类型名，对象追加其 id 类字段值。 */
    private static String describeArg(Object arg, String parameterName) {
        if (arg == null) {
            return null;
        }
        if (isScalar(arg)) {
            return isIdLike(parameterName) ? parameterName + "=" + truncate(String.valueOf(arg))
                    : arg.getClass().getSimpleName();
        }
        String typeName = arg.getClass().getSimpleName();
        String idFields = idFieldsOf(arg);
        return idFields.isEmpty() ? typeName : typeName + "{" + idFields + "}";
    }

    /** 反射收集对象（含父类）中 id 类标量字段：{@code id=1, userId=2}。 */
    private static String idFieldsOf(Object arg) {
        StringBuilder fields = new StringBuilder();
        for (Class<?> type = arg.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !isIdLike(field.getName())) {
                    continue;
                }
                Object value = readField(field, arg);
                if (value == null || !isScalar(value)) {
                    continue;
                }
                if (fields.length() > 0) {
                    fields.append(", ");
                }
                fields.append(field.getName()).append('=').append(truncate(String.valueOf(value)));
            }
        }
        return fields.toString();
    }

    /** 读取字段值；不可访问时返回 {@code null}（摘要缺失优于中断审计）。 */
    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | SecurityException e) {
            FALLBACK_LOG.debug("审计参数摘要读取字段失败: {}", field.getName(), e);
            return null;
        }
    }

    /** id 类参数名判定：{@code id} / 驼峰 {@code *Id} / 下划线 {@code *_id} / 常量 {@code *ID}。 */
    private static boolean isIdLike(String name) {
        if (name == null) {
            return false;
        }
        return "id".equalsIgnoreCase(name)
                || name.endsWith("Id") || name.endsWith("Ids") || name.endsWith("ID")
                || name.endsWith("_id") || name.endsWith("_ids");
    }

    /** 标量判定：数值/字符串/布尔/字符 —— 只有这类参数才可能输出值（且仅限 id 类）。 */
    private static boolean isScalar(Object value) {
        return value instanceof Number || value instanceof CharSequence
                || value instanceof Boolean || value instanceof Character;
    }

    /** 截断至 {@link #MAX_PARAM_SUMMARY_LENGTH} 字符（含后缀，保证总长不超上限）。 */
    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_PARAM_SUMMARY_LENGTH) {
            return value;
        }
        int keep = MAX_PARAM_SUMMARY_LENGTH - TRUNCATED_SUFFIX.length();
        return value.substring(0, keep) + TRUNCATED_SUFFIX;
    }

    /** 方法维度：{@code 类名#方法名}。 */
    private static String methodDimension(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
    }

    /** 取编译期参数名（依赖 {@code -parameters}）；不可得时返回 {@code null}。 */
    private static String[] parameterNames(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature() instanceof MethodSignature methodSignature
                ? methodSignature.getParameterNames() : null;
    }

    /** {@code user} 字段：有登录态写数字，无登录态写 {@code "anonymous"}。 */
    private static void appendUserField(StringBuilder json) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            appendStringField(json, FIELD_USER, ANONYMOUS_USER);
        } else {
            appendRawField(json, FIELD_USER, Long.toString(userId));
        }
    }

    private static void appendStringField(StringBuilder json, String name, String value) {
        appendFieldName(json, name);
        appendQuoted(json, value == null ? "" : value);
    }

    private static void appendRawField(StringBuilder json, String name, String raw) {
        appendFieldName(json, name);
        json.append(raw);
    }

    /** 追加 {@code ,"name":}；首字段不加分隔符。 */
    private static void appendFieldName(StringBuilder json, String name) {
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(name).append("\":");
    }

    /** 追加 JSON 字符串字面量（含转义），防止参数摘要里的引号/控制字符破坏行内 JSON 结构。 */
    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
