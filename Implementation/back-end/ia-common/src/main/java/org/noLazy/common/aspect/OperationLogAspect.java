package org.noLazy.common.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.noLazy.common.annotation.OperationLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志 AOP — 拦截 @OperationLog 注解的方法，写入 operation_log 表。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private final JdbcTemplate jdbcTemplate;

    public OperationLogAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint jp, OperationLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = jp.proceed();
        try {
            String userId = "0";
            String ip = "";
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ip = request.getRemoteAddr();
                Object uid = request.getAttribute("userId");
                if (uid != null) userId = uid.toString();
            }
            jdbcTemplate.update(
                    "INSERT INTO operation_log (user_id, action, target, detail, ip, create_time) VALUES (?,?,?,?,?,NOW())",
                    Long.valueOf(userId), opLog.action(), jp.getSignature().toShortString(),
                    opLog.value(), ip);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
        return result;
    }
}
