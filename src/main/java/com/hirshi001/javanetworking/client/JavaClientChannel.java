package com.hirshi001.javanetworking.client;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.buffer.buffers.CircularArrayBackedByteBuffer;
import com.hirshi001.javanetworking.TCPSocket;
import com.hirshi001.javanetworking.UDPSocket;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.client.ClientOption;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.ScheduledExec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JavaClientChannel extends BaseChannel {

    final InetSocketAddress address;
    final TCPSocket tcpSide;
    final UDPSocket udpSide;
    final BufferFactory bufferFactory;

    int localPort = 0;
    private ScheduledFuture<?> tcpFuture, udpFuture;
    private final Object tcpLock = new Object(), udpLock = new Object(), connectLock = new Object();

    private ScheduledExecutorService executor;


    public JavaClientChannel(ScheduledExec exec, ScheduledExecutorService executor, Client client, InetSocketAddress address, BufferFactory bufferFactory) {
        super(client, exec);
        this.executor = executor;
        this.address = address;
        this.bufferFactory = bufferFactory;

        this.tcpSide = new TCPSocket(bufferFactory);
        this.udpSide = new UDPSocket();

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
    public void checkUDPPackets() {
        DatagramPacket packet;
        while (true) {
            try {
                packet = udpSide.receive();
                if (packet == null) break;
                onUDPPacketsReceived(bufferFactory.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
            } catch (IOException e) {
                break;
            }
        }
    }

    @Override
    protected <T> boolean activateOption(ChannelOption<T> option, T value) {
        boolean success = super.activateOption(option, value);
        udpSide.setOption(option, value);
        tcpSide.setOption(option, value);
        if (option == ChannelOption.UDP_RECEIVE_BUFFER_SIZE) {
            udpSide.udpReceiveBufferSize((Integer) value);
            return true;
        }
        else if (option==ChannelOption.MAX_UDP_PAYLOAD_SIZE) {
            udpSide.setSendBufferSize((Integer) value);
            return true;
        }
        return success;
    }

    @Override
    public void checkTCPPackets() {
        if (tcpSide.isClosed()) {
            stopTCP().perform();
        }
        if (tcpSide.newDataAvailable()) {
            ByteBuffer buffer = tcpSide.getData();
            onTCPBytesReceived(buffer);
        }
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
    public JavaClient getSide() {
        return (JavaClient) super.getSide();
    }

    @Override
    protected void writeAndFlushTCP(ByteBuffer buffer) {
        tcpSide.writeAndFlush(buffer);
    }

    @Override
    protected void writeAndFlushUDP(ByteBuffer buffer) {
        udpSide.send(buffer, address);
        buffer.clear();
    }


    @Override
    public RestFuture<?, Channel> startTCP() {
        return RestAPI.create(() -> {
            synchronized (connectLock) {
                Socket socket = new Socket(address.getAddress(), address.getPort(), null, localPort);
                tcpSide.connect(socket);
                localPort = tcpSide.getLocalPort();
                scheduleTCP();
            }
            getListenerHandler().onTCPConnect(this);
            return this;
        });
    }

    void scheduleTCP() {
        synchronized (tcpLock) {
            if (!isTCPOpen()) return;
            if (tcpFuture != null) {
                tcpFuture.cancel(false);
            }

            Integer delay = getSide().getClientOption(ClientOption.TCP_PACKET_CHECK_INTERVAL);
            if (delay == null) delay = 0;

            if (delay >= 0) {
                if (delay == 0) delay = 1; // minimum delay of 1 ms
                tcpFuture = executor.scheduleWithFixedDelay(this::checkTCPPackets, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestAPI.create(() -> {
            synchronized (tcpLock) {
                if (isTCPClosed()) return this;
                tcpSide.disconnect();
                if (tcpFuture != null) {
                    tcpFuture.cancel(true);
                }
                if (isTCPClosed() && isUDPClosed()) {
                    close().perform();
                }
                getListenerHandler().onTCPDisconnect(this);
                return this;
            }
        });
    }

    @Override
    public RestFuture<?, Channel> startUDP() {
        return RestAPI.create(() -> {
            synchronized (connectLock) {
                udpSide.connect(localPort);
                localPort = udpSide.getLocalPort();
                scheduleUDP();
            }
            getListenerHandler().onUDPStart(this);
            return this;
        });
    }

    void scheduleUDP() {
        synchronized (udpLock) {
            if (!isUDPOpen()) return;
            if (udpFuture != null) {
                udpFuture.cancel(true);
            }

            Integer delay = getSide().getClientOption(ClientOption.UDP_PACKET_CHECK_INTERVAL);
            if (delay == null) delay = 0;
            if (delay >= 0) {
                if (delay == 0) delay = 1; // minimum delay of 1 ms
                udpFuture = executor.scheduleWithFixedDelay(this::checkUDPPackets, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }


    @Override
    public RestFuture<?, Channel> stopUDP() {
        return RestAPI.create(() -> {
            synchronized (udpLock) {
                if (isUDPClosed()) return this;
                if (udpFuture != null) {
                    udpFuture.cancel(false);
                    udpFuture = null;
                }
                udpSide.close();
                if (isTCPClosed() && isUDPClosed()) {
                    close().perform();
                }
                getListenerHandler().onUDPStop(this);
                return this;
            }
        });
    }

    @Override
    public boolean isTCPOpen() {
        return tcpSide.isConnected();
    }

    @Override
    public boolean isUDPOpen() {
        return udpSide.isConnected();
    }

    @Override
    public boolean isClosed() {
        return isTCPClosed() && isUDPClosed();
    }

}

