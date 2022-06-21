package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.UDPSide;
import com.hirshi001.networking.network.channel.ChannelInitializer;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.networking.network.channel.ChannelSet;
import com.hirshi001.networking.network.channel.DefaultChannelSet;
import com.hirshi001.networking.network.server.BaseServer;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.network.server.ServerListener;
import com.hirshi001.networking.network.server.ServerOption;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class JavaServer extends BaseServer<JavaServerChannel> {

    private UDPSide udpSide;
    private TCPServer tcpServer;
    private Future<?> tcpServerFuture, udpServerFuture;
    private ScheduledExecutorService executor;

    private final Object tcpLock = new Object();
    private final Object udpLock = new Object();

    private final AtomicBoolean isTCPClosed = new AtomicBoolean(true);
    private final AtomicBoolean isUDPClosed = new AtomicBoolean(true);

    private final Map<ServerOption, Object> options = new ConcurrentHashMap<>();

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
    public RestFuture<?, Server> startTCP() {
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

                            DefaultChannelSet<JavaServerChannel> channelSet = getClients();
                            JavaServerChannel channel;
                            synchronized (channelSet.getLock()){
                                channel = channelSet.get(address.getAddress().getAddress(), port);
                                if(channel==null){
                                    channel = new JavaServerChannel(executor, this, address, getBufferFactory());
                                    if(!addChannel(channel)){
                                        try {
                                            socket.close();
                                        } catch (IOException e) {e.printStackTrace();}
                                        return;
                                    }
                                }
                            }
                            channel.connect(socket);
                        });
                        isTCPClosed.set(false);
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
    public RestFuture<?, Server> startUDP() {
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

                            DefaultChannelSet<JavaServerChannel> channelSet = getClients();
                            JavaServerChannel channel;
                            synchronized (channelSet.getLock()) {
                                channel = channelSet.get(packet.getAddress().getAddress(), packet.getPort());
                                if (channel == null) {
                                    channel = new JavaServerChannel(executor, this, (InetSocketAddress) packet.getSocketAddress(), getBufferFactory());
                                    if (!addChannel(channel)) return;
                                };
                            }
                            channel.udpPacketReceived(packet.getData(), packet.getLength());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, 0, 1, TimeUnit.MILLISECONDS);
                isUDPClosed.set(false);
            }
            return this;
        });
    }

    @Override
    public RestFuture<?, Server> stopTCP() {
        return RestFuture.create(()->{
            synchronized (tcpLock) {
                if(tcpServerFuture != null) {
                    tcpServerFuture.cancel(true);
                    tcpServerFuture = null;
                }
                isTCPClosed.set(true);
            }
            return this;
        });
    }

    @Override
    public RestFuture<?, Server> stopUDP() {
        return RestFuture.create(()->{
            synchronized (udpLock) {
                if(udpServerFuture != null) {
                    udpServerFuture.cancel(true);
                    udpServerFuture = null;
                }
                isUDPClosed.set(true);
            }
            return this;
        });
    }

    @Override
    public <T> void setServerOption(ServerOption<T> option, T value) {
        options.put(option, value);
    }

    private void activateOption(ServerOption option, Object value) {
        if(option==ServerOption.MAX_CLIENTS){
           // getClients().setMaxClients((int)value);
        }
        else if(option==ServerOption.RECEIVE_BUFFER_SIZE){ //for udp packets
            udpSide.setOption(ChannelOption.UDP_RECEIVE_BUFFER_SIZE, (Integer) value);
            udpSide.setBufferSize((Integer) value);
        }
        else if(option==ServerOption.SET_SO_TIMEOUT){

        }
        else if(option==ServerOption.REUSE_ADDRESS){
            //tcpServer.setOption(ChannelOption.SO_REUSEADDR, (Boolean) value);
        }
    }

    @Override
    public <T> T getServerOption(ServerOption<T> option) {
        return (T) options.get(option);
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
        return isTCPClosed.get() && isUDPClosed.get();
    }

    @Override
    public boolean isOpen() {
        return !isClosed();
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
