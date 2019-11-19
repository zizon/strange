package com.sf.doctor;

public class RefinedClassLoader extends ClassLoader {


    public RefinedClassLoader() {
    }

    public Class<?> defineClass( byte[] bytecode) {
        return super.defineClass(null, bytecode, 0,bytecode.length);
    }
}
