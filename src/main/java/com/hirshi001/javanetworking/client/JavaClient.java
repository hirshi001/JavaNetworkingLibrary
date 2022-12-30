package com.hirshi001.javanetworking.client;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelListener;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.networking.network.client.BaseClient;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.client.ClientOption;
import com.hirshi001.networking.network.server.ServerListener;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestFuture;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public class JavaClient extends BaseClient {

    JavaClientChannel channel;
    ScheduledExecutorService executor;
    private final Object lock = new Object();

    private final Map<ClientOption, Object> optionObjectMap;

    public JavaClient(NetworkData networkData, BufferFactory bufferFactory, String host, int port, ScheduledExecutorService executor) {
        super(networkData, bufferFactory, host, port);
        this.executor = executor;
        optionObjectMap = new HashMap<>();
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public <T> void setClientOption(ClientOption<T> option, T value) {
        optionObjectMap.put(option, value);
        activateClientOption(option, value);
    }

    @Override
    public <T> T getClientOption(ClientOption<T> option) {
        return (T) optionObjectMap.get(option);
    }

    protected <T> void activateClientOption(ClientOption<T> option, T value){
        if(option==ClientOption.UDP_PACKET_CHECK_INTERVAL){
            channel.scheduleUDP();
        }
        if(option==ClientOption.TCP_PACKET_CHECK_INTERVAL){
            channel.scheduleTCP();
        }
    }

    public ChannelListener getClientListenerHandler(){
        return clientListenerHandler;
    }

    @Override
    public boolean isClosed() {
        return channel.isClosed();
    }

    @Override
    public boolean tcpOpen() {
        return getChannel().isTCPOpen();
    }

    @Override
    public boolean udpOpen() {
        return getChannel().isUDPOpen();
    }

    @Override
    public boolean isOpen() {
        return getChannel().isOpen();
    }

    @Override
    public void close() {
        try {
            stopTCP().perform().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        try {
            stopUDP().perform().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public RestFuture<?, Client> startTCP() {
        return RestFuture.create((future, inputNull)->{
            createChannelIfNull();
            if(!channel.supportsTCP()){
                future.setCause(new UnsupportedOperationException("TCP is not supported on this client"));
            }else{
                channel.startTCP().onFailure(future::setCause).then(n-> future.taskFinished(JavaClient.this)).perform();
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
                channel.startUDP().onFailure(future::setCause).then(n->future.taskFinished(JavaClient.this)).perform();
            }
        });
    }

    @Override
    public RestFuture<?, Client> stopTCP() {
        return channel.stopTCP().map((c)->this);
    }

    @Override
    public RestFuture<?, Client> stopUDP() {
        return channel.stopUDP().map((c)->this);
    }

    @Override
    public RestFuture<Client, Client> checkUDPPackets() {
        return RestFuture.create( ()->{
            getChannel().checkUDPPackets().perform().get();
            return this;
        });
    }

    @Override
    public RestFuture<Client, Client> checkTCPPackets() {
        return RestFuture.create( ()->{
            getChannel().checkTCPPackets().perform().get();
            return this;
        });
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
