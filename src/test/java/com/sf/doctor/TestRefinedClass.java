package com.sf.doctor;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class TestRefinedClass {

    @Test
    public void testProfileMethod() {
        try {
            RefinedClass origin = RefinedClass.loadLoadableClass(RefinedClass.class);
            MethodLookup.findMethod(origin.getClass(), "profileMethod")
                    .flatMap(origin::profileMethod)
                    .count();

            origin.print(new PrintWriter(System.out, true));
            Object object = new RefinedClassLoader().defineClass(origin.bytecode()).
                    getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(origin.bytecode()));
            //refiend.print(new PrintWriter(System.out, true));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static interface I {
    }


    public static class A implements I {
        protected Runnable t;

        public void test() {
            System.out.println("ok");
            t = this::test;
        }
    }

    @Test
    public void testClassloader() throws InstantiationException {
        try {
            RefinedClass origin = RefinedClass.loadLoadableClass(A.class);
            origin.print(new PrintWriter(System.out, true));

            MethodLookup.findMethod(origin.getClass(), "test")
                    .flatMap(origin::profileMethod)
                    .count();
            I a = (I) new RefinedClassLoader().defineClass(origin.bytecode()).newInstance();

            origin.print(new PrintWriter(System.out, true));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPrintClass() {
        RefinedClass clazz = RefinedClass.loadLoadableClass(ProfileTransformer.class);
        clazz.print(new PrintWriter(System.out, true));
    }

}
