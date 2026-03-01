package com.erp.service.security.annotation;

import com.erp.domain.security.HrModule;
import com.erp.domain.security.HrAction;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HrPermission {

    HrModule module();

    HrAction[] action();
}