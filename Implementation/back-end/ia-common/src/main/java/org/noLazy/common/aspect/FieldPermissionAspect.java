//package org.noLazy.common.aspect;
//
//import lombok.RequiredArgsConstructor;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.noLazy.common.annotation.FieldPermission;
//import org.noLazy.common.enums.RoleEnum;
//import org.noLazy.common.utils.UserContext;
//import org.springframework.stereotype.Component;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Set;
//
//@Aspect
//@Component
//@RequiredArgsConstructor
//public class FieldPermissionAspect {
//
//    private final UserContext userContext; // 获取当前登录用户信息
//
//    @Around("@annotation(fieldPermission)")
//    public Object checkPermission(ProceedingJoinPoint joinPoint, FieldPermission fieldPermission) throws Throwable {
//        // 1. 获取当前用户角色
//        RoleEnum currentRole = userContext.getCurrentRole();
//
//        // 2. 从方法参数里找到 UpdateReq 子类实例
//        Object[] args = joinPoint.getArgs();
//        UpdateReq req = null;
//        for (Object arg : args) {
//            if (arg instanceof UpdateReq) {
//                req = (UpdateReq) arg;
//                break;
//            }
//        }
//        if (req == null) {
//            return joinPoint.proceed(); // 没有要校验的 DTO，放过
//        }
//
//        // 3. 获取本次修改了的字段集合
//        Set<String> updatedFields = req.getUpdatedFields();
//
//        // 4. 解析注解配置的字段 -> 所需角色
//        Map<String, RoleEnum> fieldRoleMap = new HashMap<>();
//        for (String entry : fieldPermission.value()) {
//            String[] parts = entry.split(":");
//            String field = parts[0].trim();
//            RoleEnum requiredRole = RoleEnum.valueOf(parts[1].trim().toUpperCase());
//            fieldRoleMap.put(field, requiredRole);
//        }
//
//        // 5. 检查本次修改的字段中，是否有超出权限的
//        for (String field : updatedFields) {
//            if (fieldRoleMap.containsKey(field)) {
//                RoleEnum required = fieldRoleMap.get(field);
//                // 简单用角色优先级：ADMIN > VIP > USER
//                if (!currentRole.hasPrivilege(required)) {
//                    throw new BusinessException(ErrorCode.FIELD_FORBIDDEN,
//                            String.format("您无权修改字段: %s", field));
//                }
//            }
//        }
//
//        return joinPoint.proceed();
//    }
//}