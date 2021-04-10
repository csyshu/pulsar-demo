package com.csy.pulsar.config;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * <p>Description：</p>
 *
 * @author shuyun.cheng
 * @date 2020/11/20 19:14
 */
@Configuration
public class PulsarConfig {
    @Bean(name = "pulsarClient")
    public PulsarClient getPulsarClient() throws PulsarClientException {
        return PulsarClient.builder()
                .serviceUrl("pulsar://ip1:6650,1p2:6650")
                .build();
    }

    @Bean
    public Producer<byte[]> getProducer(@Qualifier("pulsarClient") PulsarClient pulsarClient) throws PulsarClientException {
        return pulsarClient.newProducer().topic("persistent://MY/API/request_log")
                .batchingMaxPublishDelay(10, TimeUnit.MILLISECONDS)
                .sendTimeout(10, TimeUnit.SECONDS)
                .blockIfQueueFull(true)
                .create();
    }

    @Bean
    public Consumer<byte[]> getConsumer(@Qualifier("pulsarClient") PulsarClient pulsarClient) throws PulsarClientException {
        return pulsarClient.newConsumer().topic("persistent://MY/API/request_log").subscriptionName("csy-subscription").subscribe();
    }
}
