package com.sf.doctor;


import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WhiteList {

    protected Set<String> filtered;

    public WhiteList() {
        this.filtered =
                Stream.of(
                        Bridge.class,
                        StackTracing.class
                ).map(Class::getName)
                        .collect(Collectors.toSet());
    }

    public boolean match(Class<?> clazz) {
        String name = clazz.getName();
        return clazz.getClassLoader() == null
                || name.startsWith("java.lang")
                || name.startsWith("java.util")
                || name.startsWith("com.sf.doctor");
    }

    protected ClassNode bytecode(Class<?> clazz) {
        try {
            ClassReader reader = new ClassReader(clazz.getName());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format(
                    "fail to read bytecode:%s",
                    clazz.getName()
            ), e);
        }
    }

    protected MethodInsnNode instructions(Class<?> clazz) {
        try {
            ClassReader reader = new ClassReader(clazz.getName());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format(
                    "fail to read bytecode:%s",
                    clazz.getName()
            ), e);
        }
        return null;
    }
}
