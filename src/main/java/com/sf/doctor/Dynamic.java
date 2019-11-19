package com.sf.doctor;

import java.lang.invoke.MethodHandle;

public class Dynamic {


    public <T> T call(MethodHandle handle, Object... args) {
        try {
            return (T) handle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            throw new RuntimeException("fail to invoke method", throwable);
        }
    }
}
