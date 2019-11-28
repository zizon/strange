package com.sf.doctor;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class StackTracing {

    protected static Map<Long, ConcurrentMap<String, Stack<Long>>> PROFILE_ENTRY_CALL = new ConcurrentHashMap<>();
    protected static Map<String, double[]> PROFILE_STAT = new ConcurrentHashMap<>();
    protected static Set<String> ROOT_SET_METHOD = new ConcurrentSkipListSet<>();

    public static void enter(String signature) {
        System.out.println("enter:" + signature);
        if (!shouldTrace(signature)) {
            System.out.println("enter: not trace:" + signature);
            return;
        }

        // mark timestamp
        PROFILE_ENTRY_CALL.computeIfAbsent(
                Thread.currentThread().getId(),
                (ignore) -> new ConcurrentHashMap<>()
        ).computeIfAbsent(signature, (ignore) -> new Stack<>())
                .push(System.nanoTime());
    }

    public static void leave(String signature) {
        System.out.println("leave:" + signature);
        if (!shouldTrace(signature)) {
            System.out.println("leave: not trace:" + signature);
            return;
        }

        long now = System.nanoTime();

        Optional.ofNullable(
                PROFILE_ENTRY_CALL.get(Thread.currentThread()
                        .getId())
        ).map((tracing_method) -> tracing_method.get(signature))
                .map(Stack::pop)
                .ifPresent((start) -> {
                    long cost = now - start;
                    PROFILE_STAT.compute(signature, (ignore, stat) -> {
                        if (stat == null) {
                            stat = new double[]{
                                    0, // invocation count
                                    0, // mean
                                    0, // max
                                    -1 // min
                            };
                        }

                        stat[0] += 1;
                        stat[1] = ((stat[0] - 1) * stat[1] + cost) / stat[0];
                        stat[2] = Math.max(stat[2], cost);
                        stat[3] = stat[3] < 0 ? cost : Math.min(stat[3], cost);
                        return stat;
                    });
                });
    }

    public static boolean shouldTrace(String signature) {
        return isTargetInCallStack() || ROOT_SET_METHOD.contains(signature);
    }

    public static void addToRootSet(String signature) {
        ROOT_SET_METHOD.add(signature);
    }

    public static boolean isTargetInCallStack() {
        return Optional.ofNullable(PROFILE_ENTRY_CALL.get(Thread.currentThread().getId()))
                // find current thread tracing methods,
                // and then lookup if any TARGET_SIGNATURE in tracing
                .map((tracing) -> tracing.entrySet().stream()
                        .anyMatch(
                                (entry) -> !entry.getValue().isEmpty()
                                        && ROOT_SET_METHOD.contains(entry.getKey())
                        )
                ).orElse(false);
    }

    public static void print(PrintWriter writer) {
        writer.println(
                PROFILE_STAT.entrySet().stream()
                        .sorted(Comparator.comparingDouble((entry) -> entry.getValue()[1]))
                        .map((entry) -> String.format(
                                "%s called:%.0f average:%,.2f ns max:%,.2f ns min:%,.2f ns",
                                entry.getKey(),
                                entry.getValue()[0],
                                entry.getValue()[1],
                                entry.getValue()[2],
                                entry.getValue()[3]
                        ))
                        .collect(Collectors.joining("\n"))
        );
    }

    public static void close() {
        PROFILE_ENTRY_CALL.clear();
        PROFILE_STAT.clear();
        ROOT_SET_METHOD.clear();
    }
}
