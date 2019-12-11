package com.sf.doctor;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Agent implements Supplier<Class<?>>, Runnable {

    protected final Runnable cleanup;
    protected final ProfileTransformer transformer;
    protected final String clazz_name;
    protected final String method_name;

    public Agent(Instrumentation instrumentation,
                 Map<String, String> arguments,
                 Runnable cleanup) {
        // setup transformer
        int port = Integer.parseInt(arguments.get("port"));
        String host = arguments.get("host");
        this.transformer = new ProfileTransformer(instrumentation, new AgentChannel(host, port));

        this.transformer.printer().println(String.format("connect to %s:%s", host, port));

        // print config
        arguments.forEach((key, value) -> this.transformer.printer().println(String.format("key:%s value:%s", key, value)));

        // setup cleanup
        this.cleanup = cleanup;

        // find profile entry
        String[] target = arguments.get("entry").split("#");
        if (target.length != 2) {
            throw new IllegalArgumentException("target should be in form of class#$method");
        }

        // attach
        this.clazz_name = target[0];
        this.method_name = target[1];
    }

    protected void internalRun() {
        while (!transformer.isClosed()) {
            StackTracing.print(this.transformer.printer());

            // sleep 5s
            if (transformer.health(TimeUnit.SECONDS.toMillis(5))) {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
                continue;
            }

            break;
        }
    }

    @Override
    public void run() {
        try {
            // refine
            this.transformer.attach(this.clazz_name, this.method_name);

            this.internalRun();
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
        } finally {
            Stream.<Optional<Runnable>>of(
                    Optional.of(this.transformer::detach),
                    Optional.ofNullable(this.cleanup)
            ).filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach((runnable) -> {
                        try {
                            runnable.run();
                        } catch (Throwable throwable) {
                            throwable.printStackTrace(System.out);
                        }
                    });
        }
    }

    @Override
    public Class<?> get() {
        return StackTracing.class;
    }

    @SuppressWarnings("unchecked")
    public static void agentmain(String agent_args, Instrumentation instrumentation) {
        System.out.println("agent loaded");
        Map<String, String> arguments = Arrays.stream(agent_args.split(";"))
                .collect(Collectors.toMap(
                        (tuple) -> tuple.split("=")[0],
                        (tuple) -> tuple.split("=")[1]
                ));

        Runnable cleanup = null;
        try {
            AgentClassLoader classloader = new AgentClassLoader(arguments.get("jar"));

            cleanup = () ->
                    Stream.<Runnable>of(
                            Bridge::cleanup,
                            Bridge::unstub,
                            //TODO
                            //classloader::close,
                            () -> System.out.println("agent unload")
                    ).forEach((runnable) -> {
                        try {
                            runnable.run();
                        } catch (Throwable throwable) {
                            throwable.printStackTrace(System.out);
                        }
                    });
            // create agent in seperate classloader
            Object new_agent = classloader.loadClass(Agent.class.getName())
                    .getConstructor(Instrumentation.class, Map.class, Runnable.class)
                    .newInstance(instrumentation, arguments, cleanup);

            // make handler of agent in host classloader use
            // stacktracing methods from agent classloader
            Bridge.stub(((Supplier<Class<?>>) new_agent).get());

            // kick start
            new Thread((Runnable) new_agent, "strange-agent").start();
        } catch (Throwable e) {
            new RuntimeException("unexpected exception", e).printStackTrace(System.out);
            Optional.ofNullable(cleanup).ifPresent(Runnable::run);
        }
    }
}
