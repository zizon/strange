package com.sf.doctor;

import org.junit.Test;

import java.lang.reflect.Modifier;

public class TestDynamic {

    public static interface TestClass {
        public <T> void run(String a, int b,T c);
    }

    @Test
    public void test() {
        MethodLookup.findMethod(TestClass.class, "run")
               .forEach((method)->System.out.println( method.isBridge()));
    }
}
