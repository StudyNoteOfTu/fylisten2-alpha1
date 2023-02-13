package com.company.fylisten.fylisten2.annotations;


import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@IntDef({
        Status.INITIALED,
        Status.ON_ATTACH,
        Status.ON_CREATE,
        Status.ON_CREATE_VIEW,
        Status.ON_START,
        Status.ON_RESUME,
        Status.ON_PAUSE,
        Status.ON_STOP,
        Status.ON_DESTROY,
        Status.ON_DESTROY_VIEW,
        Status.ON_DETACH
})
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Status {
    public static final int INITIALED = -1;
    public static final int ON_ATTACH = 0;
    public static final int ON_CREATE = 1;
    public static final int ON_CREATE_VIEW = 2;
    public static final int ON_START = 3;
    public static final int ON_RESUME = 4;
    public static final int ON_PAUSE = 5;
    public static final int ON_STOP = 6;
    public static final int ON_DESTROY_VIEW = 7;
    public static final int ON_DESTROY = 8;
    public static final int ON_DETACH = 9;
}
