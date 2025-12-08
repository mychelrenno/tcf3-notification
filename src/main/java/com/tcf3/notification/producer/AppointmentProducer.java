package com.tcf3.notification.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcf3.notification.model.AgendamentoMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AppointmentProducer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentProducer.class);
    private static final String TOPIC_NAME = "agendamentos";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Envia a mensagem de agendamento para o tópico Kafka.
     * @param message A mensagem de agendamento a ser enviada.
     */
    public void sendMessage(AgendamentoMessage message) {
        log.info("Enviando mensagem para o tópico {}: {}", TOPIC_NAME, message);
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC_NAME, message.getEmailPaciente(), jsonMessage);
            log.info("Mensagem enviada com sucesso para o tópico {}.", TOPIC_NAME);
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar mensagem para JSON", e);
            throw new RuntimeException("Erro ao serializar mensagem para JSON", e);
        }
    }
}
