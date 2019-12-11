package com.sf.doctor;

import org.junit.Test;
import org.objectweb.asm.ClassReader;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class TestProfileTransformer {

    public interface DefaultRunnable extends Runnable {
        default void run() {
            System.out.println("default run");
        }

        ;
    }


    public static abstract class TestClass {

        public void run() {
            System.out.println("class run");
        }

        ;


    }

    public static class TestClass1 extends TestClass implements DefaultRunnable {

    }

    @Test
    public void test() {
        try {
            new TestClass1().run();
            Arrays.stream(LinkedHashMap.class.getMethods())
                    .forEach(System.out::println);

            //ThreadLocal.class.getMethod("createMap", Thread.class, Object.class);

            ClassReader reader = new ClassReader(ThreadLocal.class.getName());
            //reader.accept(new TraceClassVisitor(new PrintWriter(System.out, true)), 0);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
