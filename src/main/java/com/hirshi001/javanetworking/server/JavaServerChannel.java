package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.javanetworking.TCPSocket;
import com.hirshi001.networking.network.channel.BaseChannel;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.restapi.RestFuture;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaServerChannel extends BaseChannel {


    final TCPSocket tcpSide;
    final AtomicBoolean udpClosed = new AtomicBoolean(false);
    private final InetSocketAddress address;
    private final BufferFactory bufferFactory;
    ScheduledFuture<?> future;


    public JavaServerChannel(ScheduledExecutorService executor, JavaServer server, InetSocketAddress address, BufferFactory bufferFactory){
        super(server, executor);
        this.address = address;
        this.tcpSide = new TCPSocket(bufferFactory);
        this.bufferFactory = bufferFactory;
    }
    public void connect(Socket socket){
        tcpSide.connect(socket);
        future = getExecutor().scheduleWithFixedDelay(()->{
            if(tcpSide.isClosed()){
                stopTCP().perform();
                return;
            }
            if(tcpSide.newDataAvailable()){
                ByteBuffer buffer = tcpSide.getData();
                onTCPBytesReceived(buffer);
            }
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    public void udpPacketReceived(byte[] bytes, int length){
        onUDPPacketReceived(bufferFactory.wrap(bytes, 0, length));
    }

    @Override
    protected void sendTCP(byte[] data, int offset, int length) {
        tcpSide.writeData(data, offset, length);
    }

    @Override
    protected void sendUDP(byte[] data, int offset, int length) {
        DatagramPacket packet = new DatagramPacket(data, offset, length, address);
        getSide().getUDPSide().send(packet);
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
        return RestFuture.create(()->{throw new UnsupportedOperationException("Cannot open TCP on the Server side");});
    }

    @Override
    public RestFuture<?, Channel> stopTCP() {
        return RestFuture.create(()->{
            tcpSide.disconnect();
            if(future!=null){
                future.cancel(true);
            }
            if(isUDPClosed() && isTCPClosed()){
                close().perform();
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
            if(isUDPClosed() && isTCPClosed()){
                close().perform();
            }
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
    public RestFuture<?, Channel> flushUDP() {
        return RestFuture.create(()->this);
    }

    @Override
    public RestFuture<?, Channel> flushTCP() {
        return RestFuture.create(()->{
            tcpSide.flush();
            return this;
        });
    }

    @Override
    public boolean isUDPOpen() {
        return !udpClosed.get();
    }


    public JavaServer getSide(){
        return (JavaServer)super.getSide();
    }

    public TCPSocket getTCPSide(){
        return tcpSide;
    }

}

