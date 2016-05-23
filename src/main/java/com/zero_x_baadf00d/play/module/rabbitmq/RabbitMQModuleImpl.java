/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thibault Meyer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.zero_x_baadf00d.play.module.rabbitmq;

import com.google.inject.Inject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import play.Configuration;
import play.Logger;
import play.inject.ApplicationLifecycle;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of {@code RabbitMQModule}.
 *
 * @author Thibault Meyer
 * @version 16.05.19
 * @see RabbitMQModule
 * @since 16.03.19
 */
@Singleton
public class RabbitMQModuleImpl implements RabbitMQModule {

    /**
     * The logger to use in this module.
     *
     * @since 16.05.19
     */
    private static final Logger.ALogger LOGGER = Logger.of(RabbitMQModuleImpl.class);

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_URI = "rabbitmq.conn.uri";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_HEARTBEAT = "rabbitmq.conn.heartbeat";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_RECOVERY = "rabbitmq.conn.networkRecoveryInterval";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_TIMEOUT = "rabbitmq.conn.connectionTimeOut";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_EXECUTOR = "rabbitmq.conn.executorService";

    /**
     * Play application configuration.
     *
     * @since 16.05.19
     */
    private final Configuration configuration;

    /**
     * Connection to the RabbitMQ server.
     *
     * @since 16.05.19
     */
    private Connection rabbitConnection;

    /**
     * Build an instance.
     *
     * @param lifecycle     The current application lifecyle
     * @param configuration The current application configuration
     * @since 16.05.19
     */
    @Inject
    public RabbitMQModuleImpl(final ApplicationLifecycle lifecycle, final Configuration configuration) {
        this.configuration = configuration;
        try {
            final String uri = configuration.getString(RabbitMQModuleImpl.RABBITMQ_CONN_URI);
            if (uri == null || uri.isEmpty()) {
                throw new RuntimeException("URI is empty");
            }
            final ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(uri);
            connectionFactory.setRequestedHeartbeat(configuration.getInt(RabbitMQModuleImpl.RABBITMQ_CONN_HEARTBEAT, ConnectionFactory.DEFAULT_HEARTBEAT));
            connectionFactory.setNetworkRecoveryInterval(configuration.getInt(RabbitMQModuleImpl.RABBITMQ_CONN_RECOVERY, 5000));
            connectionFactory.setConnectionTimeout(configuration.getInt(RabbitMQModuleImpl.RABBITMQ_CONN_TIMEOUT, ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT));

            final ExecutorService es = Executors.newFixedThreadPool(configuration.getInt(RabbitMQModuleImpl.RABBITMQ_EXECUTOR, 20));
            this.rabbitConnection = connectionFactory.newConnection(es);
            RabbitMQModuleImpl.LOGGER.info("RabbitMQ connected at {}", String.format(
                    "amqp://%s:%d/%s",
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getVirtualHost())
            );
        } catch (Exception ex) {
            RabbitMQModuleImpl.LOGGER.error("Can't initialize RabbitMQ module", ex);
            throw new RuntimeException(ex);
        }

        lifecycle.addStopHook(() -> {
            RabbitMQModuleImpl.LOGGER.info("Shutting down RabbitMQ");
            this.rabbitConnection.close();
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public Map<String, Object> getServerProperties() {
        return this.rabbitConnection.getServerProperties();
    }

    @Override
    public int getChannelMax() {
        return this.rabbitConnection.getChannelMax();
    }

    @Override
    public Channel getChannel() throws IOException {
        return this.rabbitConnection.createChannel();
    }

    @Override
    public Channel getChannel(final String name) throws IOException {
        final Channel channel = this.rabbitConnection.createChannel();
        final String key = "rabbitmq.channels." + name.replace(" ", "_") + ".";
        channel.queueDeclare(
                name,
                this.configuration.getBoolean(key + "durable", true),
                this.configuration.getBoolean(key + "exclusive", false),
                this.configuration.getBoolean(key + "autoDelete", false),
                null
        );
        return channel;
    }
}
