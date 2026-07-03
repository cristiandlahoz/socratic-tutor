package com.wornux.security.authorization;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionMethodAspect {

    private final AuthorizationService authorizationService;

    public PermissionMethodAspect(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Around("@annotation(com.wornux.security.authorization.RequiresPermission) || @within(com.wornux.security.authorization.RequiresPermission)")
    public Object requirePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        var permission = resolveAnnotation(joinPoint);
        if (permission != null) {
            authorizationService.check(permission.value());
        }
        return joinPoint.proceed();
    }

    private RequiresPermission resolveAnnotation(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var methodAnnotation = AnnotationUtils.findAnnotation(signature.getMethod(), RequiresPermission.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequiresPermission.class);
    }
}
