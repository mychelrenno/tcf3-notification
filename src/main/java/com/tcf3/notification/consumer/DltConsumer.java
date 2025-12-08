package com.tcf3.notification.consumer;

import com.tcf3.notification.model.AgendamentoMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumidor dedicado ao tópico de Dead Letter (DLT).
 * O Spring Kafka nomeia o DLT topic como: {original_topic}.DLT
 * Neste caso, o tópico original é "agendamentos", então o DLT é "agendamentos.DLT".
 */
@Component
public class DltConsumer {

    private static final Logger log = LoggerFactory.getLogger(DltConsumer.class);

    private static final String DLT_TOPIC_NAME = "agendamentos.DLT";

    @KafkaListener(topics = DLT_TOPIC_NAME, groupId = "${spring.kafka.consumer.group-id}-dlt", containerFactory = "kafkaListenerContainerFactory")
    public void listenDlt(AgendamentoMessage message) {
        log.error("--- MENSAGEM NO DLT RECEBIDA ---");
        log.error("Mensagem que falhou após todas as retentativas: {}", message);
        
        // Aqui é onde a lógica de tratamento de falhas permanentes deve ser implementada.
        // Exemplos:
        // 1. Notificar uma equipe de suporte (ex: Slack, PagerDuty).
        // 2. Armazenar a mensagem em um banco de dados para análise posterior.
        // 3. Enviar um e-mail de falha para o administrador do sistema.
        
        log.error("--- FIM DO PROCESSAMENTO DLT ---");
    }
}
