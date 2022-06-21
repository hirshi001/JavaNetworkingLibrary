package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.JavaOptionMap;
import com.hirshi001.javanetworking.TCPSide;
import com.hirshi001.javanetworking.UDPSide;
import com.hirshi001.networking.network.NetworkSide;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelListenerHandler;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.packetdecoderencoder.PacketEncoderDecoder;
import com.hirshi001.networking.packethandlercontext.PacketHandlerContext;
import com.hirshi001.networking.packethandlercontext.PacketType;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaServerChannel extends BaseChannel {


    final TCPSide tcpSide;
    final AtomicBoolean udpClosed = new AtomicBoolean(false);
    private final JavaServer server;
    private final InetSocketAddress address;
    private final BufferFactory bufferFactory;
    ScheduledFuture future;


    public JavaServerChannel(ScheduledExecutorService executor, JavaServer server, InetSocketAddress address, BufferFactory bufferFactory){
        super(executor);
        this.server = server;
        this.address = address;
        this.tcpSide = new TCPSide(bufferFactory);
        this.bufferFactory = bufferFactory;
        clientListenerHandler = new ChannelListenerHandler();
    }
    public void connect(Socket socket){
        tcpSide.connect(socket);
        future = getExecutor().scheduleWithFixedDelay(()->{
            if(tcpSide.newDataAvailable()){
                ByteBuffer buffer = tcpSide.getData();
                while(true) {
                    buffer.markReaderIndex();
                    PacketEncoderDecoder encoderDecoder = getSide().getNetworkData().getPacketEncoderDecoder();
                    PacketHandlerContext context = encoderDecoder.decode(getSide().getNetworkData().getPacketRegistryContainer(), buffer, null);
                    if (context != null) {
                        context.packetType = PacketType.TCP;
                        context.channel = this;
                        context.networkSide = server;
                        onPacketReceived(context);
                        ((JavaServer) getSide()).getServerListenerHandler().onTCPReceived(context);
                    } else {
                        buffer.resetReaderIndex();
                        break;
                    }
                }
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    public void udpPacketReceived(byte[] bytes, int length){
        ByteBuffer buffer = bufferFactory.wrap(bytes, 0, length);

        PacketEncoderDecoder encoderDecoder = getSide().getNetworkData().getPacketEncoderDecoder();
        PacketHandlerContext context = encoderDecoder.decode(getSide().getNetworkData().getPacketRegistryContainer(), buffer, null);
        if (context != null) {
            context.packetType = PacketType.UDP;
            context.channel = this;
            context.networkSide = server;
            onPacketReceived(context);
            ((JavaServer) getSide()).getServerListenerHandler().onUDPReceived(context);
        }

    }

    @Override
    protected void sendTCP(byte[] data, int offset, int length) throws IOException {
        tcpSide.writeData(data, offset, length);
    }

    @Override
    protected void sendUDP(byte[] data, int offset, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, offset, length, address);
        server.getUDPSide().send(packet);
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

    }

    @Override
    public <T> T getChannelOption(ChannelOption<T> option) {
        return null;
    }

    @Override
    public NetworkSide getSide() {
        return server;
    }

    @Override
    public RestFuture<?, Channel> startTCP() {
        return RestFuture.create(()->{throw new UnsupportedOperationException("Cannot open TCP on the Server side");});
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestFuture.create(()->{
            tcpSide.disconnect();
            if(future!=null){
                future.cancel(true);
            }
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> startUDP() {
        return RestFuture.create(()->{
            udpClosed.set(false);
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> stopUDP() {
        return RestFuture.create(()->{
            udpClosed.set(true);
            return this;
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
        return !udpClosed.get();
    }
}

