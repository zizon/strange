package com.sf.doctor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Lookups {

    protected Map<String, Set<Class<?>>> classes;
    protected Map<String, Map<Class<?>, Optional<Method>>> class_methods;

    public Lookups(Map<String, Set<Class<?>>> classes) {
        this.classes = classes;
        this.class_methods = new ConcurrentHashMap<>();
    }

    public Stream<Class<?>> upperBoundedBy(Class<?> upper) {
        return this.classes.values().stream()
                .flatMap(Set::stream)
                .filter(upper::isAssignableFrom)
                .collect(Collectors.toSet())
                .stream()
                ;
    }

    public Stream<Method> declaredMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods());
    }

    protected Stream<Class<?>> byName(String name) {
        return this.classes.values().stream().flatMap(Set::stream)
                .filter((clazz) -> clazz.getName().equals(name))
                .collect(Collectors.toSet()).stream();
    }
}
