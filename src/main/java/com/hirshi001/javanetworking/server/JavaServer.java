package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.UDPSide;
import com.hirshi001.networking.network.channel.ChannelInitializer;
import com.hirshi001.networking.network.channel.ChannelSet;
import com.hirshi001.networking.network.server.BaseServer;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.network.server.ServerListener;
import com.hirshi001.networking.network.server.ServerOption;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class JavaServer extends BaseServer<JavaServerChannel> {

    private UDPSide udpSide;
    private TCPServer tcpServer;
    private Future<?> tcpServerFuture, udpServerFuture;
    private ScheduledExecutorService executor;

    private final Object tcpLock = new Object();
    private final Object udpLock = new Object();

    public JavaServer(ScheduledExecutorService scheduledExecutorService, NetworkData networkData, BufferFactory bufferFactory, int port) {
        super(networkData, bufferFactory, port);
        this.executor = scheduledExecutorService;

        tcpServer = new TCPServer(getPort());
        try {
            this.udpSide = new UDPSide(port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public RestFuture<Server, Server> startTCP() {
        return RestFuture.create(()->{
            synchronized (tcpLock) {
                if(tcpServerFuture != null) {
                    tcpServerFuture.cancel(false);
                }
                tcpServerFuture = executor.submit(()->{
                    try {
                        tcpServer.start((socket)->{
                            int port = socket.getPort();
                            InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
                            Supplier<JavaServerChannel> supplier = ()-> new JavaServerChannel(executor, this, address,  getBufferFactory());

                            JavaServerChannel channel = getClients().getOrCreate(address.getAddress().getAddress(), port, supplier, (c)->{
                                ChannelInitializer ci = getChannelInitializer();
                                if(ci != null) ci.initChannel(c);
                            });
                            channel.connect(socket);
 });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
            return this;
        });
    }
    
    public ServerListener getServerListenerHandler(){
        return serverListenerHandler;
    }

    public UDPSide getUDPSide(){
        return udpSide;
    }

    @Override
    public RestFuture<Server, Server> startUDP() {
        return RestFuture.create(()->{
            synchronized (udpLock) {
                if(udpServerFuture != null) {
                    udpServerFuture.cancel(true);
                    udpServerFuture = null;
                }
                udpServerFuture = executor.scheduleWithFixedDelay(()->{
                    try {
                        DatagramPacket packet = udpSide.receive();
                        if(packet != null) {

                            JavaServerChannel channel = getClients()
                                    .getOrCreate(
                                            packet.getAddress().getAddress(),
                                            packet.getPort(),
                                            ()-> new JavaServerChannel(executor, this, new InetSocketAddress(packet.getAddress(), packet.getPort()),
                                                    getBufferFactory()),
                                            (c)-> {
                                                ChannelInitializer init = getChannelInitializer();
                                                if(init != null)init.initChannel(c);
                                            });
                            channel.udpPacketReceived(packet.getData(), packet.getLength());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, 0, 1, TimeUnit.MILLISECONDS);
            }
            return this;
        });
    }

    @Override
    public RestFuture<Server, Server> stopTCP() {
        return RestFuture.create(()->{
            synchronized (tcpLock) {
                if(tcpServerFuture != null) {
                    tcpServerFuture.cancel(true);
                    tcpServerFuture = null;
                }
            }
            return this;
        });
    }

    @Override
    public RestFuture<Server, Server> stopUDP() {
        return RestFuture.create(()->{
            synchronized (udpLock) {
                if(udpServerFuture != null) {
                    udpServerFuture.cancel(true);
                    udpServerFuture = null;
                }
            }
            return this;
        });
    }

    @Override
    public <T> void setServerOption(ServerOption<T> option, T value) {

    }

    @Override
    public <T> T getServerOption(ServerOption<T> option) {
        return null;
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
    public boolean isClosed() {
        return false;
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
