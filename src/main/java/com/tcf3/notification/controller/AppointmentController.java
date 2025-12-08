package com.tcf3.notification.controller;

import com.tcf3.notification.model.AgendamentoMessage;
import com.tcf3.notification.producer.AppointmentProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.tcf3.notification.service.HealthCheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agendamentos")
public class AppointmentController {

    @Autowired
    private AppointmentProducer producer;

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Endpoint para simular o envio de uma mensagem de agendamento para o Kafka.
     * @param message O corpo da mensagem de agendamento.
     * @return Resposta HTTP indicando o sucesso do envio.
     */
    @PostMapping("/enviar")
    public ResponseEntity<Map<String, String>> enviarAgendamento(@Valid @RequestBody AgendamentoMessage message) {
        Map<String, String> response = new HashMap<>();
        try {
            producer.sendMessage(message);
            response.put("message", "Mensagem de agendamento enviada com sucesso para o Kafka.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("message", "Erro ao enviar mensagem para o Kafka: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Endpoint para verificar o status de saúde do serviço.
     * @return Um mapa contendo o status de cada componente.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = healthCheckService.checkHealth();
        
        if ("UP".equals(status.get("status"))) {
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.status(503).body(status); // Service Unavailable
        }
    }
}
