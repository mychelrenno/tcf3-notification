package com.tcf3.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    @Autowired
    private KafkaTemplate<String, ?> kafkaTemplate;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @Value("${spring.mail.host}")
    private String mailHost;

    /**
     * Realiza a verificação de saúde do serviço.
     * @return Um mapa contendo o status de cada componente.
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "UP"); // Status geral inicial

        // 1. Verificação do Kafka
        boolean kafkaUp = checkKafkaConnection(healthStatus);
        
        // 2. Verificação do E-mail
        boolean mailUp = checkMailConnection(healthStatus);

        // Atualiza o status geral
        if (!kafkaUp || !mailUp) {
            healthStatus.put("status", "DOWN");
        }

        return healthStatus;
    }

    /**
     * Tenta verificar a conexão com o Kafka.
     * @param healthStatus Mapa para adicionar o status do Kafka.
     * @return true se a conexão for bem-sucedida, false caso contrário.
     */
    private boolean checkKafkaConnection(Map<String, Object> healthStatus) {
        try {
            // Tenta obter metadados dos tópicos, o que requer uma conexão ativa.
            // O timeout padrão é usado.
            kafkaTemplate.partitionsFor("agendamentos"); 
            healthStatus.put("kafka", "UP");
            return true;
        } catch (Exception e) {
            log.error("Falha na conexão com o Kafka: {}", e.getMessage());
            healthStatus.put("kafka", "DOWN");
            healthStatus.put("kafka_error", e.getMessage());
            return false;
        }
    }

    /**
     * Tenta verificar a conexão com o servidor de e-mail.
     * @param healthStatus Mapa para adicionar o status do E-mail.
     * @return true se a conexão for bem-sucedida, false caso contrário.
     */
    private boolean checkMailConnection(Map<String, Object> healthStatus) {
        try {
            // Tenta obter a sessão de transporte do JavaMailSender, o que geralmente
            // tenta se conectar ao servidor SMTP.
            mailSender.testConnection();
            healthStatus.put("email", "UP");
            healthStatus.put("email_host", mailHost);
            return true;
        } catch (Exception e) {
            log.error("Falha na conexão com o servidor de e-mail: {}", e.getMessage());
            healthStatus.put("email", "DOWN");
            healthStatus.put("email_host", mailHost);
            healthStatus.put("email_error", e.getMessage());
            return false;
        }
    }
}
