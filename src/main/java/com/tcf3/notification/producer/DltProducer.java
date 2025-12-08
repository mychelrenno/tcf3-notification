package com.tcf3.notification.producer;

import com.tcf3.notification.model.AgendamentoMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DltProducer {
    private static final String DLQ_TOPIC = "agendamentos-dlq";

    @Autowired
    private KafkaTemplate<String, AgendamentoMessage> kafkaTemplate;

    public void sendToDlq(AgendamentoMessage message) {
        kafkaTemplate.send(DLQ_TOPIC, message);
    }
}

