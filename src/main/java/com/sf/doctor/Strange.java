package com.sf.doctor;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Strange {

    protected String host;
    protected int port;
    protected ServerSocketChannel channel;

    public Strange() {
        try {
            host = InetAddress.getLocalHost().getHostName();
            channel = ServerSocketChannel.open();
            channel.configureBlocking(true);
            channel.bind(null);
            port = channel.socket().getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("server creation fail", e);
        }
    }

    public void processIO() {
        try {
            SocketChannel connection = channel.accept();
            channel.close();

            // tuning
            //connection.socket().setSoTimeout(5 * 1000);
            connection.shutdownOutput();
            connection.configureBlocking(true);

            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            WritableByteChannel console = Channels.newChannel(System.out);

            // use sockt input stream to make timeout effective
            ReadableByteChannel input = Channels.newChannel(connection.socket().getInputStream());
            while (input.read(buffer) != -1) {
                buffer.flip();
                console.write(buffer);
                buffer.compact();
                System.out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("fail io", e);
        }
    }

    public Strange attach(String[] args) {
        String pid = args[0];
        String agent = args[1];
        System.out.println(String.format("attatch to %s with agent:%s", pid, agent));

        try {
            System.out.println(String.format("listening at %s:%s", host, port));
            Map<String, String> parameters = new HashMap<>();
            parameters.put("host", host);
            parameters.put("port", Integer.toString(port));
            parameters.put("jar", agent);
            //parameters.put("entry","org.spark_project.jetty.util.thread.QueuedThreadPool$2#run");
            parameters.put("entry","org.apache.spark.deploy.history.HistoryServer#getApplicationInfoList");

            String arguments = parameters.entrySet().stream()
                    .map((entry) -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(";"));
            HotSpotVirtualMachine hotspot = (HotSpotVirtualMachine) VirtualMachine.attach(pid);
            hotspot.loadAgent(agent, arguments);
            System.out.println("attached");

            return this;
        } catch (Throwable throwable) {
            throw new RuntimeException("fail to attach vm", throwable);
        }
    }

    public static void main(String[] args) {
        new Strange().attach(args).processIO();
    }
}
