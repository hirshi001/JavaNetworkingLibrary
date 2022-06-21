package javatest;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.bufferfactory.DefaultBufferFactory;
import com.hirshi001.buffer.buffers.ArrayBackedByteBuffer;
import com.hirshi001.javanetworking.JavaNetworkFactory;
import com.hirshi001.networking.network.NetworkFactory;
import com.hirshi001.networking.network.PacketResponseManager;
import com.hirshi001.networking.network.client.AbstractChannelListener;
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
import com.hirshi001.networking.util.defaultpackets.primitivepackets.BytePacket;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.IntegerPacket;
import com.hirshi001.networking.util.defaultpackets.primitivepackets.StringPacket;
import com.hirshi001.restapi.RestFuture;
import com.hirshi001.restapi.RestFutureListener;
import logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

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
        bufferFactory = new DefaultBufferFactory((b, size) -> new ArrayBackedByteBuffer(size, b));
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
    public void testTCP() throws ExecutionException, InterruptedException {


        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class),0);

        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.startTCP().perform().get();



        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null , IntegerPacket.class), 0);

        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.startTCP().perform().get();

        System.out.println("Sending packets");
        for(int i=0;i<100;i++) {
            client.sendTCPWithResponse(new IntegerPacket(i+1), null, 10000).
                    then((context) -> System.out.println("Response from server: " + context.packet)).
                    perform();
        }

        Thread.sleep(1000);
        System.out.println("Finished");

        server.close();
        client.stopTCP().perform().get();
        client.stopUDP().perform().get();
    }

    @Test
    public void testUDP() throws ExecutionException, InterruptedException {
        serverPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, JavaTest::randomIntegerResponse, IntegerPacket.class),0);
        server = networkFactory.createServer(serverNetworkData, bufferFactory, 1234);
        server.startUDP().perform().get();
        server.startTCP().perform().get();

        Thread.sleep(5);

        clientPacketRegistryContainer.getDefaultRegistry().
                registerDefaultPrimitivePackets().
                register(new PacketHolder<>(IntegerPacket::new, null , IntegerPacket.class), 0);
        client = networkFactory.createClient(clientNetworkData, bufferFactory, "localhost", 1234);
        client.startUDP().perform().get();
        client.startTCP().perform().get();

        for(int i=0;i<5;i++) {
            client.sendUDPWithResponse(new IntegerPacket(i+1), null, 10000).
                    then((context) -> System.out.println("Response from server: " + context.packet)).
                    perform();

            Thread.sleep(5);
        }

        Thread.sleep(1000);

    }

    private static void randomIntegerResponse(PacketHandlerContext<IntegerPacket> context){
        IntegerPacket ip = context.packet;
        String message = "Hello: From client: " + ip.value + ". To client:" + ThreadLocalRandom.current().nextInt(ip.value);
        StringPacket response = new StringPacket(message);
        response.setResponsePacket(ip);
        context.channel.sendTCP(response, null).perform();
    }

}
