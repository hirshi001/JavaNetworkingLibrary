package com.hirshi001.javanetworking;

import com.hirshi001.networking.network.channel.ChannelOption;

import java.util.HashMap;
import java.util.Map;

public class JavaOptionMap {

    public static final Map<ChannelOption, SocketOptionConsumer> SOCKET_OPTION_MAP = new HashMap<ChannelOption, SocketOptionConsumer>(){{
        put(ChannelOption.TCP_KEEP_ALIVE, (socket, value) -> socket.setKeepAlive((Boolean) value));
        put(ChannelOption.TCP_OOB_INLINE, (socket, value) -> socket.setOOBInline((Boolean) value));
        put(ChannelOption.TCP_NODELAY, (socket, value) -> socket.setTcpNoDelay((Boolean) value));
        put(ChannelOption.TCP_SO_TIMEOUT, (socket, value) -> socket.setSoTimeout((Integer) value));
        put(ChannelOption.TCP_SO_LINGER, (socket, value) -> socket.setSoLinger(true, (Integer) value));
        put(ChannelOption.TCP_RECEIVE_BUFFER_SIZE, (socket, value) -> socket.setReceiveBufferSize((Integer) value));
        put(ChannelOption.TCP_SEND_BUFFER_SIZE, (socket, value) -> socket.setSendBufferSize((Integer) value));
        put(ChannelOption.TCP_REUSE_ADDRESS, (socket, value) -> socket.setReuseAddress((Boolean) value));
        put(ChannelOption.TCP_TRAFFIC_CLASS, (socket, value) -> socket.setTrafficClass((Integer) value));
    }};

    public static final Map<ChannelOption, DatagramSocketOptionConsumer> DATAGRAM_SOCKET_OPTION_MAP = new HashMap<ChannelOption, DatagramSocketOptionConsumer>(){{
        put(ChannelOption.UDP_SO_TIMEOUT, (datagramSocket, value) -> datagramSocket.setSoTimeout((Integer) value));
        put(ChannelOption.UDP_REUSE_ADDRESS, (datagramSocket, value) -> datagramSocket.setReuseAddress((Boolean) value));
        put(ChannelOption.MAX_UDP_PACKET_SIZE, (datagramSocket, value) -> datagramSocket.setReceiveBufferSize((Integer) value));
        put(ChannelOption.UDP_TRAFFIC_CLASS, (datagramSocket, value) -> datagramSocket.setTrafficClass((Integer) value));
    }};


}
