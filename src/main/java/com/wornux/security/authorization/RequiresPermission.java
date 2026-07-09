package com.wornux.security.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.wornux.security.permission.AppPermission;
import com.wornux.services.workspace.WorkspaceDestination;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    AppPermission value();

    WorkspaceDestination workspace() default WorkspaceDestination.NO_ACCESS;
}
