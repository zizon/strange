package com.sf.doctor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileTransformer implements ClassFileTransformer {

    protected final Instrumentation instrumentation;
    protected final AgentChannel channel;
    protected final WhiteList whitelist;

    public ProfileTransformer(Instrumentation instrumentation, AgentChannel channel) {
        this.instrumentation = instrumentation;
        this.channel = channel;
        this.whitelist = new WhiteList();
    }

    public void attach(String class_name, String method_name) {
        Lookups lookups = new Lookups(Arrays.<Class<?>>stream(instrumentation.getAllLoadedClasses())
                .collect(Collectors.groupingBy(
                        Class::getName,
                        Collectors.toSet()
                )));
        this.instrumentation.addTransformer(this, true);

        Stream<Class<?>> base_classes = lookups.byName(class_name);

        // a star matching start with the exact methods declared in class.
        // a concrete matching start with all possible subclass
        (
                method_name.equals("*")
                        ? base_classes
                        : base_classes
                        .flatMap(lookups::upperBoundedBy)
        ).flatMap(lookups::declaredMethods)
                .map(RefinedClass::signature)
                .forEach(StackTracing::addToRootSet)
        ;

        // trigger all
        this.trigger();
    }

    public void detach() {
        try {
            this.channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("fail to close channel", e);
        } finally {
            try {
                this.trigger();
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

        try {
            return workForClass(classBeingRedefined, classfileBuffer);
        } catch (Throwable throwable) {
            return null;
        }
    }

    protected byte[] workForClass(Class<?> clazz, byte[] classfileBuffer) {
        if (this.isClosed()) {
            // closed,revert changes
            return new RefinedClass(classfileBuffer)
                    .revert();
        }

        return new RefinedClass(classfileBuffer)
                .profiling()
                .bytecode();
    }

    protected void triggerEach() {
        Arrays.stream(this.instrumentation.getAllLoadedClasses())
                .filter((clazz) -> !whitelist.match(clazz))
                .filter(instrumentation::isModifiableClass)
                .filter((clazz) -> !clazz.isSynthetic())
                // retransform lambda will crash some jvm.
                // see https://bugs.openjdk.java.net/browse/JDK-8008678
                .filter((clazz) -> !clazz.getName().startsWith("java.lang.invoke.LambdaForm"))
                .filter((clazz) -> !clazz.getName().equals(Bridge.class.getName()))
                .filter((clazz) -> !clazz.getName().equals(StackTracing.class.getName()))
                //.findAny()
                .forEach((clazz) -> {
                    //clazz = RefinedClass.class;
                    try {
                        this.instrumentation.retransformClasses(clazz);
                    } catch (Throwable e) {
                        throw new RuntimeException("fail to trigger retransform:" + clazz, e);
                    }
                });
    }

    protected void trigger() {
        try {
            this.instrumentation.retransformClasses(
                    Arrays.stream(this.instrumentation.getAllLoadedClasses())
                            .filter((clazz) -> !this.whitelist.match(clazz))
                            .filter(instrumentation::isModifiableClass)
                            .filter((clazz) -> !clazz.isSynthetic())
                            // retransform lambda will crash some jvm.
                            // see https://bugs.openjdk.java.net/browse/JDK-8008678
                            .filter((clazz) -> !clazz.getName().startsWith("java.lang.invoke.LambdaForm"))
                            .toArray(Class<?>[]::new)
            );
        } catch (Throwable e) {
            throw new RuntimeException("fail to trigger retransform:", e);
        }
    }
}
