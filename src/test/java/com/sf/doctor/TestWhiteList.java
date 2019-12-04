package com.sf.doctor;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.jar.JarFile;

public class TestWhiteList {

    @Test
    public void test() {
        try (JarFile file = new JarFile(
                new File(
                        Test.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                )
        )) {
            new WhiteList().fromJarFile(file)
                    .forEach((entry) -> {
                        System.out.println(entry.getName());
                    });
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStrip(){
        String message = " wait for  <0x000000008020b7f0> (a java.util.concurrent.ForkJoinPool)";
        System.out.println(message.replaceAll("<.[^<>]*>","<>"));
    }


}
