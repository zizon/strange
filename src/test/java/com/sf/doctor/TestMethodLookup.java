package com.sf.doctor;

import org.junit.Test;

import java.util.function.Consumer;

public class TestMethodLookup {

    public static class TestClass implements Consumer<String> {

        @Override
        public void accept(String s) {

        }

        public Consumer<String> andThen(Consumer<? super String> after) {
            return null;
        }

    }


    @Test
    public void test() {
        MethodLookup.findMethod(TestClass.class, "andThen")
                .forEach(System.out::println);
    }
}
