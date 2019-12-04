package com.sf.doctor;

import org.junit.Test;

import java.util.stream.IntStream;

public class TestProfileTransformer {

    @Test
    public void test(){
        IntStream.range(0,300)
                .flatMap((i)->IntStream.range(0,i))
                .peek((string)->System.out.println(String.format("peek %s", string)))
                .mapToObj(Integer::toString)
                .distinct()
                .forEach((string)->System.out.println(String.format("foreach %s", string)));
    }
}
