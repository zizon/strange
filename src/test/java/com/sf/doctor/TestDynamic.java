package com.sf.doctor;

import org.junit.Test;

public class TestDynamic {

    public static class TestClass {
        public void run(String a, int b) {
        }
    }

    @Test
    public void test() {
        MethodLookup.findMethodHandle(TestClass.class, "run")
                .forEach((handle) -> new Dynamic().call(handle, new TestClass(), "ok", 1));
    }
}
