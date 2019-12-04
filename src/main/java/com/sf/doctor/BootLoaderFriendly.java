package com.sf.doctor;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Stream;

public class BootLoaderFriendly {

    protected static ClassLoader setupToolsJar() {
        try {
            Class.forName("com.sun.tools.javac.Main");
            return Thread.currentThread().getContextClassLoader();
        } catch (ClassNotFoundException e) {
            return new URLClassLoader(
                    Stream.concat(
                            Arrays.stream(
                                    System.getProperty("java.class.path").split(File.pathSeparator)
                            ).map(File::new),
                            Stream.of(
                                    new File(
                                            new File(System.getProperty("java.home")).getParent(),
                                            "lib/tools.jar"
                                    )
                            )
                    ).map((file) -> {
                        try {
                            return file.toURI().toURL();
                        } catch (MalformedURLException exception) {
                            throw new RuntimeException(String.format("fail to parse file:%s", file), exception);
                        }
                    }).toArray(URL[]::new),

                    null
            );
        }
    }

    public static void main(String[] args) {
        try {
            setupToolsJar()
                    .loadClass("com.sf.doctor.Strange")
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (Throwable e) {
            throw new RuntimeException("fail to start Strange", e);
        }
    }
}
