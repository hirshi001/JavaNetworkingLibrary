/*
 * Copyright 2023 Hrishikesh Ingle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    private final Object lock = new Object();

    public JavaClient(ScheduledExec exec, NetworkData networkData, BufferFactory bufferFactory, String host, int port) {
        super(exec, networkData, bufferFactory, host, port);
    }

    @Override
    protected void setReceiveBufferSize(int size) {
        if(channel!=null) channel.udpSide.udpReceiveBufferSize(size);
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean isClosed() {
        return channel.isClosed();
    }

    @Override
    public boolean supportsTCP() {
        return true;
    }

    @Override
    public boolean supportsUDP() {
        return true;
    }

    @Override
    public RestFuture<?, Client> startTCP() {
        return RestAPI.create((future, inputNull)->{
            createChannelIfNull();
            if(!channel.supportsTCP()){
                future.setCause(new UnsupportedOperationException("TCP is not supported on this client"));
            }else{
                channel.startTCP().onFailure(future::setCause).then(n-> {
                    future.taskFinished(JavaClient.this);
                }).perform();
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
                channel = new JavaClientChannel(exec, getExecutor(), this, new InetSocketAddress(getHost(), getPort()), getBufferFactory());
                if(channelInitializer!=null) channelInitializer.initChannel(channel);
            }
        }
    }


}
