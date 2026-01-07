package com.tcf3.notification.service;

import com.tcf3.notification.exception.EmailSendException;
import com.tcf3.notification.exception.TemplateLoadException;
import com.tcf3.notification.model.AgendamentoMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @InjectMocks
    private EmailService emailService;

    private final String TEMPLATE_CONTENT = "Prezado [nome do paciente], você possui um atendimento com [nome do responsável pelo atendimento] agendado para o dia [data do agendamento] às [hora do agendamento].\n\nAtenciosamente,\nEquipe de atendimento";
    private final String TEMPLATE_PATH = "classpath:email_template.txt";
    private final String TEMPLATE_CANCEL_PATH = "classpath:email_template_cancelamento.txt";

    @BeforeEach
    void setUp() throws IOException {
        // Configuração do mock para o carregamento do template (mecanismo de cache)
        when(resourceLoader.getResource(TEMPLATE_PATH)).thenReturn(resource);
        when(resourceLoader.getResource(TEMPLATE_CANCEL_PATH)).thenReturn(resource);
        InputStream inputStream = new ByteArrayInputStream(TEMPLATE_CONTENT.getBytes());
        when(resource.getInputStream()).thenReturn(inputStream);

        // Chama o método @PostConstruct manualmente para simular a inicialização do cache
        emailService.loadEmailTemplate();
    }

    @Test
    void loadEmailTemplate_shouldCacheTemplateContent() throws Exception {
        // Acessa o campo privado emailTemplateCache por reflexão
        Field cacheField = EmailService.class.getDeclaredField("emailTemplateCache");
        cacheField.setAccessible(true);
        String cached = (String) cacheField.get(emailService);

        // Verifica se o template foi carregado e não está vazio
        assertNotNull(cached);
        assertEquals(TEMPLATE_CONTENT, cached);

        // Verifica se o resourceLoader foi chamado apenas uma vez para o template padrão
        verify(resourceLoader, times(1)).getResource(TEMPLATE_PATH);
    }

    @Test
    void loadEmailTemplate_shouldThrowTemplateLoadExceptionOnLoadFailure() throws Exception {
        // Simula uma falha na leitura do arquivo
        when(resource.getInputStream()).thenThrow(new IOException("Simulated IO Error"));

        // Cria uma nova instância para testar a falha na inicialização
        EmailService failingService = new EmailService();

        // Injeta mocks privados por reflexão (mailSender e resourceLoader)
        Field mailSenderField = EmailService.class.getDeclaredField("mailSender");
        mailSenderField.setAccessible(true);
        mailSenderField.set(failingService, mailSender);

        Field resourceLoaderField = EmailService.class.getDeclaredField("resourceLoader");
        resourceLoaderField.setAccessible(true);
        resourceLoaderField.set(failingService, resourceLoader);

        // Verifica se a exceção correta é lançada
        assertThrows(TemplateLoadException.class, failingService::loadEmailTemplate);
    }

    @Test
    void sendNotificationEmail_shouldSendCorrectEmail() {
        // Dados de teste
        AgendamentoMessage agendamento = new AgendamentoMessage(
            "paciente@teste.com",
            "João Teste",
            "01012026",
            "10:30",
            "Dr. Mockito",
            false
        );

        // Executa o método
        emailService.sendNotificationEmail(agendamento);

        // Captura a mensagem enviada
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        // Verifica os campos do e-mail
        assertEquals("Confirmação de Agendamento de Atendimento", sentMessage.getSubject());
        assertEquals("paciente@teste.com", sentMessage.getTo()[0]);

        // Verifica o corpo do e-mail com a substituição do template
        String expectedBody = "Prezado João Teste, você possui um atendimento com Dr. Mockito agendado para o dia 01/01/2026 às 10:30.\n\nAtenciosamente,\nEquipe de atendimento";
        assertEquals(expectedBody, sentMessage.getText());
    }

    @Test
    void sendNotificationEmail_shouldThrowEmailSendExceptionOnMailFailure() {
        // Dados de teste
        AgendamentoMessage agendamento = new AgendamentoMessage(
            "paciente@teste.com",
            "João Teste",
            "01012026",
            "10:30",
            "Dr. Mockito",
            false
        );

        // Simula uma falha no envio do e-mail
        doThrow(new RuntimeException("Simulated Mail Error")).when(mailSender).send(any(SimpleMailMessage.class));

        // Verifica se a exceção correta é lançada (para acionar o retry do Kafka)
        assertThrows(EmailSendException.class, () -> emailService.sendNotificationEmail(agendamento));
    }
}
