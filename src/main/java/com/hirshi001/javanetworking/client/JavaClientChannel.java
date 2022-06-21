package com.hirshi001.javanetworking.client;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.JavaOptionMap;
import com.hirshi001.javanetworking.TCPSide;
import com.hirshi001.javanetworking.UDPSide;
import com.hirshi001.javanetworking.server.JavaServer;
import com.hirshi001.javanetworking.server.JavaServerChannel;
import com.hirshi001.networking.network.NetworkSide;
import com.hirshi001.networking.network.channel.*;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.packetdecoderencoder.PacketEncoderDecoder;
import com.hirshi001.networking.packethandlercontext.PacketHandlerContext;
import com.hirshi001.networking.packethandlercontext.PacketType;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JavaClientChannel extends BaseChannel {

    final Client client;
    final InetSocketAddress address;
    final TCPSide tcpSide;
    final UDPSide udpSide;
    final BufferFactory bufferFactory;

    int localPort = 0;
    private ScheduledFuture<?> tcpFuture, udpFuture;
    private final Object tcpLock = new Object(), udpLock = new Object();


    public JavaClientChannel(ScheduledExecutorService executor, Client client, InetSocketAddress address, BufferFactory bufferFactory) {
        super(executor);
        clientListenerHandler = new ChannelListenerHandler();
        this.client = client;
        this.address = address;
        this.bufferFactory = bufferFactory;

        this.tcpSide = new TCPSide(bufferFactory);
        UDPSide tempUdpSide = null;

        try {
            tempUdpSide = new UDPSide(0);
            localPort = tempUdpSide.getLocalPort();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.udpSide = tempUdpSide;

    }

    @Override
    public boolean supportsUDP() {
        return udpSide != null;
    }

    @Override
    public boolean supportsTCP() {
        return true;
    }

    @Override
    protected void sendTCP(byte[] data, int offset, int length) {
        tcpSide.writeData(data, offset, length);
    }

    @Override
    protected void sendUDP(byte[] data, int offset, int length) {
        DatagramPacket packet = new DatagramPacket(data, offset, length, address);
        udpSide.send(packet);
    }

    @Override
    public String getIp() {
        return address.getHostName();
    }

    @Override
    public int getPort() {
        return address.getPort();
    }

    @Override
    public byte[] getAddress() {
        return address.getAddress().getAddress();
    }

    @Override
    public <T> void setChannelOption(ChannelOption<T> option, T value) {
        if(supportsUDP() && JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.containsKey(option)) {
            udpSide.setOption(option, value);
        }
        if(supportsTCP() && JavaOptionMap.SOCKET_OPTION_MAP.containsKey(option)) {
            tcpSide.setOption(option, value);
        }
    }

    @Override
    public <T> T getChannelOption(ChannelOption<T> option) {
        if(supportsUDP() && JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.containsKey(option)) {
            return udpSide.getOption(option);
        }
        if(supportsTCP() && JavaOptionMap.SOCKET_OPTION_MAP.containsKey(option)) {
            return tcpSide.getOption(option);
        }
        return null;
    }

    @Override
    public NetworkSide getSide() {
        return client;
    }

    @Override
    public RestFuture<?, Channel> openTCP() {
        return RestFuture.create(()->{
            Socket socket = new Socket(address.getAddress(), address.getPort(), InetAddress.getLocalHost(), localPort);
            tcpSide.connect(socket);
            localPort = tcpSide.getLocalPort();

            synchronized (tcpLock) {
                if (tcpFuture != null) {
                    tcpFuture.cancel(false);
                }

                tcpFuture = getExecutor().scheduleWithFixedDelay(() -> {
                    ByteBuffer buffer = tcpSide.getData();
                    try {
                        if (tcpSide.newDataAvailable()) {
                            while (true) {
                                buffer.markReaderIndex();
                                PacketEncoderDecoder encoderDecoder = getSide().getNetworkData().getPacketEncoderDecoder();
                                PacketHandlerContext context = encoderDecoder.decode(getSide().getNetworkData().getPacketRegistryContainer(), buffer, null);
                                if (context != null) {
                                    context.packetType = PacketType.TCP;
                                    context.channel = this;
                                    context.networkSide = client;
                                    onPacketReceived(context);
                                    getListenerHandler().TCPReceived(context);
                                    ((JavaClient) getSide()).getClientListenerHandler().TCPReceived(context);
                                } else {
                                    buffer.resetReaderIndex();
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 0, 1, TimeUnit.MILLISECONDS);
            }

            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> closeTCP() {
        return RestFuture.create(()->{
            synchronized (tcpLock) {
                tcpSide.disconnect();
                if (tcpFuture != null) {
                    tcpFuture.cancel(false);
                }
                return this;
            }
        });
    }

    @Override
    public RestFuture<?, Channel> openUDP() {
        System.out.println("hi");
        return RestFuture.create(()-> {
            synchronized (udpLock) {
                if (udpFuture != null) {
                    udpFuture.cancel(false);
                }
                udpFuture = getExecutor().scheduleAtFixedRate(() -> {
                    DatagramPacket packet = null;
                    try {
                        packet = udpSide.receive();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (packet != null) {
                        ByteBuffer buffer = bufferFactory.wrap(packet.getData(), packet.getOffset(), packet.getLength());

                        PacketEncoderDecoder encoderDecoder = getSide().getNetworkData().getPacketEncoderDecoder();
                        PacketHandlerContext context = encoderDecoder.decode(getSide().getNetworkData().getPacketRegistryContainer(), buffer, null);
                        if (context != null) {
                            context.packetType = PacketType.UDP;
                            context.channel = this;
                            context.networkSide = client;
                            onPacketReceived(context);
                            getListenerHandler().UDPReceived(context);
                            ((JavaClient) getSide()).getClientListenerHandler().UDPReceived(context);
                        }
                    }
                }, 0, 1, TimeUnit.MILLISECONDS);
            }
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> closeUDP() {
        return RestFuture.create(()-> {
            synchronized (udpLock) {
                if (udpFuture != null) {
                    udpFuture.cancel(false);
                    udpFuture = null;
                }
                return this;
            }
        });
    }

    @Override
    public boolean isTCPOpen() {
        return tcpSide.isConnected();
    }

    @Override
    public boolean isTCPClosed() {
        return tcpSide.isClosed();
    }

    @Override
    public boolean isUDPOpen() {
        return udpSide!=null;
    }

    @Override
    public boolean isClosed() {
        return isTCPClosed() && isUDPClosed();
    }

}

