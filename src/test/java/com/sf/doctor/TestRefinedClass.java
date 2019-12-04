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
    public void testPrintClass() {
        RefinedClass clazz = RefinedClass.loadLoadableClass(ProfileTransformer.class);
        clazz.print(new PrintWriter(System.out, true));
    }

}
