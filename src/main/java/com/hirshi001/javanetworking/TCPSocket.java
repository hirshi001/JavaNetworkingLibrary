package com.hirshi001.javanetworking;

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.buffer.buffers.ByteBuffer;
import com.hirshi001.buffer.buffers.CircularArrayBackedByteBuffer;
import com.hirshi001.networking.network.channel.ChannelOption;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TCPSocket {

    private final ByteBuffer buffer;
    private Socket socket;
    private final Map<ChannelOption, Object> options;

    int lastSize;

    public TCPSocket(BufferFactory factory) {
        this.buffer = factory.circularBuffer(64);
        this.options = new ConcurrentHashMap<>();
        lastSize = buffer.size();
    }

    public void connect(Socket socket) {
        buffer.clear();
        this.socket = socket;
        setOptions();
    }

    public void disconnect() {
        try {
            if (buffer != null) buffer.clear();
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }

    public boolean newDataAvailable() {
        if (!isConnected()) return false;

        try {
            InputStream in = socket.getInputStream();

            int available = in.available();
            if (available > 0) {
                buffer.ensureWritable(available);
                for (int i = 0; i < available; i++) {
                    buffer.writeByte(in.read());
                }
                return true;
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;

    }

    public ByteBuffer getData() {
        return buffer;
    }

    public void writeData(byte[] data, int offset, int length) {
        try {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(data, offset, length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeAndFlush(ByteBuffer buffer) {
        if (buffer.hasArray()) {
            writeData(buffer.array(), buffer.readerIndex(), buffer.readableBytes());
        } else if (buffer instanceof CircularArrayBackedByteBuffer) {
            CircularArrayBackedByteBuffer cbuffer = (CircularArrayBackedByteBuffer) buffer;
            if (cbuffer.arrayReaderIndex() <= cbuffer.arrayWriterIndex()) {
                writeData(cbuffer.array(), cbuffer.arrayReaderIndex(), cbuffer.arrayWriterIndex() - cbuffer.arrayReaderIndex());
            } else {
                writeData(cbuffer.array(), cbuffer.arrayReaderIndex(), cbuffer.array().length - cbuffer.arrayReaderIndex());
                writeData(cbuffer.array(), 0, cbuffer.arrayWriterIndex());
            }
        } else {
            byte[] bytes = new byte[buffer.readableBytes()];
            int length = buffer.readBytes(bytes);
            writeData(bytes, 0, length);
        }
        flush();
        buffer.clear();
    }

    public void flush() {
        if (!isConnected()) return;
        try {
            socket.getOutputStream().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setOptions() {
        for (Map.Entry<ChannelOption, Object> entry : options.entrySet()) {
            setOption(entry.getKey(), entry.getValue());
        }
    }

    public <T> void setOption(ChannelOption<T> option, T value) {
        options.put(option, value);
        if (isConnected() && JavaOptionMap.SOCKET_OPTION_MAP.containsKey(option)) {
            try {
                JavaOptionMap.SOCKET_OPTION_MAP.get(option).accept(socket, value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public <T> T getOption(ChannelOption<T> option) {
        return (T) options.get(option);
    }

    public int getLocalPort() {
        if (socket == null) return -1;
        return socket.getLocalPort();
    }


}
