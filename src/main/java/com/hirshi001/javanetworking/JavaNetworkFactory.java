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

import com.hirshi001.buffer.bufferfactory.BufferFactory;
import com.hirshi001.javanetworking.client.JavaClient;
import com.hirshi001.javanetworking.server.JavaServer;
import com.hirshi001.javarestapi.JavaRestFuture;
import com.hirshi001.javarestapi.JavaRestFutureFactory;
import com.hirshi001.javarestapi.JavaScheduledExecutor;
import com.hirshi001.networking.network.NetworkFactory;
import com.hirshi001.networking.network.client.Client;
import com.hirshi001.networking.network.server.Server;
import com.hirshi001.networking.networkdata.NetworkData;
import com.hirshi001.restapi.RestAPI;
import com.hirshi001.restapi.ScheduledExec;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

public class JavaNetworkFactory implements NetworkFactory {

    private ScheduledExecutorService executor;

    public JavaNetworkFactory(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public Server createServer(NetworkData networkData, BufferFactory bufferFactory, int port) {
        try {
            return new JavaServer(executor, RestAPI.getDefaultExecutor(), networkData, bufferFactory, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Client createClient(NetworkData networkData, BufferFactory bufferFactory, String host, int port) {
        return new JavaClient(RestAPI.getDefaultExecutor(), networkData, bufferFactory, host, port);
    }
}
