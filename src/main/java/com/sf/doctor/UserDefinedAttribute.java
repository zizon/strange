package com.sf.doctor;

import org.objectweb.asm.Attribute;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

class UserDefinedAttribute extends Attribute {
    static final MethodHandle CONTENT_READER;
    static final MethodHandle CONTENT_WRITER;

    static {
        try {
            Field field = Attribute.class.getDeclaredField("content");
            field.setAccessible(true);
            CONTENT_READER = MethodHandles.lookup().unreflectGetter(field);
            CONTENT_WRITER = MethodHandles.lookup().unreflectSetter(field);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("fail to find content accessor", e);
        }
    }

    public UserDefinedAttribute(String type, byte[] content) {
        super(type);
        content(content);
    }

    public static byte[] content(Attribute attribute) {
        try {
            return (byte[]) CONTENT_READER.invoke(attribute);
        } catch (Throwable throwable) {
            throw new RuntimeException("fail to get content");
        }
    }

    public byte[] content() {
        return content(this);
    }

    public void content(byte[] content) {
        try {
            CONTENT_WRITER.invoke(this, content);
        } catch (Throwable throwable) {
            throw new RuntimeException("fail to get content");
        }
    }
}
