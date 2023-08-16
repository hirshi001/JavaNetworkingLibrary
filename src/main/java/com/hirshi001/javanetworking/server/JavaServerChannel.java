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
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.TCPSocket;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.ScheduledExec;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaServerChannel extends BaseChannel {


    final TCPSocket tcpSide;
    final AtomicBoolean udpClosed = new AtomicBoolean(true);
    private final InetSocketAddress address;
    private final BufferFactory bufferFactory;


    public JavaServerChannel(ScheduledExec executor, JavaServer server, InetSocketAddress address, BufferFactory bufferFactory) {
        super(server, executor);
        this.address = address;
        this.tcpSide = new TCPSocket(bufferFactory);
        this.bufferFactory = bufferFactory;
    }

    public void connect(Socket socket) {
        tcpSide.connect(socket);
        onTCPConnected();
    }

    public boolean checkNewTCPData() {
        try {
            if (tcpSide.newDataAvailable()) {
                ByteBuffer buffer = tcpSide.getData();
                onTCPBytesReceived(buffer);
                return true;
            }
        }catch (Exception e){
            stopTCP().perform();
        }
        return false;
    }

    public void udpPacketReceived(byte[] bytes, int length, long time) {
        if(isUDPClosed()) return;
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
            // see the connect method
        });
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestAPI.create(() -> {
            if (tcpSide.isClosed()) return this;
            tcpSide.disconnect();
            onTCPDisconnected();
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> startUDP() {
        return RestAPI.create(() -> {
            udpClosed.set(false);
            lastUDPReceived = lastReceived = System.nanoTime();
            onUDPStart();
            return this;
        });
    }

    @Override
    public RestFuture<?, Channel> stopUDP() {
        return RestAPI.create(() -> {
            if (isUDPClosed()) return this;
            udpClosed.set(true);
            onUDPStop();
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
        super.checkUDPPackets();
    }

    @Override
    public void checkTCPPackets() {
        long now = System.nanoTime();
        if(isTCPOpen() && checkNewTCPData()){
            lastTCPReceived = now;
            lastReceived = now;
        }
        super.checkTCPPackets();
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

