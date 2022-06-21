package com.hirshi001.javanetworking;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.client.JavaClient;
import com.hirshi001.javanetworking.server.JavaServer;
import com.hirshi001.networking.network.NetworkFactory;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.networkdata.NetworkData;

import java.util.concurrent.ScheduledExecutorService;

public class JavaNetworkFactory implements NetworkFactory {

    private ScheduledExecutorService executor;

    public JavaNetworkFactory(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public Server createServer(NetworkData networkData, BufferFactory bufferFactory, int port) {
        return new JavaServer(executor, networkData, bufferFactory, port);
    }

    @Override
    public Client createClient(NetworkData networkData, BufferFactory bufferFactory, String host, int port) {
        return new JavaClient(networkData, bufferFactory, host, port, executor);
    }
}
