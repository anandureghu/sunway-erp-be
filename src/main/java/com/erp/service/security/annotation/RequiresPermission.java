package com.erp.service.security.annotation;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    AppModule module();

    AppAction[] action();
}
