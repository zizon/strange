package com.sf.doctor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class AgentChannel implements AutoCloseable {

    protected final Socket socket;
    protected final PrintWriter printer;

    public AgentChannel(String host, int port) {
        try {
            this.socket = this.connect(new InetSocketAddress(host, port));
            this.printer = new PrintWriter(this.socket.getOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("fail to connect:%s %s", host, port), e);
        }
    }

    public void println(String message) {
        this.printer.println(message);
    }

    public boolean isClosed() {
        return this.socket.isClosed();
    }

    public boolean ping(int timeout) {
        return !this.printer().checkError();
    }

    public PrintWriter printer() {
        return this.printer;
    }

    protected Socket connect(InetSocketAddress address) throws IOException {
        Socket socket = new Socket();
        socket.connect(address);
        socket.shutdownInput();

        return socket;
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }
}
