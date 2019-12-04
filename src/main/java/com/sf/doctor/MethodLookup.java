package com.sf.doctor;


import org.objectweb.asm.Type;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodLookup {

    public static Stream<Method> findMethods(Class<?> clazz) {
        return findMethodsWihtLevel(clazz, 0)
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

    public static Stream<Method> findMethod(Class<?> clazz, String name) {
        return findMethods(clazz)
                .filter((method) -> method.getName().equals(name));
    }

    public static Stream<Method> findMethod(Class<?> clazz, String name, String descriptor) {
        return findMethods(clazz)
                .filter((method) -> method.getName().equals(name))
                .filter((method) -> Type.getMethodDescriptor(method).equals(descriptor));
    }

    protected static String methodType(Method method) {
        return MethodType.methodType(
                method.getReturnType(),
                method.getParameterTypes()
        ).toString();
    }

    protected static Stream<Map.Entry<Method, Integer>> findMethodsWihtLevel(Class<?> clazz, int level) {
        return Stream.of(
                // declared method
                Arrays.stream(clazz.getDeclaredMethods())
                        .map((method) -> new AbstractMap.SimpleImmutableEntry<>(method, level)),

                // parent method
                Optional.ofNullable(clazz.getSuperclass())
                        .map((parent) -> findMethodsWihtLevel(parent, level + 1))
                        .orElseGet(Stream::empty),

                // default interface method
                Arrays.stream(clazz.getInterfaces())
                        .flatMap((implemented) -> findMethodsWihtLevel(implemented, level + 1))
                        .filter((entry) -> entry.getKey().isDefault())
        ).flatMap(Function.identity());
    }
}
