package com.arto.kafka.consumer.binding;

import com.arto.core.common.MqTypeEnum;
import com.arto.core.consumer.ConsumerConfig;
import com.arto.core.consumer.MqListener;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by xiong.j on 2017/1/12.
 */
@Setter
@Getter
@ToString
public class KafkaConsumerConfig extends ConsumerConfig {

    /** 并发线程数量 */
    private int numThreads;

    public KafkaConsumerConfig(String destination, MqListener listener) {
        this.setType(MqTypeEnum.KAFKA);
        this.setDestination(destination);
        this.setListener(listener);
    }

    public KafkaConsumerConfig(String destination, MqListener listener, int priority) {
        this.setType(MqTypeEnum.KAFKA);
        this.setDestination(destination);
        this.setPriority(priority);
        this.setListener(listener);
    }
}
