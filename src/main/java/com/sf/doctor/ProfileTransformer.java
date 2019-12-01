package com.sf.doctor;

import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileTransformer implements ClassFileTransformer {

    protected final ConcurrentMap<Class<?>, ConcurrentMap<Method, Boolean>> transformed_methods;
    protected final Instrumentation instrumentation;
    protected final AgentChannel channel;
    protected Set<String> whitelisted_signature;

    public ProfileTransformer(Instrumentation instrumentation, AgentChannel channel) {
        this.transformed_methods = new ConcurrentHashMap<>();
        this.instrumentation = instrumentation;
        this.channel = channel;
        this.whitelisted_signature = Collections.emptySet();

        this.refreshWhiteList();
    }

    public ProfileTransformer refreshWhiteList() {
        ///this.whitelisted_signature = signatures;
        this.whitelisted_signature = new WhiteList().buildSignatures();

        return this;
    }

    public void attach(String class_name, String method_name) {
        this.instrumentation.addTransformer(this, true);

        findClassByName(class_name)
                .forEach((clazz) ->
                        ("*".equals(method_name) ?
                                Arrays.stream(clazz.getDeclaredMethods())
                                : MethodLookup.findMethod(clazz, method_name)
                        )
                                // add root set method
                                .peek((method) -> StackTracing.addToRootSet(RefinedClass.signature(method)))
                                // mark being transform
                                .peek((method) -> transformed_methods.computeIfAbsent(
                                        method.getDeclaringClass(),
                                        (ignore) -> new ConcurrentHashMap<>()
                                        ).computeIfAbsent(method, (ignore) -> false)
                                )
                                .map(Method::getDeclaringClass)
                                .distinct()
                                .forEach(this::transformClass)
                )
        ;
    }

    public void detach() {
        try {
            this.channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("fail to close channel", e);
        } finally {
            try {
                this.transformed_methods.keySet()
                        .forEach((clazz) -> {
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
            Optional.ofNullable(transformed_methods.get(classBeingRedefined))
                    .orElseGet(ConcurrentHashMap::new).keySet().stream()
                    // filter that should transform
                    .filter((method) -> !this.shouldNotTransformed(method))
                    // add profile code
                    .peek((method) -> transformed_methods.computeIfAbsent(
                            method.getDeclaringClass(),
                            (ignore) -> new ConcurrentHashMap<>()
                    ).compute(method, (ignore_key, ignore_value) -> true))
                    .flatMap(refined::profileMethod)
                    // referenced method
                    .flatMap((node) -> Arrays.stream(instrumentation.getAllLoadedClasses())
                            .filter((holder) -> Type.getInternalName(holder).equals(node.owner))
                            .flatMap((holder) -> MethodLookup.findMethod(holder, node.name))
                            .filter((method) -> Type.getMethodDescriptor(method).equals(node.desc))
                    )
                    // filter that should transform
                    .filter((method) -> !this.shouldNotTransformed(method))
                    .collect(Collectors.groupingBy(
                            Method::getDeclaringClass,
                            // simply add transform entry.
                            // summary touch states of method under this class
                            Collectors.reducing(
                                    true,
                                    (method) -> transformed_methods.computeIfAbsent(
                                            method.getDeclaringClass(),
                                            (ignore) -> new ConcurrentHashMap<>()
                                    ).computeIfAbsent(method, (ignore) -> false),
                                    Boolean::logicalAnd
                            )
                    )).entrySet().stream()
                    // filter class that had not yet transformed method
                    .filter((entry) -> !entry.getValue())
                    .map(Map.Entry::getKey)
                    .forEach(this::transformClass)
            ;
        } else {
            Optional.ofNullable(transformed_methods.get(classBeingRedefined))
                    .ifPresent((methods) -> methods.keySet()
                            .forEach((transforemd_method) ->
                                    channel.println(
                                            String.format("reverting method:%s", transforemd_method)
                                    )
                            ));
            refined.revert();
        }

        return refined.bytecode();
    }

    protected void transformClass(Class<?> clazz) {
        Optional.ofNullable(instrumentation)
                .ifPresent((instrumentation) -> {
                    try {
                        instrumentation.retransformClasses(clazz);
                    } catch (UnmodifiableClassException e) {
                        throw new RuntimeException(String.format(
                                "fail to transform class:%s",
                                clazz
                        ), e);
                    }
                });
    }

    protected boolean shouldNotTransformed(Method method) {
        int skip_class = Modifier.NATIVE | Modifier.ABSTRACT;
        if ((method.getModifiers() & skip_class) != 0) {
            printer().println(String.format("skip transform method without bytecode: %s", method));
            return true;
        } else if (method.isBridge()) {
            printer().println(String.format("skip transform bridge method: %s", method));
            return false;
        } else if (whitelisted_signature.contains(RefinedClass.signature(method))) {
            printer().println(String.format("skip whitelisted method: %s", method));
            return false;
        }

        return Optional.ofNullable(transformed_methods.get(method.getDeclaringClass()))
                .flatMap((methods) -> Optional.ofNullable(methods.get(method)))
                .orElse(false);
    }

    protected Stream<Class<?>> findClassByName(String name) {
        return Arrays.<Class<?>>stream(this.instrumentation.getAllLoadedClasses())
                .filter((clazz) -> clazz.getName().equals(name));
    }
}
