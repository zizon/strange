package com.sf.doctor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WhiteList {

    protected final Map<String, Set<Class<?>>> classes;

    public WhiteList(Map<String, Set<Class<?>>> classes) {
        this.classes = classes;
    }

    public Set<String> build() {
        Set<String> visited = new ConcurrentSkipListSet<>();

        Stream.of(
                Agent.class,
                ProfileTransformer.class,
                Bridge.class
        ).map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .forEach((method) -> this.propagate(method, visited))
        ;
        return visited;
    }

    protected void propagate(Method method, Set<String> visited) {
        Stream.concat(
                // self
                Stream.of(method),
                // and super/interfaces
                Lookups.sameNameAndDescriptor(method)
        ).parallel().filter((matching) -> visited.add(RefinedClass.signature(method)))
                // group by owner
                .collect(Collectors.groupingBy(
                        Method::getDeclaringClass,
                        Collectors.groupingBy((reflective) -> String.format(
                                "%s%s",
                                method.getName(),
                                Type.getMethodDescriptor(method)
                        ))
                ))
                .forEach((owner, methods) -> {
                    try {
                        ClassNode node = new ClassNode();
                        ClassReader reader = new ClassReader(owner.getName());
                        reader.accept(node, 0);

                        Optional.ofNullable(node.methods)
                                .orElseGet(Collections::emptyList)
                                .stream()
                                // filter if method_node is interested
                                .filter((method_node) -> Optional.ofNullable(
                                        methods.get(String.format(
                                                "%s%s",
                                                method_node.name,
                                                method_node.desc
                                        )))
                                        .map((match) -> !match.isEmpty())
                                        .orElse(false)
                                )
                                // find method invocation
                                .map((method_node) -> Optional.ofNullable(method_node.instructions)
                                        .orElseGet(InsnList::new))
                                .flatMap((instructions) -> Arrays.stream(instructions.toArray()))
                                .filter((instruction) -> instruction instanceof MethodInsnNode)
                                .map(MethodInsnNode.class::cast)
                                // instruction to method
                                .flatMap(this::transform)
                                // propagate
                                .forEach((reflective) -> this.propagate(reflective, visited))
                        ;
                    } catch (IOException e) {
                        throw new UncheckedIOException(String.format(
                                "fail to read class bytecode:%s",
                                owner
                        ), e);
                    }
                });
    }

    protected Stream<Method> transform(MethodInsnNode node) {
        return this.classes.getOrDefault(node.owner.replace("/", "."),Collections.emptySet())
                .stream()
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter((method) -> method.getName().equals(node.name))
                .filter((method) -> Type.getMethodDescriptor(method).equals(node.desc))
                ;
    }
}
