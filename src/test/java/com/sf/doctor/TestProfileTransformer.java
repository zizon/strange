package com.sf.doctor;

import org.junit.Test;

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
    }

    @Test
    public void test() {
        try {


/*
            Optional.ofNullable(node.methods)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .forEach((method) -> {

                        System.out.println(RefinedClass.signature(node, method));
                        Optional.ofNullable(method.attrs)
                                .orElseGet(Collections::emptyList)
                                .forEach((attr) -> System.out.println(attr));
                    });
*/
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
