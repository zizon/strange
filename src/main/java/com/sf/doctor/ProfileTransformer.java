package com.sf.doctor;

import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

public class ProfileTransformer implements ClassFileTransformer {

    protected final ConcurrentMap<Class<?>, NavigableSet<Method>> touched_methods;
    protected final Instrumentation instrumentation;
    protected final AgentChannel channel;

    public ProfileTransformer(Instrumentation instrumentation, AgentChannel channel) {
        this.touched_methods = new ConcurrentHashMap<>();
        this.instrumentation = instrumentation;
        this.channel = channel;
    }

    public void attach(String class_name, String method_name) {
        this.instrumentation.addTransformer(this, true);

        findClassByName(class_name)
                .forEach((clazz) -> MethodLookup.findMethod(clazz, method_name)
                        .peek((method) -> StackTracing.addToRootSet(RefinedClass.signature(method)))
                        .forEach(this::transformMethod)
                );
    }

    public void detach() {
        try {
            this.channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("fail to close channel", e);
        } finally {
            try {
                this.touched_methods.keySet().forEach((clazz) -> {
                    try {
                        this.instrumentation.retransformClasses(clazz);
                    } catch (UnmodifiableClassException e) {
                        e.printStackTrace(System.out);
                    }
                });
            } finally {
                System.out.println("remove instrumentation");
                this.instrumentation.removeTransformer(this);
            }
        }
    }

    public PrintWriter printer() {
        return this.channel.printer();
    }

    public boolean isClosed() {
        return this.channel.isClosed();
    }

    public boolean health(long timeout) {
        return this.channel.ping((int) timeout);
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String clazz,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (classBeingRedefined == null) {
            return null;
        }

        RefinedClass refined = new RefinedClass(new ByteArrayInputStream(classfileBuffer));
        if (!this.isClosed()) {
            Optional.ofNullable(touched_methods.get(classBeingRedefined))
                    .orElseGet(Collections::emptyNavigableSet).stream()
                    .flatMap(refined::profileMethod)
                    .flatMap((node) -> Arrays.stream(instrumentation.getAllLoadedClasses())
                            .filter((holder) -> Type.getInternalName(holder).equals(node.owner))
                            .flatMap((holder) -> MethodLookup.findMethod(holder, node.name))
                            .filter((method) -> Type.getMethodDescriptor(method).equals(node.desc))
                    )
                    .peek((method) -> channel.println(String.format(
                            "refine method:%s",
                            method)
                    ))
                    .forEach(this::transformMethod);
        } else {
            Optional.ofNullable(touched_methods.get(classBeingRedefined))
                    .orElseGet(Collections::emptyNavigableSet)
                    .forEach((method) -> channel.println(
                            String.format("reverting method:%s", method)
                    ));
            refined.revert();
        }

        //refined.print(this.writer);
        return refined.bytecode();
    }

    protected void transformMethod(Method method) {
        int skip_class = Modifier.NATIVE | Modifier.ABSTRACT;
        if ((method.getModifiers() & skip_class) != 0) {
            printer().println(String.format("skip transform method without bytecode: %s", method));
            return;
        } else if (method.isBridge()) {
            printer().println(String.format("skip transform bridge method: %s", method));
            return;
        }

        findClassByName(method.getDeclaringClass().getName())
                // mark touched
                .peek((clazz) -> this.touched_methods.computeIfAbsent(
                        clazz,
                        (ignore) -> new ConcurrentSkipListSet<>(Comparator.comparing(Method::toString)))
                        .add(method)
                )
                .forEach((clazz) -> {
                    try {
                        this.instrumentation.retransformClasses(clazz);
                    } catch (UnmodifiableClassException e) {
                        throw new RuntimeException("fail to transform method: " + method, e);
                    }
                });
    }

    protected Stream<Class> findClassByName(String name) {
        return Arrays.stream(this.instrumentation.getAllLoadedClasses())
                .filter((clazz) -> clazz.getName().equals(name));
    }
}
