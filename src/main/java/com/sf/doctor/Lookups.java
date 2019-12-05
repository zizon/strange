package com.sf.doctor;


import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class Lookups {

    public static Stream<Method> sameNameAndDescriptor(Method method) {
        String descriptor = Type.getMethodDescriptor(method);
        return collectSuperAndInterface(method.getDeclaringClass())
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter((matching) -> matching.getName().equals(method.getName()))
                .filter((matching) -> Type.getMethodDescriptor(matching).equals(descriptor))
                .distinct()
                ;
    }

    public static Stream<Method> collectMethodsWithByteCode(Class<?> clazz) {
        return Stream.concat(
                Stream.of(clazz),
                Lookups.collectSuperAndInterface(clazz)
        ).map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter((method) -> {
                    if (method.getDeclaringClass().isInterface()) {
                        return method.isDefault();
                    }

                    return !method.isBridge()
                            && (method.getModifiers() & Modifier.NATIVE) == 0
                            && (method.getModifiers() & Modifier.ABSTRACT) == 0;

                })
                .distinct();
    }

    public static Stream<Class<?>> collectSuperAndInterface(Class<?> clazz) {
        return Stream.concat(
                Arrays.stream(clazz.getInterfaces()),
                Stream.of(clazz.getSuperclass())
        ).filter(Objects::nonNull)
                .flatMap(Lookups::collectSuperAndInterface)
                .distinct()
                ;
    }
}
