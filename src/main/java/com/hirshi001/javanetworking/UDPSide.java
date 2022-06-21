package com.hirshi001.javanetworking;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ArrayBackedByteBuffer;
import com.hirshi001.javanetworking.JavaOptionMap;
import com.hirshi001.networking.network.channel.ChannelOption;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UDPSide {

    private DatagramChannel channel;
    byte[] buffer;
    private Map<ChannelOption, Object> options;

    public UDPSide(int localPort) throws IOException {
        channel = DatagramChannel.open();
        channel.bind(new InetSocketAddress(localPort));
        channel.configureBlocking(false);

        options = new ConcurrentHashMap<>();

        buffer = new byte[1024];

    }

    public int getPort() {
        return channel.socket().getLocalPort();
    }


    public void send(DatagramPacket datagramPacket){
        try {
            channel.send(ByteBuffer.wrap(datagramPacket.getData()), datagramPacket.getSocketAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DatagramPacket receive() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        InetSocketAddress address = (InetSocketAddress) channel.receive(byteBuffer);
        if(address == null) return null;

        int size = byteBuffer.position();
        DatagramPacket packet = new DatagramPacket(buffer.clone(), size);
        packet.setSocketAddress(address);
        packet.setAddress(address.getAddress());

        return packet;
    }

    public void setBufferSize(int size){
        buffer = new byte[size];
    }


    public <T> void setOption(ChannelOption<T> option, T value){
        options.put(option, value);
        if(JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.containsKey(option)){
            try {
                JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.get(option).accept(channel.socket(), value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public <T> T getOption(ChannelOption<T> option){
        return (T) options.get(option);
    }

    public int getLocalPort(){
        return channel.socket().getLocalPort();
    }



}
