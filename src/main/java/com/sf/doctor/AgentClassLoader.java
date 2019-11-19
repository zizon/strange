package com.sf.doctor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentClassLoader extends URLClassLoader implements AutoCloseable {
    protected final ClassLoader origin;

    public AgentClassLoader(String jar) throws Throwable {
        super(
                new URL[]{
                        new File(jar).toURI().toURL()
                },
                null
        );

        origin = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this);
    }

    public void close() throws IOException {
        super.close();
        Thread.currentThread().setContextClassLoader(origin);
    }
}
