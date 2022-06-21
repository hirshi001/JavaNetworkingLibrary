package com.hirshi001.javanetworking;

import java.net.DatagramSocket;

@FunctionalInterface
public interface DatagramSocketOptionConsumer<T> {
    void accept(DatagramSocket socket, T value) throws Exception;
}
