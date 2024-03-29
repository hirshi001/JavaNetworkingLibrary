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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class TCPServer {

    private final int port;
    ServerSocket serverSocket;

    public TCPServer(int port) {
        this.port = port;
    }

    public void connect() throws IOException{
        serverSocket = new ServerSocket(port);
    }

    public void start(Consumer<Socket> callback) throws IOException {
        while (true) {
            Socket socket = serverSocket.accept();
            callback.accept(socket);
        }
    }
}
