package com.sf.doctor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

public class Bridge {

    protected static MethodHandle ENTER_HANDLE;
    protected static MethodHandle LEAVE_HANDLE;
    protected static MethodHandle PRINT_HANDLE;
    protected static MethodHandle CLEANUP_HANDLE;

    public static void enter(String signature) {
        Bridge.guardConsume(ENTER_HANDLE, signature);
    }

    public static void leave(String signature) {
        Bridge.guardConsume(LEAVE_HANDLE, signature);
    }

    public static void print(PrintWriter writer) {
        Bridge.guardConsume(PRINT_HANDLE, writer);
    }

    public static void cleanup() {
        Bridge.guardConsume(CLEANUP_HANDLE);
    }

    public static void stub(final Class<?> stack_tracing_class) throws ReflectiveOperationException {
        // now in host classloader
        try {
            Arrays.stream(stack_tracing_class.getMethods())
                    .filter((method) -> {
                        switch (method.getName()) {
                            case "enter":
                            case "leave":
                            case "print":
                            case "cleanup":
                                return true;
                            default:
                                return false;
                        }
                    })
                    .forEach((method) -> {
                        try {
                            MethodHandle handle = MethodHandles.lookup().unreflect(method);
                            Field handle_field = Bridge.class.getDeclaredField(String.format("%S_HANDLE", method.getName()));
                            handle_field.setAccessible(true);
                            handle_field.set(null, handle);
                        } catch (IllegalAccessException | NoSuchFieldException e) {
                            throw new RuntimeException(String.format("fail to get method handler of method: %s", method), e);
                        }
                    });
        } catch (Throwable throwable) {
            throw new ReflectiveOperationException(throwable);
        }
    }

    public static void unstub() {
        Arrays.stream(Bridge.class.getDeclaredFields())
                .filter((field) -> field.getName().endsWith("HANDLE"))
                .filter((field) -> Modifier.isStatic(field.getModifiers()))
                .peek((field) -> field.setAccessible(true))
                .forEach((field) -> {
                    try {
                        field.set(null, null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(String.format("fail to set field:%s", field), e);
                    }
                });
    }

    protected static void guardConsume(MethodHandle handle) {
        /*
        Optional.ofNullable(handle)
                .ifPresent((delegate) -> {
                    try {
                        delegate.invoke();
                    } catch (Throwable throwable) {
                        throwable.printStackTrace(System.out);
                    }
                });
*/
        return;
    }

    protected static <T> void guardConsume(MethodHandle handle, T input) {
        /*
        Optional.ofNullable(handle)
                .ifPresent((delegate) -> {
                    try {
                        delegate.invoke(input);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace(System.out);
                    }
                });
*/
        return;
    }
}
