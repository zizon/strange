package com.sf.doctor;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Stack;

public class TestRefinedClass {

    @Test
    public void testProfileMethod() {
        try {
            RefinedClassLoader classloader = new RefinedClassLoader();

            RefinedClass refined = RefinedClass.loadLoadableClass(RefinedClass.class);
            System.out.println("-----origin: " + refined.bytecode().length);
            //refined.print(System.out);

            refined.annotate();
            System.out.println("-----annotate: " + refined.bytecode().length);
            //refined.print(System.out);

            refined.revert();
            System.out.println("-----revert: " + refined.bytecode().length);
            //refined.print(System.out);
            byte[] bytecode = refined.bytecode();
            try (OutputStream stream = new FileOutputStream(new File("orign.class"))) {
                stream.write(bytecode);
            }

            bytecode = refined.bytecode();
            refined.print(System.out);
            try (OutputStream stream = new FileOutputStream(new File("generated.class"))) {
                stream.write(bytecode);
            }
            Object object = classloader.defineClass(bytecode).getConstructor(byte[].class)
                    .newInstance(bytecode);
            object.getClass().getMethod("test").invoke(object);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static class BaseClass {
        public void baseMethod() {
        }
    }

    public static class IntermediaClass extends BaseClass {

    }

    public static class ChildClass extends IntermediaClass {
    }

    @Test
    public void testSuperMethod() {
        RefinedClass refined = RefinedClass.loadLoadableClass(ChildClass.class);
        //refined.print(System.out);

        Arrays.stream(ChildClass.class.getMethods())
                .forEach(System.out::println);

        Arrays.stream(ChildClass.class.getDeclaredMethods())
                .forEach(System.out::println);
    }

    public static class StaticClass {
        public static Stack<Boolean> STACK = new Stack<>();

        public static void push(boolean flags) {
            STACK.push(flags);
        }

        public static void print() {
            System.out.println(STACK.size());
        }
    }

    @Test
    public void testStaticMethod() {
        StaticClass.push(true);
        StaticClass.print();

        Class<?> test = new RefinedClassLoader().defineClass(RefinedClass.loadLoadableClass(StaticClass.class).bytecode());
        try {
            test.getDeclaredMethod("print").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static class Parent {
        public void run() throws IOException {
            throw new IOException();
        }

        public void noException() {
            try {
                this.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class Child {

        public void run() {
            new Parent().noException();
        }
    }

    @Test
    public void testRuntimeException() {
        //RefinedClass.loadLoadableClass(Parent.class).print(System.out);
        Arrays.stream(Parent.class.getDeclaredMethods())
                .forEach((method) -> System.out.println(method.toString()));

    }

}
