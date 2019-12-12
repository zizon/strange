package com.sf.doctor;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestStackTracing {

    @Test
    public void test() {
        try {
            Method method = TestStackTracing.class.getMethod("test");
            String signagure = Type.getType(method.getDeclaringClass()) + method.getName() + Type.getType(method);
            StackTracing.addToRootSet(signagure);
            StackTracing.enter(signagure);
            StackTracing.enter("first");
            StackTracing.enter("second");
            StackTracing.enter("second");
            StackTracing.leave("second");
            StackTracing.leave("second");
            StackTracing.leave("first");
            StackTracing.leave(signagure);

            StackTracing.enter("second");
            StackTracing.leave("second");
            StackTracing.print(new PrintWriter(System.out, true));


        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public void test2() {
        try {
            for (Method method : java.lang.invoke.LambdaMetafactory.class.getMethods()) {
                System.out.println(method);
            }


            java.lang.invoke.LambdaMetafactory.class.getMethod("metafactory", MethodHandles.Lookup.class,
                    String.class,
                    MethodType.class,
                    MethodType.class,
                    MethodHandle.class,
                    MethodType.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        LockSupport.park();
    }

    @Test
    public void testRener() {
        // called:2 average:83,147.50 ns max:110,026.00 ns min:56,269.00 ns
        StackTracing.addToRootSet("org/apache/spark/deploy/history/HistoryServer#getApplicationList;()Lscala/collection/Iterator;");
        StackTracing.addToRootSet("org/apache/spark/deploy/history/HistoryServer#getApplicationList");
        IntStream.range(0,10).forEach((drop)->{
            StackTracing.enter("org/apache/spark/deploy/history/HistoryServer#getApplicationList;()Lscala/collection/Iterator;");
            StackTracing.leave("org/apache/spark/deploy/history/HistoryServer#getApplicationList;()Lscala/collection/Iterator;");
        });

        IntStream.range(0,10).forEach((drop)->{
            StackTracing.enter("org/apache/spark/deploy/history/HistoryServer#getApplicationList");
            StackTracing.leave("org/apache/spark/deploy/history/HistoryServer#getApplicationList");
        });

        StackTracing.print(new PrintWriter(System.out));

        String method = "org/apache/spark/deploy/history/HistoryServer#getApplicationList;()Lscala/collection/Iterator;";
        double max = 110026.00;
        double min = 56269.00;
        double avg = 83147.50;
        Map<String, double[]> PROFILE_STAT = new ConcurrentHashMap<>();
        PROFILE_STAT.put(method, new double[]{2, avg, min, max});
        PROFILE_STAT.put("hello", new double[]{2, 123, min, max});
        PrintWriter writer = new PrintWriter(System.out, true);

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
                        .toArray(Object[]::new)
        );

        Stream.concat(
                rows.stream()
                        .map((row) -> String.format(row_format, Arrays.stream(row).toArray()))
                        .flatMap((row) -> Stream.of(bar, row)),
                Stream.of(bar)
        ).forEach(writer::println);
    }

}
