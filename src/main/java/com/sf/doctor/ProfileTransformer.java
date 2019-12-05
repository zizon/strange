package com.sf.doctor;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

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

    protected static final Comparator<Method> METHOD_COMPARATOR = (left, right) -> {
        Class<?> left_owner = left.getDeclaringClass();
        Class<?> right_owner = right.getDeclaringClass();
        if (left_owner.isAssignableFrom(right_owner)) {
            return -1;
        } else if (right_owner.isAssignableFrom(left_owner)) {
            return 1;
        }

        throw new RuntimeException(String.format(
                "should in associated , but diverse, %s %s",
                left,
                right
        ));
    };

    protected final ConcurrentMap<Class<?>, ConcurrentMap<Method, Boolean>> work;
    protected final Instrumentation instrumentation;
    protected final AgentChannel channel;
    protected Set<String> whitelisted_signature;
    protected Map<String, Set<Class<?>>> cache_classes;

    public ProfileTransformer(Instrumentation instrumentation, AgentChannel channel) {
        this.work = new ConcurrentHashMap<>();
        this.instrumentation = instrumentation;
        this.channel = channel;

        this.refresh();
    }

    public ProfileTransformer refresh() {
        this.cache_classes = Arrays.<Class<?>>stream(instrumentation.getAllLoadedClasses())
                .collect(Collectors.groupingBy(
                        Class::getName,
                        Collectors.toSet()
                ));
        this.whitelisted_signature = new WhiteList(this.cache_classes).build();

        return this;
    }

    public void attach(String class_name, String method_name) {
        this.instrumentation.addTransformer(this, true);

        // collect match methods
        this.cache_classes.getOrDefault(class_name, Collections.emptySet()).stream()
                .flatMap((clazz) -> Stream.concat(
                        Stream.of(clazz),
                        implemented(clazz)
                ))
                .flatMap(Lookups::collectMethodsWithByteCode)
                .filter(this::accept)
                .filter((method) -> method_name.equals("*") || method.getName().equals(method_name))
                .peek((method) -> StackTracing.addToRootSet(RefinedClass.signature(method)))
                .peek(this::addWork)
                // group by class
                .map(Method::getDeclaringClass)
                .distinct()
                // trigger transform
                .forEach(this::trigger);
    }

    public void detach() {
        try {
            this.channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("fail to close channel", e);
        } finally {
            try {
                this.work.keySet()
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

        try {
            return workForClass(classBeingRedefined, classfileBuffer);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
            return null;
        }
    }

    protected Stream<Class<?>> implemented(Class<?> clazz) {
        return this.cache_classes.values().stream()
                .flatMap(Set::stream)
                .filter(clazz::isAssignableFrom)
                .filter((implemented) -> !implemented.equals(clazz))
                ;
    }

    protected byte[] workForClass(Class<?> clazz, byte[] classfileBuffer) {
        printer().println(String.format(
                "trigger of class:%s",
                clazz
        ));

        if (this.isClosed()) {
            // closed,revert changes
            return Optional.ofNullable(work.get(clazz))
                    .map((entry) -> {
                        entry.forEach((transformed_method, ignore) ->
                                System.out.println(
                                        String.format("reverting method: %s", transformed_method)
                                )
                        );

                        return new RefinedClass(clazz).revert().bytecode();
                    })
                    .orElse(null);
        }

        return Optional.ofNullable(work.get(clazz))
                .map((methods) -> {
                    RefinedClass refined = new RefinedClass(new ByteArrayInputStream(classfileBuffer));

                    methods.entrySet().stream()
                            // find works not yet be done
                            .filter((entry) -> !entry.getValue())
                            .map(Map.Entry::getKey)
                            .peek(this::markWorkDone)
                            // should transform?
                            .filter(this::accept)
                            //.peek((method)->printer().println(String.format("profiling method:%s",method)))
                            // modify byte code
                            .map(refined::profileMethod)
                            // expand to instructions
                            .flatMap((method) -> Arrays.stream(
                                    Optional.ofNullable(method.instructions)
                                            .orElseGet(InsnList::new)
                                            .toArray()
                            ))
                            // filter method invocation
                            .filter((node) -> node instanceof MethodInsnNode)
                            .map(MethodInsnNode.class::cast)
                            // cast to method
                            .flatMap(this::transform)
                            .collect(Collectors.groupingBy(
                                    Method::getDeclaringClass,
                                    Collectors.collectingAndThen(
                                            Collectors.toSet(),
                                            (class_methods) -> class_methods.stream().map((method) -> String.format(
                                                    "%s%s",
                                                    method.getName(),
                                                    Type.getMethodDescriptor(method)
                                            )).collect(Collectors.toSet())
                                    )
                            ))
                            .entrySet().stream()
                            .flatMap((entry) ->
                                    Stream.concat(
                                            Stream.of(entry.getKey()),
                                            this.implemented(entry.getKey())
                                    )
                                            .flatMap(Lookups::collectMethodsWithByteCode)
                                            .filter((method) -> entry.getValue().contains(String.format(
                                                    "%s%s",
                                                    method.getName(),
                                                    Type.getMethodDescriptor(method)
                                            )))
                                            .filter(this::maybeAddWork)
                                            .map(Method::getDeclaringClass)
                            )
                            .distinct()
                            .forEach(this::trigger)
                    ;

                    return refined.bytecode();
                })
                .orElse(null);
    }

    protected Stream<Method> transform(MethodInsnNode node) {
        return this.cache_classes.getOrDefault(
                node.owner.replace("/", "."),
                Collections.emptySet()
        ).stream()
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter((method) -> method.getName().equals(node.name))
                .filter((method) -> Type.getMethodDescriptor(method).equals(node.desc))
                ;
    }

    protected boolean maybeAddWork(Method method) {
        return work.computeIfAbsent(
                method.getDeclaringClass(),
                (ignore) -> new ConcurrentHashMap<>()
        ).compute(method, (ignore_key, done) -> Optional.ofNullable(done).orElse(false));
    }

    protected void addWork(Method method) {
        work.computeIfAbsent(
                method.getDeclaringClass(),
                (ignore) -> new ConcurrentHashMap<>()
        ).compute(method, (ignore_key, ignore_value) -> false);
    }

    protected void markWorkDone(Method method) {
        work.computeIfAbsent(
                method.getDeclaringClass(),
                (ignore) -> new ConcurrentHashMap<>()
        ).compute(method, (ignore_key, ignore_value) -> true);
    }

    protected void trigger(Class<?> clazz) {
        try {
            printer().println(String.format("call transform class:%s loader:%s", clazz, clazz.getClassLoader()));
            instrumentation.retransformClasses(clazz);
        } catch (UnmodifiableClassException e) {
            throw new RuntimeException(String.format(
                    "fail to transform class:%s",
                    clazz
            ), e);
        }
    }

    protected boolean accept(Method method) {
        int skip_class = Modifier.NATIVE | Modifier.ABSTRACT;
        if ((method.getModifiers() & skip_class) != 0) {
            printer().println(String.format("skip transform method without bytecode: %s", method));
            return false;
        } else if (method.isBridge()) {
            printer().println(String.format("skip transform bridge method: %s", method));
            return false;
        } else if (whitelisted_signature.contains(RefinedClass.signature(method))) {
            printer().println(String.format("skip whitelisted method: %s", method));
            return false;
        }

        return true;
    }
}
