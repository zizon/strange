package com.sf.doctor;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

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

    public void test2(){
        try {
            for(Method method: java.lang.invoke.LambdaMetafactory.class.getMethods()){
                System.out.println(method);
            }


            java.lang.invoke.LambdaMetafactory.class.getMethod("metafactory",MethodHandles.Lookup.class,
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

}
