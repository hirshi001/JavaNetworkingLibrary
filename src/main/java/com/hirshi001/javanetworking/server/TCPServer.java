package com.hirshi001.javanetworking.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class TCPServer {

    private final int port;
    ServerSocket serverSocket;

    public TCPServer(int port) {
        this.port = port;
    }

    public void start(Consumer<Socket> callback) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            Socket socket = serverSocket.accept();
            callback.accept(socket);
        }
    }
}
