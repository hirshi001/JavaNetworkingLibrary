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
package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.UDPSocket;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelSet;
import com.hirshi001.networking.network.channel.DefaultChannelSet;
import com.hirshi001.networking.network.server.BaseServer;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.network.server.ServerOption;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.ScheduledExec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unchecked")
public class JavaServer extends BaseServer<JavaServerChannel> {

    private UDPSocket udpSide;
    private TCPServer tcpServer;
    private Future<?> tcpServerFuture;
    private ScheduledExecutorService executor;

    private final Object tcpLock = new Object();
    private final Object udpLock = new Object();

    private final AtomicBoolean isTCPClosed = new AtomicBoolean(true);
    private final AtomicBoolean isUDPClosed = new AtomicBoolean(true);


    protected DefaultChannelSet<JavaServerChannel> channelSet;

    public JavaServer(ScheduledExecutorService scheduledExecutorService, ScheduledExec exec, NetworkData networkData, BufferFactory bufferFactory, int port) throws IOException {
        super(exec, networkData, bufferFactory, port);
        this.executor = scheduledExecutorService;

        this.tcpServer = new TCPServer(getPort());
        this.udpSide = new UDPSocket();
        channelSet = new DefaultChannelSet<>(this, ConcurrentHashMap.newKeySet());
        // udpSide.connect(getPort());

    }

    @Override
    public RestFuture<?, Server> startTCP() {
        return RestAPI.create(() -> {
            synchronized (tcpLock) {
                if (tcpServerFuture != null) {
                    tcpServerFuture.cancel(false);
                }
                tcpServerFuture = executor.submit(() -> {
                    try {
                        tcpServer.start((socket) -> {
                            int port = socket.getPort();
                            InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();

                            JavaServerChannel channel;

                            channel = channelSet.get(address.getAddress().getAddress(), port);
                            if (channel == null) {
                                channel = new JavaServerChannel(exec, this, address, getBufferFactory());
                                channel.connect(socket);
                                if (!addChannel(channel)) {
                                    try {
                                        socket.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else {
                                channel.connect(socket);
                            }

                        });
                        isTCPClosed.set(false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                onTCPServerStart();
            }
            return this;
        });
    }
    public UDPSocket getUDPSide() {
        return udpSide;
    }

    @Override
    public RestFuture<?, Server> startUDP() {
        return RestAPI.create(() -> {
            synchronized (udpLock) {
                udpSide.connect(getPort());
                isUDPClosed.set(false);
            }
            onUDPServerStart();
            return this;
        });
    }

    @Override
    public RestFuture<?, Server> stopTCP() {
        return RestAPI.create(() -> {
            isTCPClosed.set(true);
            onTCPServerStop();

            channelSet.forEach((channel) -> {
                try {
                    channel.stopTCP().perform().get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            });

            return this;
        });
    }

    @Override
    public RestFuture<?, Server> stopUDP() {
        return RestAPI.create(() -> {
            isUDPClosed.set(true);
            onUDPServerStop();

            channelSet.forEach((channel) -> {
                try {
                    channel.stopUDP().perform().get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            });

            return this;
        });
    }

    @Override
    protected <T> void activateServerOption(ServerOption<T> option, T value) {
        super.activateServerOption(option, value);
        if (option == ServerOption.RECEIVE_BUFFER_SIZE) { //for udp packets
            udpSide.setSendBufferSize((Integer) value);
        }
    }

    @Override
    public ChannelSet<Channel> getClients() {
        return (ChannelSet<Channel>) (Object) channelSet;
    }


    @Override
    public RestFuture<?, ? extends JavaServer> close() {
        return RestAPI.create(()->{
            try {
                stopTCP().perform().get();
            } catch (InterruptedException | ExecutionException ignored) { }

            try {
                stopUDP().perform().get();
            } catch (InterruptedException | ExecutionException ignored) {}
            return this;
        });
    }

    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean tcpOpen() {
        return !isTCPClosed.get();
    }

    @Override
    public boolean udpOpen() {
        return !isUDPClosed.get();
    }

    @Override
    public void checkUDPPackets() {
        long now = System.nanoTime();
        while (true) {
            try {
                DatagramPacket packet = udpSide.receive();
                if (packet == null) break;
                JavaServerChannel channel;

                channel = channelSet.get(packet.getAddress().getAddress(), packet.getPort());
                if (channel == null) {
                    channel = new JavaServerChannel(exec, this, (InetSocketAddress) packet.getSocketAddress(), getBufferFactory());
                    if (!addChannel(channel)) continue;
                }
                if(channel.isUDPClosed()){
                    channel.startUDP().perform();
                }

                channel.udpPacketReceived(packet.getData(), packet.getLength(), now);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.checkUDPPackets();
    }


    @Override
    public boolean supportsTCP() {
        return true;
    }

    @Override
    public boolean supportsUDP() {
        return true;
    }
}
