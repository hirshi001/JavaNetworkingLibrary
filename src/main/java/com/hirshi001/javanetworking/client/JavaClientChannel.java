package com.hirshi001.javanetworking.client;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.TCPSocket;
import com.hirshi001.javanetworking.UDPSocket;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.client.ClientOption;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
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


    public JavaClientChannel(ScheduledExecutorService executor, Client client, InetSocketAddress address, BufferFactory bufferFactory) {
        super(client, executor);
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
    public RestFuture<Channel, Channel> checkUDPPackets() {
        return RestFuture.create( ()-> {
            DatagramPacket packet;
            while (true) {
                try {
                    packet = udpSide.receive();
                    if (packet == null) break;
                    onUDPPacketReceived(bufferFactory.wrap(packet.getData(), packet.getOffset(), packet.getLength()));
                } catch (IOException e) {
                    break;
                }
            }
            return this;
        });
    }

    @Override
    public RestFuture<Channel, Channel> checkTCPPackets() {
        return RestFuture.create(()->{
            if (tcpSide.isClosed()) {
                stopTCP().perform();
                return this;
            }
            if (tcpSide.newDataAvailable()) {
                ByteBuffer buffer = tcpSide.getData();
                onTCPBytesReceived(buffer);
            }
            return this;
        });

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
    public JavaClient getSide() {
        return (JavaClient) super.getSide();
    }

    @Override
    public RestFuture<?, Channel> flushUDP() {
        return RestFuture.create(() -> this);
    }

    @Override
    public RestFuture<?, Channel> flushTCP() {
        return RestFuture.create(() -> {
            tcpSide.flush();
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> startTCP() {
        return RestFuture.create(() -> {
            synchronized (connectLock) {
                Socket socket = new Socket(address.getAddress(), address.getPort(), null, localPort);
                tcpSide.connect(socket);
                localPort = tcpSide.getLocalPort();
                scheduleTCP();
            }
            return this;
        });
    }

    void scheduleTCP() {
        synchronized (tcpLock) {
            if(!isTCPOpen()) return;
            if (tcpFuture != null) {
                tcpFuture.cancel(false);
            }

            Integer delay = getSide().getClientOption(ClientOption.UDP_PACKET_CHECK_INTERVAL);
            if(delay==null) delay = 0;

            if (delay >= 0) {
                if (delay == 0) delay = 1; // minimum delay of 1 ms

                tcpFuture = getExecutor().scheduleWithFixedDelay(() -> {
                    try {
                        checkTCPPackets().perform().get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestFuture.create(() -> {
            synchronized (tcpLock) {
                if (isTCPClosed()) return this;
                tcpSide.disconnect();
                if (tcpFuture != null) {
                    tcpFuture.cancel(true);
                }
                if (isTCPClosed() && isUDPClosed()) {
                    close().perform();
                }
                return this;
            }
        });
    }

    @Override
    public RestFuture<?, Channel> startUDP() {
        return RestFuture.create(() -> {
            synchronized (connectLock) {
                udpSide.connect(localPort);
                localPort = udpSide.getLocalPort();
                scheduleUDP();
            }
            return this;
        });
    }

    void scheduleUDP() {
        synchronized (udpLock) {
            if(!isUDPOpen()) return;
            if (udpFuture != null) {
                udpFuture.cancel(true);
            }

            Integer delay = getSide().getClientOption(ClientOption.UDP_PACKET_CHECK_INTERVAL);
            if(delay==null) delay = 0;
            if (delay >= 0) {
                if (delay == 0) delay = 1; // minimum delay of 1 ms
                udpFuture = getExecutor().scheduleWithFixedDelay(() -> {
                    try {
                        checkUDPPackets().perform().get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }



    @Override
    public RestFuture<?, Channel> stopUDP() {
        return RestFuture.create(() -> {
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

