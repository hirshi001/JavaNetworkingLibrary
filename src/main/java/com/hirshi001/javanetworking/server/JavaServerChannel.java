package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.TCPSocket;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.ScheduledExec;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaServerChannel extends BaseChannel {


    final TCPSocket tcpSide;
    final AtomicBoolean udpClosed = new AtomicBoolean(false);
    private final InetSocketAddress address;
    private final BufferFactory bufferFactory;

    public long lastTCPReceived = 0;
    public boolean lastTCPReceivedValid = false;
    public long lastUDPReceived = 0;
    public boolean lastUDPReceivedValid = false;
    public long lastReceived = 0;


    public JavaServerChannel(ScheduledExec executor, JavaServer server, InetSocketAddress address, BufferFactory bufferFactory) {
        super(server, executor);
        this.address = address;
        this.tcpSide = new TCPSocket(bufferFactory);
        this.bufferFactory = bufferFactory;
    }

    public void connect(Socket socket) {
        tcpSide.connect(socket);
        lastTCPReceivedValid = true;
        lastTCPReceived = lastReceived = System.nanoTime();
    }

    public boolean checkNewTCPData() {
        if (tcpSide.newDataAvailable()) {
            ByteBuffer buffer = tcpSide.getData();
            onTCPBytesReceived(buffer);
            return true;
        }
        return false;
    }

    public void udpPacketReceived(byte[] bytes, int length, long time) {
        lastUDPReceived = time;
        lastReceived = time;
        onUDPPacketsReceived(bufferFactory.wrap(bytes, 0, length));
    }

    public long getUDPPacketTimeout() {
        return udpPacketTimeout;
    }

    public long getTCPPacketTimeout() {
        return tcpPacketTimeout;
    }

    public long getPacketTimeout() {
        return packetTimeout;
    }

    @Override
    protected void writeAndFlushTCP(ByteBuffer buffer) {
        tcpSide.writeAndFlush(buffer);
    }

    @Override
    protected void writeAndFlushUDP(ByteBuffer buffer) {
        getSide().getUDPSide().send(buffer, address);
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
    public RestFuture<?, Channel> startTCP() {
        return RestAPI.create(() -> {
            throw new UnsupportedOperationException("Cannot open TCP on the Server side");
        });
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestAPI.create(() -> {
            if (tcpSide.isClosed()) return this;
            lastTCPReceivedValid = false;
            tcpSide.disconnect();
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> startUDP() {
        return RestAPI.create(() -> {
            udpClosed.set(false);
            lastUDPReceivedValid = true;
            lastUDPReceived = lastReceived = System.nanoTime();
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> stopUDP() {
        return RestAPI.create(() -> {
            if (isUDPClosed()) return this;
            lastUDPReceivedValid = false;
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
    public void checkUDPPackets() {
        getSide().checkUDPPackets();
    }

    @Override
    public void checkTCPPackets() {
        long now = System.nanoTime();
        if(isTCPOpen() && checkNewTCPData()){
            lastTCPReceived = now;
            lastReceived = now;
        }
    }

    @Override
    public boolean isUDPOpen() {
        return !udpClosed.get();
    }

    public JavaServer getSide() {
        return (JavaServer) super.getSide();
    }

    public TCPSocket getTCPSide() {
        return tcpSide;
    }

}

