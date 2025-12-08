package com.tcf3.notification.consumer;

import com.tcf3.notification.model.AgendamentoMessage;
import com.tcf3.notification.producer.DltProducer;
import com.tcf3.notification.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

@Component
public class AppointmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentConsumer.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private Validator validator;

    @Autowired
    private DltProducer dltProducer;

    // O tópico Kafka que será escutado. Assumimos o nome "agendamentos".
    private static final String TOPIC_NAME = "agendamentos";

    @KafkaListener(topics = TOPIC_NAME, groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void listen(AgendamentoMessage message) {
        log.info("Mensagem recebida do tópico {}: {}", TOPIC_NAME, message);

        // 1. Validação do DTO
        Set<ConstraintViolation<AgendamentoMessage>> violations = validator.validate(message);

        if (!violations.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Mensagem inválida recebida do Kafka. Erros: ");
            for (ConstraintViolation<AgendamentoMessage> violation : violations) {
                errorMsg.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            log.error(errorMsg.toString());
            // Envia a mensagem inválida para o DLQ
            dltProducer.sendToDlq(message);
            return; // Interrompe o processamento da mensagem inválida
        }

        // 2. Processamento da mensagem válida
        // Se o emailService falhar, ele lançará uma exceção, que será capturada
        // pelo DefaultErrorHandler do Kafka, acionando o mecanismo de retry.
        emailService.sendNotificationEmail(message);
        log.info("Notificação por e-mail enviada com sucesso para: {}", message.getEmailPaciente());
    }
}
