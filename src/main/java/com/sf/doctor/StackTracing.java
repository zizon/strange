package com.sf.doctor;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StackTracing {

    protected static Map<Long, ConcurrentMap<String, Stack<Long>>> PER_THREAD_METHOD_ENTRY_TIME = new ConcurrentHashMap<>();
    protected static Map<String, double[]> PROFILE_STAT = new ConcurrentHashMap<>();
    protected static Set<String> ROOT_SET_METHOD = new ConcurrentSkipListSet<>();

    public static void enter(String signature) {
        try {
            unsafeEnter(signature);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
        }
    }

    public static void leave(String signature) {
        try {
            unsafeLeave(signature);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
        }
    }

    public static void print(PrintWriter writer) {
        try {
            unsafePrint(writer);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.out);
        }
    }

    public static boolean shouldTrace(String signature) {
        return isTargetInCallStack() || ROOT_SET_METHOD.contains(signature);
    }

    public static void addToRootSet(String signature) {
        ROOT_SET_METHOD.add(signature);
    }

    public static boolean isTargetInCallStack() {
        return Optional.ofNullable(PER_THREAD_METHOD_ENTRY_TIME.get(Thread.currentThread().getId()))
                // find current thread tracing methods,
                // and then lookup if any TARGET_SIGNATURE in tracing
                .map((tracing) -> tracing.entrySet().stream()
                        .anyMatch(
                                (entry) -> !entry.getValue().isEmpty()
                                        && ROOT_SET_METHOD.contains(entry.getKey())
                        )
                )
                .orElse(false);
    }

    protected static void unsafeEnter(String signature) {
        if (!shouldTrace(signature)) {
            return;
        }

        // mark timestamp
        long tid = Thread.currentThread().getId();
        Optional.ofNullable(PER_THREAD_METHOD_ENTRY_TIME)
                .ifPresent((entry_times) -> entry_times
                        // create thread local method stack map for time keeping
                        .computeIfAbsent(
                                tid,
                                (ignore) -> new ConcurrentHashMap<>()
                        )
                        // create method invoke time stack
                        .computeIfAbsent(signature, (ignore) -> new Stack<>())
                        // mark start time
                        .push(System.nanoTime())
                )
        ;
    }

    protected static void unsafeLeave(String signature) {
        if (!shouldTrace(signature)) {
            return;
        }

        long now = System.nanoTime();
        long tid = Thread.currentThread().getId();
        Optional.ofNullable(PER_THREAD_METHOD_ENTRY_TIME)
                // current tread time stacks.
                // in  case a late transform,leave may invoke with no thread stack map generated , yet.
                // so be aware
                .flatMap((entry_times) -> Optional.ofNullable(entry_times.get(tid)))
                // find method time stack.
                // take care of situation as above.
                .flatMap((tracing_method) -> Optional.ofNullable(tracing_method.get(signature)))
                // check empty.
                // it could be happened when a time racing repeated attach
                // or a late attach.
                .filter((stack) -> !stack.isEmpty())
                // pop the start time,
                // it may not be correct as situation refer above
                .map(Stack::pop)
                .ifPresent((start) -> {
                    long cost = now - start;

                    // eliminate the obvious malformed tracing.
                    if (cost < 0) {
                        return;
                    }

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

    protected static void unsafePrint(PrintWriter writer) {
        Map<String, double[]> stat = PROFILE_STAT;
        if (stat == null) {
            return;
        }

        List<String[]> rows = Stream.concat(
                Stream.<String[]>of(new String[]{
                        "Average (ns)",
                        "Maximum (ns)",
                        "Minimum (ns)",
                        "Invocation",
                        "Method"
                }),
                PROFILE_STAT.entrySet().stream()
                        .sorted(Comparator.comparingDouble((entry) -> -entry.getValue()[1]))
                        .map((entry) -> new String[]{
                                String.format("%,.2f", entry.getValue()[1]),
                                String.format("%,.2f", entry.getValue()[2]),
                                String.format("%,.2f", entry.getValue()[3]),
                                String.format("%,.0f", entry.getValue()[0]),
                                String.format("%-40s", entry.getKey()),
                        })

        ).collect(Collectors.toList());

        int[] column_width = IntStream.range(0, 5)
                .map((column) -> rows.stream().
                        map((row) -> row[column])
                        .map(String::length)
                        .max(Integer::compareTo)
                        .orElse(0)
                )
                .toArray();

        // format row
        String row_format = String.format(
                "| %%%ds | %%%ds | %%%ds | %%%ds | %%-%ds |",
                column_width[0],
                column_width[1],
                column_width[2],
                column_width[3],
                column_width[4]
        );
        String bar_format = String.format(
                "+-%%%ds-+-%%%ds-+-%%%ds-+-%%%ds-+-%%%ds-+",
                column_width[0],
                column_width[1],
                column_width[2],
                column_width[3],
                column_width[4]
        );

        String bar = String.format(
                bar_format,
                Arrays.stream(column_width)
                        .mapToObj((width) -> IntStream.range(0, width).mapToObj((ignore) -> "-").collect(Collectors.joining()))
                        .toArray()
        );

        Stream.concat(
                rows.stream()
                        .map((row) -> String.format(row_format, Arrays.stream(row).toArray()))
                        .flatMap((row) -> Stream.of(bar, row)),
                Stream.of(bar)
        ).forEach(writer::println);
        writer.flush();
    }

    public static void cleanup() {
        System.out.println("clean up tracing stack");
        PER_THREAD_METHOD_ENTRY_TIME.clear();
        PER_THREAD_METHOD_ENTRY_TIME = null;

        PROFILE_STAT.clear();
        PROFILE_STAT = null;

        ROOT_SET_METHOD.clear();
        ROOT_SET_METHOD = null;
    }
}
