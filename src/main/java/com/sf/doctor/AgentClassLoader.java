package com.sf.doctor;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentClassLoader extends URLClassLoader implements AutoCloseable {

    public AgentClassLoader(String jar) throws Throwable {
        super(
                new URL[]{
                        new File(jar).toURI().toURL()
                },
                null
        );
    }

    public void close() {
        try {
            super.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
