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
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.ScheduledExec;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JavaClient extends BaseClient {

    JavaClientChannel channel;
    ScheduledExecutorService executor;
    ScheduledExec exec;
    private final Object lock = new Object();

    private final Map<ClientOption, Object> optionObjectMap;

    public JavaClient(NetworkData networkData, BufferFactory bufferFactory, String host, int port, ScheduledExecutorService executor) {
        super(networkData, bufferFactory, host, port);
        this.executor = executor;
        this.exec = new ScheduledExec(){
            @Override
            public void run(Runnable runnable, long delay) {
                executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
            }

            @Override
            public void run(Runnable runnable, long delay, TimeUnit period) {
                executor.schedule(runnable, delay, period);
            }

            @Override
            public void runDeferred(Runnable runnable) {
                executor.execute(runnable);
            }
        };
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
    public RestFuture<?, Client> startTCP() {
        return RestAPI.create((future, inputNull)->{
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
        return RestAPI.create((future, inputNull)->{
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
        return RestAPI.create( ()->{
            if(channel!=null) channel.stopTCP().perform();
            return JavaClient.this;
        });
    }

    @Override
    public RestFuture<?, Client> stopUDP() {
        return RestAPI.create( ()->{
            if(channel!=null) channel.stopUDP().perform();
            return JavaClient.this;
        });
    }

    private void createChannelIfNull(){
        synchronized (lock){
            if(channel==null){
                channel = new JavaClientChannel(exec, executor, this, new InetSocketAddress(getHost(), getPort()), getBufferFactory());
                if(channelInitializer!=null)channelInitializer.initChannel(channel);
            }
        }
    }


}
