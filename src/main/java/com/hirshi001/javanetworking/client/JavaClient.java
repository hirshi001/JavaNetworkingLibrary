package com.hirshi001.javanetworking.client;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelListener;
import com.hirshi001.networking.network.client.BaseClient;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.server.ServerListener;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestFuture;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;

public class JavaClient extends BaseClient {

    JavaClientChannel channel;
    ScheduledExecutorService executor;
    private final Object lock = new Object();

    public JavaClient(NetworkData networkData, BufferFactory bufferFactory, String host, int port, ScheduledExecutorService executor) {
        super(networkData, bufferFactory, host, port);
        this.executor = executor;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public ChannelListener getClientListenerHandler(){
        return clientListenerHandler;
    }

    @Override
    public RestFuture<?, Client> connectTCP() {
        return RestFuture.create((future, inputNull)->{
            createChannelIfNull();
            if(!channel.supportsTCP()){
                future.setCause(new UnsupportedOperationException("TCP is not supported on this client"));
            }else{
                channel.openTCP().perform();
                future.taskFinished(this);
            }
        });
    }

    @Override
    public RestFuture<?, Client> startUDP() {
        return RestFuture.create((future, inputNull)->{
            createChannelIfNull();
            if(!channel.supportsUDP()){
                future.setCause(new UnsupportedOperationException("UDP is not supported on this client"));
            }else{
                channel.openUDP().perform();
                future.taskFinished(this);
            }
        });
    }

    @Override
    public RestFuture<?, Client> disconnectTCP() {
        return channel.closeTCP().map((c)->this);
    }

    @Override
    public RestFuture<?, Client> stopUDP() {
        return channel.closeUDP().map((c)->this);
    }

    private void createChannelIfNull(){
        synchronized (lock){
            if(channel==null){
                channel = new JavaClientChannel(executor, this, new InetSocketAddress(getHost(), getPort()), getBufferFactory());
                if(channelInitializer!=null)channelInitializer.initChannel(channel);
            }
        }
    }
}
