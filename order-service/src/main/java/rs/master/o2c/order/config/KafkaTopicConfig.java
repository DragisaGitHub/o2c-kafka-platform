package rs.master.o2c.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import rs.master.o2c.events.TopicNames;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder
                .name(TopicNames.ORDER_EVENTS_V1)
                .partitions(1)
                .replicas(1)
                .build();
    }
}