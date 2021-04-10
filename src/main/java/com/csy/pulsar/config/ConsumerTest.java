package com.csy.pulsar.config;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>Description：</p>
 *
 * @author shuyun.cheng
 * @date 2020/11/21 10:28
 */
@Component
public class ConsumerTest {
    @Resource
    private Consumer<byte[]> consumer;

    @PostConstruct
    public void consumer() throws PulsarClientException {
        while (true) {
            // Wait for a message
            Message<byte[]> msg = consumer.receive();
            try {
                // Do something with the message
                System.out.printf("Message received: %s", new String(msg.getData()));
                // Acknowledge the message so that it can be deleted by the message broker
                consumer.acknowledge(msg);
            } catch (Exception e) {
                // Message failed to process, redeliver later
                consumer.negativeAcknowledge(msg);
            }
        }
    }
}
