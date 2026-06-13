package org.noLazy.common.interceptor;


import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Objects;

/**
 * @ClassName: UserInterceptor
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/23 16:43
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.interceptor
 */
@Slf4j
public class UserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //获取登录用户信息
        String userInfo = request.getHeader("X-User-Id");
        //判断是否获取了用户信息，如果有，存入ThreadLocal
        if (StrUtil.isNotBlank(userInfo)) {
            log.info("用户登录： {}", userInfo);
            UserContext.setUser(Long.parseLong(userInfo.trim()));
        }
        //为什么没有用户信息，也放行？
        //原因：已经在网关服务中校验了用户权限和密码，并且通过配置判断是否需要拦截该请求，后续只要有用户信息说明就是需要登录权限，存储用户信息，不需要就是不需要登录权限但都要放行，只有两种情况，需不需要用户信息到有没有用户信息，即是否要存储用户信息
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.removeUser();
    }
}
