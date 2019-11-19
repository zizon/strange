package com.sf.doctor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodLookup {

    public static Stream<MethodHandle> findMethodHandle(Class<?> clazz, String name) {
        return findMethod(clazz, name)
                .map((method) -> {
                    try {
                        return MethodHandles.lookup().unreflect(method);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("fail to get handle", e);
                    }
                });
    }

    public static Stream<Method> findMethod(Class<?> clazz, String name) {
        return findMethodWihtLevel(clazz, name, 0)
                .collect(Collectors.groupingBy(
                        // key by method signature
                        (entry) -> methodType(entry.getKey()),

                        // choose most concrete method
                        Collectors.minBy(Comparator.comparingInt(Map.Entry::getValue))
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Map.Entry::getKey)
                ;
    }

    protected static String methodType(Method method) {
        return MethodType.methodType(
                method.getReturnType(),
                method.getParameterTypes()
        ).toString();
    }

    protected static Stream<Map.Entry<Method, Integer>> findMethodWihtLevel(Class<?> clazz, String name, int level) {
        return Stream.of(
                // declared method
                Arrays.stream(clazz.getDeclaredMethods())
                        .filter((method) -> method.getName().equals(name))
                        .map((method) -> new AbstractMap.SimpleImmutableEntry<>(method, level)),

                // parent method
                Optional.ofNullable(clazz.getSuperclass())
                        .filter((method) -> method.getName().equals(name))
                        .map((parent) -> findMethodWihtLevel(parent, name, level + 1))
                        .orElseGet(Stream::empty),

                // default interface method
                Arrays.stream(clazz.getInterfaces())
                        .flatMap((implemented) -> findMethodWihtLevel(implemented, name, level + 1))
                        .filter((method) -> method.getKey().getName().equals(name))
                        .filter((entry) -> entry.getKey().isDefault())
        ).flatMap(Function.identity());
    }

}
