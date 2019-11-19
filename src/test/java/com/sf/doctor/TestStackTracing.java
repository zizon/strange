package com.sf.doctor;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Stack;

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

}
