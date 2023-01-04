package com.hirshi001.javanetworking.server;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.UDPSocket;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.DefaultChannelSet;
import com.hirshi001.networking.network.server.BaseServer;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.network.server.ServerOption;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.RestFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unchecked")
public class JavaServer extends BaseServer<JavaServerChannel> {

    private UDPSocket udpSide;
    private TCPServer tcpServer;
    private Future<?> tcpServerFuture, udpServerFuture, tcpDataCheck;
    private ScheduledExecutorService executor;

    private final Object tcpLock = new Object();
    private final Object udpLock = new Object();

    private final AtomicBoolean isTCPClosed = new AtomicBoolean(true);
    private final AtomicBoolean isUDPClosed = new AtomicBoolean(true);

    private final Map<ServerOption, Object> options = new ConcurrentHashMap<>();

    public JavaServer(ScheduledExecutorService scheduledExecutorService, NetworkData networkData, BufferFactory bufferFactory, int port) throws IOException {
        super(networkData, bufferFactory, port);
        this.executor = scheduledExecutorService;

        this.tcpServer = new TCPServer(getPort());
        this.udpSide = new UDPSocket();
        // udpSide.connect(getPort());

    }

    @Override
    public RestFuture<?, Server> startTCP() {
        return RestAPI.create(() -> {
            synchronized (tcpLock) {
                if (tcpServerFuture != null) {
                    tcpServerFuture.cancel(false);
                }
                tcpServerFuture = executor.submit(() -> {
                    try {
                        tcpServer.start((socket) -> {
                            int port = socket.getPort();
                            InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();

                            DefaultChannelSet<JavaServerChannel> channelSet = (DefaultChannelSet) getClients();
                            JavaServerChannel channel;
                            synchronized (channelSet.getLock()) {
                                channel = channelSet.get(address.getAddress().getAddress(), port);
                                if (channel == null) {
                                    channel = new JavaServerChannel(executor, this, address, getBufferFactory());
                                    channel.connect(socket);
                                    if (!addChannel(channel)) {
                                        try {
                                            socket.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else channel.connect(socket);
                            }
                        });
                        isTCPClosed.set(false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                scheduleTCP();
            }
            return this;
        });
    }

    private void scheduleTCP() {
        synchronized (tcpLock) {
            if (tcpDataCheck != null) {
                tcpDataCheck.cancel(true);
            }

            Integer delay = getServerOption(ServerOption.TCP_PACKET_CHECK_INTERVAL);
            if(delay==null) delay=0;

            if (delay >= 0) {
                if(delay==0) delay=1;
                tcpDataCheck = executor.scheduleWithFixedDelay(this::checkTCPPackets, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public UDPSocket getUDPSide() {
        return udpSide;
    }

    @Override
    public RestFuture<?, Server> startUDP() {
        return RestAPI.create(() -> {
            synchronized (udpLock) {
                udpSide.connect(getPort());
                scheduleUDP();
                isUDPClosed.set(false);
            }
            return this;
        });
    }

    private void scheduleUDP(){
        synchronized (udpLock) {
            if (udpServerFuture != null) {
                udpServerFuture.cancel(true);
                udpServerFuture = null;
            }

            Integer delay = getServerOption(ServerOption.UDP_PACKET_CHECK_INTERVAL);
            if(delay==null) delay=0;

            if (delay >= 0) {
                if(delay==0) delay=1;
                udpServerFuture = executor.scheduleWithFixedDelay(this::checkUDPPackets, 0, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public RestFuture<?, Server> stopTCP() {
        return RestAPI.create(() -> {
            synchronized (tcpLock) {
                if (tcpServerFuture != null) {
                    tcpServerFuture.cancel(true);
                    tcpServerFuture = null;
                }
                if (tcpDataCheck != null) {
                    tcpDataCheck.cancel(true);
                }
                isTCPClosed.set(true);

            }
            DefaultChannelSet<JavaServerChannel> channelSet = (DefaultChannelSet) getClients();
            synchronized (channelSet.getLock()) {
                channelSet.forEach((channel) -> {
                    try {
                        channel.stopTCP().perform().get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            }
            return this;
        });
    }

    @Override
    public RestFuture<?, Server> stopUDP() {
        return RestAPI.create(() -> {
            synchronized (udpLock) {
                if (udpServerFuture != null) {
                    udpServerFuture.cancel(true);
                    udpServerFuture = null;
                }
                for (Channel channel : getClients()) {
                    channel.stopUDP().perform().get();
                }
                isUDPClosed.set(true);
            }
            synchronized (channelSet.getLock()) {
                channelSet.forEach((channel) -> {
                    try {
                        channel.stopUDP().perform().get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            }
            return this;
        });
    }

    @Override
    public <T> void setServerOption(ServerOption<T> option, T value) {
        options.put(option, value);
        activateOption(option, value);
    }

    private <T> void activateOption(ServerOption<T> option, T value) {
        if (option == ServerOption.MAX_CLIENTS) {
            getClients().setMaxSize((Integer) value);
        } else if (option == ServerOption.RECEIVE_BUFFER_SIZE) { //for udp packets
            udpSide.setSendBufferSize((Integer) value);
        } else if (option == ServerOption.UDP_PACKET_CHECK_INTERVAL) {
            scheduleUDP();
        } else if (option == ServerOption.TCP_PACKET_CHECK_INTERVAL) {
            scheduleTCP();
        }
    }

    @Override
    public <T> T getServerOption(ServerOption<T> option) {
        return (T) options.get(option);
    }

    @Override
    public void close() {
        try {
            stopTCP().perform().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        try {
            stopUDP().perform().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean tcpOpen() {
        return !isTCPClosed.get();
    }

    @Override
    public boolean udpOpen() {
        return !isUDPClosed.get();
    }

    @Override
    public void checkUDPPackets() {
        long now = System.nanoTime();
        while (true) {
            try {
                DatagramPacket packet = udpSide.receive();
                if (packet == null) break;
                DefaultChannelSet<JavaServerChannel> channelSet = (DefaultChannelSet) getClients();
                JavaServerChannel channel;
                synchronized (channelSet.getLock()) {
                    channel = channelSet.get(packet.getAddress().getAddress(), packet.getPort());
                    if (channel == null) {
                        channel = new JavaServerChannel(executor, this, (InetSocketAddress) packet.getSocketAddress(), getBufferFactory());
                        if (!addChannel(channel)) continue;
                        channel.startUDP().perform();
                    }
                }
                channel.udpPacketReceived(packet.getData(), packet.getLength(), now);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void checkTCPPackets() {
        synchronized (getClients().getLock()) {
            long now = System.nanoTime();
            Iterator<Channel> iterator = getClients().iterator();
            while (iterator.hasNext()) {
                JavaServerChannel channel = (JavaServerChannel) iterator.next();
                if (channel.isTCPOpen() && channel.checkNewTCPData()) {
                    channel.lastTCPReceived = now;
                    channel.lastReceived = now;
                }
                if ((channel.lastTCPReceivedValid || channel.lastUDPReceivedValid) && channel.getPacketTimeout() > 0
                        && now - channel.lastReceived > TimeUnit.MILLISECONDS.toNanos(channel.getPacketTimeout())) {
                    iterator.remove();
                    channel.close().perform();
                    continue;
                }
                if (channel.lastTCPReceivedValid && channel.isTCPOpen() && channel.getTCPPacketTimeout() > 0
                        && now - channel.lastTCPReceived > TimeUnit.MILLISECONDS.toNanos(channel.getTCPPacketTimeout())) {
                    channel.stopTCP().perform();
                }
                if (channel.lastUDPReceivedValid && channel.isUDPOpen() && channel.getUDPPacketTimeout() > 0
                        && now - channel.lastUDPReceived > TimeUnit.MILLISECONDS.toNanos(channel.getUDPPacketTimeout())) {
                    channel.stopUDP().perform();
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return tcpOpen() || udpOpen();
    }

    @Override
    public boolean supportsTCP() {
        return true;
    }

    @Override
    public boolean supportsUDP() {
        return true;
    }
}
