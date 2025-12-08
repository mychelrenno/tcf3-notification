package com.tcf3.notification.service;

import com.tcf3.notification.exception.EmailSendException;
import com.tcf3.notification.exception.TemplateLoadException;
import com.tcf3.notification.model.AgendamentoMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String TEMPLATE_PATH = "classpath:email_template.txt";
    private String emailTemplateCache; // Variável para armazenar o template em cache

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${spring.mail.username}")
    private String mailFrom;

    /**
     * Carrega o template de e-mail para o cache na inicialização do bean.
     */
    @PostConstruct
    public void loadEmailTemplate() {
        try {
            Resource resource = resourceLoader.getResource(TEMPLATE_PATH);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                emailTemplateCache = reader.lines().collect(Collectors.joining("\n"));
                log.info("Template de e-mail carregado para o cache com sucesso.");
            }
        } catch (Exception e) {
            log.error("Falha ao carregar o template de e-mail do caminho: {}", TEMPLATE_PATH, e);
            // Lançar TemplateLoadException para evitar que o serviço inicie sem o template
            throw new TemplateLoadException("Falha ao inicializar o EmailService: template de e-mail não encontrado ou inacessível.", e);
        }
    }

    public void sendNotificationEmail(AgendamentoMessage agendamento) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            // O e-mail de destino é o e-mail do paciente
            message.setTo(agendamento.getEmailPaciente());
            message.setSubject("Confirmação de Agendamento de Atendimento");
            
            // Monta o corpo do e-mail usando o template em cache
            String emailBody = emailTemplateCache
                .replace("[nome do paciente]", agendamento.getNomePaciente())
                .replace("[nome do responsável pelo atendimento]", agendamento.getNomeResponsavel())
                .replace("[data do agendamento]", formatarData(agendamento.getDataAtendimento()))
                .replace("[hora do agendamento]", agendamento.getHoraAtendimento());
            
            message.setText(emailBody);
            
            // Envia o e-mail
            mailSender.send(message);
            
            log.info("E-mail de notificação enviado para: {}", agendamento.getEmailPaciente());
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail para {}: {}", agendamento.getEmailPaciente(), e.getMessage(), e);
            // Lançar EmailSendException para que o mecanismo de retry do Kafka possa agir.
            throw new EmailSendException("Falha ao enviar e-mail de notificação.", e);
        }
    }
    
    /**
     * Formata a data de "ddmmaaaa" para "dd/mm/aaaa" para melhor leitura no e-mail.
     * @param data DDMMAAAA
     * @return DD/MM/AAAA
     */
    private String formatarData(String data) {
        if (data != null && data.length() == 8) {
            return data.substring(0, 2) + "/" + data.substring(2, 4) + "/" + data.substring(4, 8);
        }
        return data; // Retorna o original se o formato não for o esperado
    }
}
