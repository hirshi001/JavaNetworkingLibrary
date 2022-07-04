package javatest;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.bufferfactory.DefaultBufferFactory;
import com.hirshi001.buffer.byteorder.ByteOrder;
import com.hirshi001.javanetworking.JavaNetworkFactory;
import com.hirshi001.javanetworking.server.JavaServerChannel;
import com.hirshi001.networking.network.NetworkFactory;
import com.hirshi001.networking.network.channel.*;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.server.AbstractServerListener;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.networkdata.DefaultNetworkData;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.networking.packet.PacketHolder;
import com.hirshi001.networking.packetdecoderencoder.PacketEncoderDecoder;
import com.hirshi001.networking.packetdecoderencoder.SimplePacketEncoderDecoder;
import com.hirshi001.networking.packethandlercontext.PacketHandlerContext;
import com.hirshi001.networking.packetregistrycontainer.MultiPacketRegistryContainer;
import com.hirshi001.networking.packetregistrycontainer.PacketRegistryContainer;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.IntegerPacket;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.StringPacket;
import logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void setup(){
        bufferFactory = new DefaultBufferFactory();
        bufferFactory.defaultOrder(ByteOrder.BIG_ENDIAN);

        packetEncoderDecoder = new SimplePacketEncoderDecoder();
        executor = Executors.newScheduledThreadPool(3);
        networkFactory = new JavaNetworkFactory(executor);

        System.setOut(new Logger().debug());

        serverPacketRegistryContainer = new MultiPacketRegistryContainer();
        serverNetworkData = new DefaultNetworkData(packetEncoderDecoder, serverPacketRegistryContainer);

        clientPacketRegistryContainer = new MultiPacketRegistryContainer();
        clientNetworkData = new DefaultNetworkData(packetEncoderDecoder, clientPacketRegistryContainer);
    }

    @Test
    public void testTCP() throws ExecutionException, InterruptedException, IOException {

        int packetCount = 100;
        AtomicBoolean serverChannelInitialized = new AtomicBoolean(false);
        AtomicInteger serverListenerReceived = new AtomicInteger(0);
        AtomicInteger serverListenerSent = new AtomicInteger(0);

        AtomicBoolean clientChannelInitialized = new AtomicBoolean(false);
        AtomicInteger clientListenerReceived = new AtomicInteger(0);
        AtomicInteger clientListenerSent = new AtomicInteger(0);

        AtomicInteger counter = new AtomicInteger(0);


        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class),0);

        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                serverChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.TCP_KEEP_ALIVE, true);
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            }
        });
        server.addServerListener(new AbstractServerListener(){
            public void onReceived(PacketHandlerContext<?> context) {
                serverListenerReceived.incrementAndGet();
            }
            public void onSent(PacketHandlerContext<?> context) {
                serverListenerSent.incrementAndGet();
            }
        });
        server.startTCP().perform().get();



        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null , IntegerPacket.class), 0);

        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                clientChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.TCP_KEEP_ALIVE, true);
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            }
        });
        client.addClientListener(new AbstractChannelListener(){
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

        for(int i=0;i<packetCount;i++) {
            client.sendTCPWithResponse(new IntegerPacket(i+1), null, 10000).
                    pauseFor(ThreadLocalRandom.current().nextInt(5), TimeUnit.MILLISECONDS).
                    then((context) -> {
                        counter.incrementAndGet();
                    }).
                    perform();

        }
        Thread.sleep(100);

        assertTrue(serverChannelInitialized.get());
        assertEquals(packetCount, serverListenerReceived.get());
        assertEquals(packetCount, serverListenerSent.get());

        assertTrue(clientChannelInitialized.get());
        assertEquals(packetCount, clientListenerReceived.get());
        assertEquals(packetCount, clientListenerSent.get());

        assertEquals(packetCount, counter.get());


        client.close();

        assertTrue(client.isClosed());
        assertTrue(client.getChannel().isUDPClosed());
        assertTrue(client.getChannel().isTCPClosed());

        server.stopTCP().perform().get();
        server.stopUDP().perform().get();

        assertTrue(server.isClosed());
        for(Channel channel : (ChannelSet<Channel>)server.getClients()) {
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

        AtomicBoolean clientChannelInitialized = new AtomicBoolean(false);
        AtomicInteger clientListenerReceived = new AtomicInteger(0);
        AtomicInteger clientListenerSent = new AtomicInteger(0);

        int packetCount = 500;

        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class),0);
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.addServerListener(new AbstractServerListener(){
            @Override
            public void onReceived(PacketHandlerContext<?> context) {
                serverListenerReceived.incrementAndGet();
            }
            @Override
            public void onSent(PacketHandlerContext<?> context) {
                serverListenerSent.incrementAndGet();
            }
        });
        server.setChannelInitializer(new ChannelInitializer() {
            @Override
            public void initChannel(Channel channel) {
                serverChannelInitialized.set(true);
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            }
        });
        server.startUDP().perform().get();
        server.startTCP().perform().get();

        Thread.sleep(5);

        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null , IntegerPacket.class), 0);
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.addClientListener(new AbstractChannelListener(){
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
                channel.setChannelOption(ChannelOption.TCP_AUTO_FLUSH, true);
            }
        });
        client.startUDP().perform().get();
        client.startTCP().perform().get();


        for(int i=0;i<packetCount;i++) {
            client.sendUDPWithResponse(new IntegerPacket(i+1), null, 1000).
                    then((context) -> counter.incrementAndGet()).
                    perform();
            Thread.sleep(1);
        }

        Thread.sleep(100);

        assertEquals(packetCount, counter.get());

        assertTrue(serverChannelInitialized.get());
        assertEquals(packetCount, serverListenerReceived.get());
        assertEquals(packetCount, serverListenerSent.get());

        assertTrue(clientChannelInitialized.get());
        assertEquals(packetCount, clientListenerReceived.get());
        assertEquals(packetCount, clientListenerSent.get());

        Thread.sleep(100);

        server.close();
        client.close();


    }

    private static void randomIntegerResponse(PacketHandlerContext<IntegerPacket> context){
        IntegerPacket ip = context.packet;
        String message = "Hello: From client: " + ip.value + ". To client:" + ThreadLocalRandom.current().nextInt(ip.value);
        StringPacket response = new StringPacket(message);
        response.setResponsePacket(ip);
        context.channel.sendUDP(response, null).perform();
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

}
