package com.sf.doctor;

import org.objectweb.asm.Type;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Agent implements ClassFileTransformer, AutoCloseable {

    protected static MethodHandle ENTER_HANDLE;
    protected static MethodHandle LEAVE_HANDLE;
    protected static MethodHandle PRINT_HANDLE;

    protected boolean closed;
    protected PrintWriter writer;
    protected final Instrumentation instrumentation;
    protected final ClassLoader loader;
    protected final ConcurrentMap<Class<?>, NavigableSet<Method>> touched_methods;

    public static void enter(String signature) {
        guardConsume(ENTER_HANDLE, signature);
    }

    public static void leave(String signature) {
        guardConsume(LEAVE_HANDLE, signature);
    }

    public static void print(PrintWriter writer) {
        guardConsume(PRINT_HANDLE, writer);
    }

    protected static <T> void guardConsume(MethodHandle handle, T input) {
        Optional.ofNullable(handle)
                .ifPresent((non_null) -> {
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(non_null.getClass().getClassLoader());
                    try {
                        non_null.invoke(input);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    } finally {
                        Thread.currentThread().setContextClassLoader(loader);
                    }
                });
        return;
    }

    public Agent(ClassLoader loader,
                 Instrumentation instrumentation,
                 Map<String, String> arguments) {
        this.writer = null;
        this.loader = loader;
        this.instrumentation = instrumentation;
        this.touched_methods = new ConcurrentHashMap<>();

        int port = Integer.parseInt(arguments.get("port"));
        String host = arguments.get("host");
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(host, port));
            channel.shutdownInput();
            this.writer = new PrintWriter(Channels.newWriter(channel, "utf8"), true);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("fail to conenct to %s:%s", host, port), e);
        }
    }

    public void println(String message) {
        Optional.ofNullable(writer)
                .ifPresent((writer) -> writer.println(message));
    }

    public void run(Map<String, String> arguments) {
        this.println(String.format("connected%n"));
        arguments.forEach((key, value) -> this.println(String.format("key:%s value:%s%n", key, value)));
        this.println(String.format("instrumentation:%s%n", instrumentation));

        this.println(String.format("attach transformer:%s%n", this));
        instrumentation.addTransformer(this, true);

        String[] target = arguments.get("entry").split("#");
        if (target.length != 2) {
            throw new IllegalArgumentException("target should be in form of class#$method");
        }

        String class_name = target[0];
        String method_name = target[1];

        // refine
        findClassByName(class_name)
                .forEach((clazz) -> MethodLookup.findMethod(clazz, method_name)
                        .forEach(this::transformMethod)
                );
    }

    protected void transformMethod(Method method) {
        findClassByName(method.getDeclaringClass().getName())
                .peek((clazz) -> touched_methods.computeIfAbsent(
                        clazz,
                        (ignore) -> new ConcurrentSkipListSet<>(Comparator.comparing(Method::toString))
                ).add(method))
                .forEach((clazz) -> {
                    try {
                        instrumentation.retransformClasses(clazz);
                    } catch (UnmodifiableClassException e) {
                        throw new RuntimeException("fail to transforme method:" + method, e);
                    }
                });
    }

    protected Stream<Class> findClassByName(String name) {
        return Arrays.stream(instrumentation.getAllLoadedClasses())
                .filter((clazz) -> clazz.getName().equals(name));
    }

    @Override
    public void close() throws Exception {
        Agent.ENTER_HANDLE = null;
        Agent.LEAVE_HANDLE = null;
        Agent.PRINT_HANDLE = null;

        // reset
        instrumentation.retransformClasses(
                touched_methods.keySet()
                        .toArray(new Class[0])
        );
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
        if (!closed) {
            Optional.ofNullable(touched_methods.get(classBeingRedefined))
                    .ifPresent((methods) -> methods.stream()
                            .peek((method) -> println(String.format(
                                    "refine class:%s of method:%s",
                                    method.getDeclaringClass(),
                                    method)
                            ))
                            .flatMap(refined::profileMethod)
                            .peek((method) -> println(String.format(
                                    "inline profile class:%s of method:%s",
                                    method,
                                    method
                            )))
                    );
        } else {
            println(String.format("revert class:%s", classBeingRedefined));
            refined.revert();
        }

        return refined.bytecode();
    }

    protected static void setupAgentStub() {
        try {
            Class<?> stack_tracing_class = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(StackTracing.class.getName());

            Stream.of(
                    new AbstractMap.SimpleImmutableEntry<String, Class<?>>("enter", String.class),
                    new AbstractMap.SimpleImmutableEntry<String, Class<?>>("leave", String.class),
                    new AbstractMap.SimpleImmutableEntry<String, Class<?>>("print", PrintWriter.class)
            ).forEach((entry) -> {
                try {
                    Method method = stack_tracing_class.getDeclaredMethod(entry.getKey(), entry.getValue());
                    MethodHandle handle = MethodHandles.lookup().unreflect(method);

                    Field handle_field = Agent.class.getDeclaredField(String.format(
                            "%s_HANDLE",
                            entry.getKey().toUpperCase())
                    );
                    handle_field.setAccessible(true);
                    handle_field.set(null, handle);
                } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
                    throw new RuntimeException("fail to get method:" + entry, e);
                }
            });
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("fail to setup agent stub", e);
        }
    }

    public static void agentmain(String agent_args, Instrumentation instrumentation) {
        System.out.println("agent loaded");
        Map<String, String> arguments = Arrays.stream(agent_args.split(";"))
                .collect(Collectors.toMap(
                        (tuple) -> tuple.split("=")[0],
                        (tuple) -> tuple.split("=")[1]
                ));

        try (AgentClassLoader classloader = new AgentClassLoader(arguments.get("jar"))) {
            setupAgentStub();

            // use new class?
            try (AutoCloseable new_agent = (AutoCloseable) classloader.loadClass(Agent.class.getName())
                    .getConstructor(ClassLoader.class, Instrumentation.class, Map.class)
                    .newInstance(classloader, instrumentation, arguments)) {

                MethodLookup.findMethodHandle(new_agent.getClass(), "run")
                        .findFirst()
                        .ifPresent((handle) ->
                                new Dynamic().call(handle, new_agent, arguments)
                        );
            }
        } catch (Throwable e) {
            new RuntimeException("unexpected exception", e).printStackTrace();
        } finally {
            System.out.println("agent unload");
            System.out.flush();
        }
    }
}
