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
package com.hirshi001.javanetworking;

import com.hirshi001.networking.network.channel.ChannelOption;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UDPSocket {

    private DatagramChannel channel;
    byte[] receiveBuffer;
    byte[] sendBuffer;

    private final Object sendBufferLock = new Object();

    private Map<ChannelOption, Object> options;
    public UDPSocket() {
        options = new ConcurrentHashMap<>();
    }

    public void connect(int localPort) throws IOException {

        channel = DatagramChannel.open();

        channel.bind(new InetSocketAddress(localPort));
        channel.configureBlocking(false);

        receiveBuffer = new byte[512];
        sendBuffer = new byte[512];
    }

    public int getPort() {
        return channel.socket().getLocalPort();
    }


    public void send(byte[] bytes, int offset, int length, InetSocketAddress address)  {
        try {
            channel.send(ByteBuffer.wrap(bytes, offset, length), address);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void send(com.hirshi001.buffer.buffers.ByteBuffer buffer, InetSocketAddress address)  {
        synchronized (sendBufferLock) {
            while(buffer.readableBytes()>0) {
                int size = buffer.readBytes(sendBuffer);
                send(sendBuffer, 0, size, address);
            }
        }
    }

    public void close() {
        try {
            if (channel != null) channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isOpen();
    }


    public DatagramPacket receive() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(receiveBuffer);
        InetSocketAddress address = (InetSocketAddress) channel.receive(byteBuffer);
        if (address == null) return null;

        int size = byteBuffer.position();
        DatagramPacket packet = new DatagramPacket(byteBuffer.array().clone(), size);
        packet.setSocketAddress(address);
        packet.setAddress(address.getAddress());

        return packet;
    }

    public void setSendBufferSize(int size) {
        synchronized (sendBufferLock) {
            sendBuffer = new byte[size];
        }
    }

    public void udpReceiveBufferSize(int size){
        receiveBuffer = new byte[size];
    }

    public <T> void setOption(ChannelOption<T> option, T value) {
        options.put(option, value);
        if (JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.containsKey(option)) {
            try {
                JavaOptionMap.DATAGRAM_SOCKET_OPTION_MAP.get(option).accept(channel.socket(), value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public <T> T getOption(ChannelOption<T> option) {
        return (T) options.get(option);
    }

    public int getLocalPort() {
        return channel.socket().getLocalPort();
    }


}
