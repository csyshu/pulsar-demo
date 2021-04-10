package com.csy.pulsar.config;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * <p>Description：</p>
 *
 * @author shuyun.cheng
 * @date 2020/11/20 19:13
 */
@Component
public class ProducerTest {
    @Resource
    private Producer<byte[]> producer;

    @PostConstruct
    public void sendMsg(String msg) throws PulsarClientException {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // You can then send messages to the broker and topic you specified:
        while (true) {
            producer.send("my pulsar test msg".getBytes());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
