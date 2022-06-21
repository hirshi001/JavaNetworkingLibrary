package com.hirshi001.javanetworking;

import java.net.Socket;

@FunctionalInterface
public interface SocketOptionConsumer<T> {
    void accept(Socket socket, T value) throws Exception;
}
