package com.sf.doctor;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class TestRefineMethod {

    @Test
    public void test(){
        ClassReader reader= new ClassReader( RefinedClass.loadLoadableClass(TestRefineMethod.class).bytecode());
        ClassNode clazz = new ClassNode();
        reader.accept(clazz,0);

        /*
        second called:2 average:2,007,962.00 ns max:4,003,304.00 ns min:12,620.00 ns
first called:1 average:4,097,047.00 ns max:4,097,047.00 ns min:4,097,047.00 ns
Lcom/sf/doctor/TestStackTracing;test()V called:1 average:11,675,501.00 ns max:11,675,501.00 ns min:11,675,501.00 ns
         */

        clazz.methods.stream()
                .forEach((method)->{

                    System.out.println(method.name);
                });
    }

}
