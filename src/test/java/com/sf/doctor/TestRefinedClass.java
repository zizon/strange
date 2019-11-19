package com.sf.doctor;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;

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

}
