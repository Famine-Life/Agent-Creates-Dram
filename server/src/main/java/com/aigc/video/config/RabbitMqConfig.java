package com.aigc.video.config;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅在启用 RabbitMQ 时声明任务队列。 */
@Configuration
@ConditionalOnProperty(prefix = "aigc.mq", name = "type", havingValue = "rabbitmq")
public class RabbitMqConfig {

    /** 声明持久化任务队列，队列名可由 aigc.mq.queue 覆盖。 */
    @Bean
    Queue taskDispatchQueue(org.springframework.core.env.Environment environment) {
        String queue = environment.getProperty("aigc.mq.queue", "aigc.task.dispatch");
        return new Queue(queue, true);
    }
}
