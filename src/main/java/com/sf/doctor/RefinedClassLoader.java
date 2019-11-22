package com.sf.doctor;

import java.util.concurrent.Callable;

public class RefinedClassLoader extends ClassLoader {

    public Class<?> defineClass(byte[] bytecode) {
        return super.defineClass(null, bytecode, 0, bytecode.length);
    }
}
