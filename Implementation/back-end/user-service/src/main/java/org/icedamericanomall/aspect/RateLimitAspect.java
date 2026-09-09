package org.icedamericanomall.aspect;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.noLazy.common.annotation.RateLimit;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.IpUtil;
import org.noLazy.common.utils.RateLimitUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// RateLimitAspect has been moved to ia-common (org.noLazy.common.aspect.RateLimitAspect)
// so all services benefit from @RateLimit support.