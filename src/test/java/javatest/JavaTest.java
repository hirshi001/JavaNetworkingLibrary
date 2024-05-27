package javatest;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.bufferfactory.DefaultBufferFactory;
import com.hirshi001.buffer.byteorder.ByteOrder;
import com.hirshi001.javanetworking.JavaNetworkFactory;
import com.hirshi001.javarestapi.JavaRestFutureFactory;
import com.hirshi001.networking.network.NetworkFactory;
import com.hirshi001.networking.network.channel.AbstractChannelListener;
import com.hirshi001.networking.network.channel.Channel;
import com.hirshi001.networking.network.channel.ChannelInitializer;
import com.hirshi001.networking.network.channel.ChannelOption;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.client.ClientOption;
import com.hirshi001.networking.network.networkcondition.NetworkCondition;
import com.hirshi001.networking.network.server.AbstractServerListener;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.network.server.ServerOption;
import com.hirshi001.networking.networkdata.DefaultNetworkData;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.networking.packet.DataPacket;
import com.hirshi001.networking.packet.PacketHandler;
import com.hirshi001.networking.packet.PacketHolder;
import com.hirshi001.networking.packetdecoderencoder.PacketEncoderDecoder;
import com.hirshi001.networking.packetdecoderencoder.SimplePacketEncoderDecoder;
import com.hirshi001.networking.packethandlercontext.PacketHandlerContext;
import com.hirshi001.networking.packethandlercontext.PacketType;
import com.hirshi001.networking.packetregistrycontainer.MultiPacketRegistryContainer;
import com.hirshi001.networking.packetregistrycontainer.PacketRegistryContainer;
import com.hirshi001.networking.util.defaultpackets.arraypackets.IntegerArrayPacket;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.IntegerPacket;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.StringPacket;
import com.hirshi001.restapi.RestAPI;
import logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class JavaTest {

    public BufferFactory bufferFactory;
    public NetworkFactory networkFactory;
    public PacketEncoderDecoder packetEncoderDecoder;
    public ScheduledExecutorService executor;

    public Server server;
    public PacketRegistryContainer serverPacketRegistryContainer;
    public NetworkData serverNetworkData;

    public Client client;
    public PacketRegistryContainer clientPacketRegistryContainer;
    public NetworkData clientNetworkData;

    @BeforeEach
    public void setup() {
        RestAPI.setFactory(new JavaRestFutureFactory());

        bufferFactory = new DefaultBufferFactory();
        bufferFactory.defaultOrder(ByteOrder.BIG_ENDIAN);

        packetEncoderDecoder = new SimplePacketEncoderDecoder(Integer.MAX_VALUE);
        executor = Executors.newScheduledThreadPool(3);
        networkFactory = new JavaNetworkFactory(executor);

        System.setOut(new Logger().debug());

        serverPacketRegistryContainer = new MultiPacketRegistryContainer();
        serverNetworkData = new DefaultNetworkData(packetEncoderDecoder, serverPacketRegistryContainer);

        clientPacketRegistryContainer = new MultiPacketRegistryContainer();
        clientNetworkData = new DefaultNetworkData(packetEncoderDecoder, clientPacketRegistryContainer);
        try{
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTCP() throws ExecutionException, InterruptedException, IOException {

        int packetCount = 100;
        AtomicBoolean serverChannelInitialized = new AtomicBoolean(false);
        AtomicInteger serverListenerReceived = new AtomicInteger(0);
        AtomicInteger serverListenerSent = new AtomicInteger(0);
        AtomicBoolean clientConnectListener = new AtomicBoolean(false);
        AtomicBoolean clientDisconnectListener = new AtomicBoolean(false);

        AtomicBoolean clientChannelInitialized = new AtomicBoolean(false);
        AtomicInteger clientListenerReceived = new AtomicInteger(0);
        AtomicInteger clientListenerSent = new AtomicInteger(0);

        AtomicInteger counter = new AtomicInteger(0);


        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class), 0);

        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                serverChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.TCP_KEEP_ALIVE, true);
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
                channel.setChannelOption(ChannelOption.PACKET_TIMEOUT, TimeUnit.SECONDS.toNanos(2));
            }
        });
        server.setServerOption(ServerOption.TCP_PACKET_CHECK_INTERVAL, 100);
        server.addServerListener(new AbstractServerListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                serverListenerReceived.incrementAndGet();
            }

            @Override
            public void onSent(PacketHandlerContext<?> context) {
                serverListenerSent.incrementAndGet();
            }

            @Override
            public void onClientConnect(Server server, Channel channel) {
                clientConnectListener.set(true);
            }

            @Override
            public void onClientDisconnect(Server server, Channel clientChannel) {
                clientDisconnectListener.set(true);
            }
        });

        server.startTCP().perform().get();


        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null, IntegerPacket.class), 0);

        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                clientChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.TCP_KEEP_ALIVE, true);
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
                channel.setChannelOption(ChannelOption.PACKET_TIMEOUT, TimeUnit.SECONDS.toNanos(2));
            }
        });
        client.setClientOption(ClientOption.TCP_PACKET_CHECK_INTERVAL, 100);
        client.addClientListeners(new AbstractChannelListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                clientListenerReceived.incrementAndGet();
            }

            @Override
            public void onSent(PacketHandlerContext<?> context) {
                clientListenerSent.incrementAndGet();
            }
        });
        client.startTCP().perform().get();

        for (int i = 0; i < packetCount; i++) {
            client.getChannel().sendTCPWithResponse(new IntegerPacket(i + 1), null, 10000).
                    pauseFor(ThreadLocalRandom.current().nextInt(5), TimeUnit.MILLISECONDS).
                    then((context) -> {
                        counter.incrementAndGet();
                    }).
                    perform();

        }



        for (int i = 0; i < 15; i++) {
            Thread.sleep(100);
            assertFalse(clientDisconnectListener.get(), "Client disconnected at i=" + i);
        }

        Thread.sleep(1500);
        assertTrue(clientDisconnectListener.get());

        assertTrue(serverChannelInitialized.get());
        assertEquals(packetCount, serverListenerReceived.get());
        assertEquals(packetCount, serverListenerSent.get());
        assertTrue(clientConnectListener.get());

        assertTrue(clientChannelInitialized.get());
        assertEquals(packetCount, clientListenerReceived.get());
        assertEquals(packetCount, clientListenerSent.get());

        assertEquals(packetCount, counter.get());


        client.close().perform();

        assertTrue(client.isClosed());
        assertTrue(client.getChannel().isUDPClosed());
        assertTrue(client.getChannel().isTCPClosed());

        server.stopTCP().perform().get();
        server.stopUDP().perform().get();

        assertTrue(server.isClosed());
        for (Channel channel : server.getClients()) {
            assertTrue(channel.isUDPClosed());
            assertTrue(channel.isTCPClosed());
        }
    }

    @Test
    public void testUDP() throws ExecutionException, InterruptedException, IOException {


        AtomicInteger counter = new AtomicInteger(0);
        AtomicBoolean serverChannelInitialized = new AtomicBoolean(false);
        AtomicInteger serverListenerReceived = new AtomicInteger(0);
        AtomicInteger serverListenerSent = new AtomicInteger(0);
        AtomicInteger clientConnectListener = new AtomicInteger(0);
        AtomicInteger clientDisconnectListener = new AtomicInteger(0);

        AtomicBoolean clientChannelInitialized = new AtomicBoolean(false);
        AtomicInteger clientListenerReceived = new AtomicInteger(0);
        AtomicInteger clientListenerSent = new AtomicInteger(0);

        int packetCount = 100;

        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class), 0);
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.addServerListener(new AbstractServerListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                serverListenerReceived.incrementAndGet();
            }

            @Override
            public void onSent(PacketHandlerContext<?> context) {
                serverListenerSent.incrementAndGet();
            }

            @Override
            public void onClientConnect(Server server, Channel channel) {
                clientConnectListener.getAndIncrement();
            }

            @Override
            public void onClientDisconnect(Server server, Channel clientChannel) {
                clientDisconnectListener.getAndIncrement();
            }
        });
        server.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                serverChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.UDP_AUTO_FLUSH, true);
                channel.setChannelOption(ChannelOption.PACKET_TIMEOUT, TimeUnit.SECONDS.toNanos(2));
            }
        });
        server.setServerOption(ServerOption.UDP_PACKET_CHECK_INTERVAL, 100);
        server.startTCP().perform().get(); // check for disconnect
        server.startUDP().perform().get();

        Thread.sleep(5);

        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null, IntegerPacket.class), 0);
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.addClientListeners(new AbstractChannelListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                clientListenerReceived.incrementAndGet();
            }

            @Override
            public void onSent(PacketHandlerContext<?> context) {
                clientListenerSent.incrementAndGet();
            }

        });
        client.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                clientChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.UDP_AUTO_FLUSH, true);
            }
        });
        client.setClientOption(ClientOption.UDP_PACKET_CHECK_INTERVAL, 100);
        client.startTCP().perform().get();
        client.startUDP().perform().get();

        for (int i = 0; i < packetCount; i++) {
            client.getChannel().sendUDPWithResponse(new IntegerPacket(i + 1), null, 1000).
                    then((context) -> counter.incrementAndGet()).
                    perform();
        }

        for (int i = 0; i < 15; i++) {
            Thread.sleep(100);
            assertEquals(0, clientDisconnectListener.get(), "Client disconnected at i=" + i);
        }
        Thread.sleep(1500);
        assertEquals(1, clientDisconnectListener.get());

        assertEquals(packetCount, counter.get());

        assertTrue(serverChannelInitialized.get());
        assertEquals(packetCount, serverListenerReceived.get());
        assertEquals(packetCount, serverListenerSent.get());
        assertEquals(1, clientConnectListener.get());

        assertTrue(clientChannelInitialized.get());
        assertEquals(packetCount, clientListenerReceived.get());
        assertEquals(packetCount, clientListenerSent.get());


        server.close().perform();
        client.close().perform();

    }

    private static void randomIntegerResponse(PacketHandlerContext<IntegerPacket> context) {
        IntegerPacket ip = context.packet;
        String message = "Hello: From client: " + ip.value + ". To client:" + ThreadLocalRandom.current().nextInt(ip.value);
        StringPacket response = new StringPacket(message);
        response.setResponsePacket(ip);
        context.channel.send(response, null, context.packetType).perform();
    }

    @Test
    public void socketCloseOpenTest() throws IOException {
        ServerSocket ss = new ServerSocket(1234);
        Socket clientSocket = new Socket("localhost", 1234);
        Socket serverSocket = ss.accept();

        clientSocket.close();
        serverSocket.close();
        ss.close();


        ServerSocket ss2 = new ServerSocket(1234);
        Socket clientSocket2 = new Socket("localhost", 1234);
        Socket serverSocket2 = ss2.accept();

        clientSocket2.close();
        serverSocket2.close();
        ss2.close();
    }

    @Test
    public void sendManyBytesTest() throws IOException, ExecutionException, InterruptedException {
        AtomicBoolean received = new AtomicBoolean(false);
        serverPacketRegistryContainer.getDefaultRegistry()
                .registerDefaultArrayPrimitivePackets()
                .register(IntegerArrayPacket::new, null, IntegerArrayPacket.class, 0);

        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            }
        });

        server.addServerListener(new AbstractServerListener() {
            @Override
            public void onClientConnect(Server server, Channel clientChannel) {
                int[] ints = new int[100000];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = i;
                }
                clientChannel.sendTCP(new IntegerArrayPacket(ints), null).perform();
            }
        });

        server.startTCP().perform().get();

        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultArrayPrimitivePackets().
                register(IntegerArrayPacket::new, new PacketHandler<IntegerArrayPacket>() {
                    @Override
                    public void handle(PacketHandlerContext<IntegerArrayPacket> context) {
                        received.set(true);
                    }
                }, IntegerArrayPacket.class, 0);

        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.setChannelInitializer(channel -> {
            channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
        });
        client.addClientListeners(new AbstractChannelListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                received.set(true);
            }
        });


        client.startTCP().perform().get();
        Thread.sleep(5000);

        assertTrue(received.get());
    }

    // NOTE: DataPackets are not fully implemented yet in the  BetterNetworkingLibrary, so this test will fail
    @Test
    public void dataPacketTest() throws IOException, ExecutionException, InterruptedException {
        serverNetworkData.getPacketRegistryContainer().getDefaultRegistry().registerDefaultPrimitivePackets();
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        final String message = "Hello, this will be sent to everyone";

        AtomicBoolean received = new AtomicBoolean(false);

        server.addServerListener(new AbstractServerListener() {
            final DataPacket<StringPacket> dataPacket = DataPacket.of(bufferFactory.buffer(64), new StringPacket(message));

            @Override
            public void onClientConnect(Server server, Channel clientChannel) {
                clientChannel.send(dataPacket, null, PacketType.TCP).perform();
                clientChannel.flushTCP();
            }
        });

        server.startTCP().perform().get();

        clientNetworkData.getPacketRegistryContainer().getDefaultRegistry().registerDefaultPrimitivePackets();
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.addClientListeners(new AbstractChannelListener() {
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                StringPacket packet = (StringPacket) context.packet;
                received.set(true);
                assertEquals(message, packet.value);
            }
        });
        client.startTCP().perform().get();
        Thread.sleep(1000);

        assertTrue(received.get());

    }

    @Test
    public void networkConditionTestUDP() throws IOException, ExecutionException, InterruptedException {
        serverPacketRegistryContainer.getDefaultRegistry().registerDefaultPrimitivePackets();
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        AtomicInteger receiveCount = new AtomicInteger(0);
        final AtomicLong firstSend = new AtomicLong(0); // time in milliseconds of first packet sent by client
        final AtomicBoolean first = new AtomicBoolean(true); // checks if the first packet arrived is too early
        server.setChannelInitializer(channel -> {
            channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            channel.addChannelListener(new AbstractChannelListener() {
                @Override
                public void onUDPReceived(PacketHandlerContext<?> context) {
                    long receiveTime = System.currentTimeMillis();
                    if(receiveTime - firstSend.get() < 4950) {
                        first.set(false);
                        System.out.println("Received too early: " + (receiveTime - firstSend.get()));
                    }
                    System.out.println("Received: " + ((IntegerPacket)context.packet).value);
                    receiveCount.incrementAndGet();
                }
            });
        });
        server.setServerOption(ServerOption.UDP_PACKET_CHECK_INTERVAL, 10);

        server.startTCP().perform().get();
        server.startUDP().perform().get();

        clientPacketRegistryContainer.getDefaultRegistry().registerDefaultPrimitivePackets();
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.setChannelInitializer(channel -> {
            channel.setChannelOption(ChannelOption.UDP_AUTO_FLUSH, true);

            NetworkCondition condition = channel.getNetworkCondition();
            condition.sendLatencySpeed = 5F;
            condition.sendPacketLossRate = 0.50D;
            condition.sendPacketLossStandardDeviation = 0.2D;
            channel.enableNetworkCondition(true);
        });



        client.startTCP().perform().get();
        client.startUDP().perform().get();

        firstSend.set(System.currentTimeMillis());
        for(int i = 0; i < 100; i++) {
            client.getChannel().sendUDP(new IntegerPacket(i), null).perform();
        }

        Thread.sleep(10000);
        System.out.println("Received: " + receiveCount.get());
        assertTrue(receiveCount.get() > 25);
        assertTrue(first.get());


    }


    @Test
    public void networkConditionTestTCP() throws IOException, ExecutionException, InterruptedException {
        serverPacketRegistryContainer.getDefaultRegistry().registerDefaultPrimitivePackets();
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        AtomicInteger receiveCount = new AtomicInteger(0);
        final AtomicLong firstSend = new AtomicLong(0); // time in milliseconds of first packet sent by client
        final AtomicBoolean first = new AtomicBoolean(true); // checks if the first packet arrived is too early
        server.setChannelInitializer(channel -> {
            channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            channel.addChannelListener(new AbstractChannelListener() {
                @Override
                public void onTCPReceived(PacketHandlerContext<?> context) {
                    long receiveTime = System.currentTimeMillis();
                    if(receiveTime - firstSend.get() < 4950) {
                        first.set(false);
                        System.out.println("Received too early: " + (receiveTime - firstSend.get()));
                    }
                    System.out.println("Received: " + ((IntegerPacket)context.packet).value);
                    receiveCount.incrementAndGet();
                }
            });
        });
        server.setServerOption(ServerOption.TCP_PACKET_CHECK_INTERVAL, 10);

        server.startTCP().perform().get();

        clientPacketRegistryContainer.getDefaultRegistry().registerDefaultPrimitivePackets();
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.setChannelInitializer(channel -> {
            channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);

            NetworkCondition condition = channel.getNetworkCondition();
            condition.sendLatencySpeed = 5F;
            condition.sendPacketLossRate = 0.50D;
            condition.sendPacketLossStandardDeviation = 0.2D;
            channel.enableNetworkCondition(true);
        });



        client.startTCP().perform().get();
        client.startUDP().perform().get();

        firstSend.set(System.currentTimeMillis());
        for(int i = 0; i < 100; i++) {
            client.getChannel().sendTCP(new IntegerPacket(i), null).perform();
        }

        Thread.sleep(10000);
        System.out.println("Received: " + receiveCount.get());
        assertTrue(receiveCount.get() == 100);
        assertTrue(first.get());


    }

}
