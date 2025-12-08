package com.tcf3.notification;

import com.tcf3.notification.consumer.AppointmentConsumer;
import com.tcf3.notification.model.AgendamentoMessage;
import com.tcf3.notification.service.EmailService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Testes de integração: usa Testcontainers por padrão, mas aceita um Kafka externo
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class KafkaIntegrationTest {

    private static final String TOPIC = "agendamentos";
    private static final String DLT_TOPIC = "agendamentos.DLT";

    // Se a variável de ambiente SPRING_KAFKA_BOOTSTRAP_SERVERS estiver setada
    // usamos o Kafka externo; caso contrário inicializamos um KafkaContainer.
    private static KafkaContainer KAFKA_CONTAINER;
    private static String bootstrapServers;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        // Verifica se foi fornecido um Kafka externo (docker-compose) via env var
        String external = System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS");
        if (external != null && !external.isBlank()) {
            bootstrapServers = external;
            registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        } else {
            // Inicializa Testcontainers Kafka e registra o bootstrap
            KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
            KAFKA_CONTAINER.start();
            bootstrapServers = KAFKA_CONTAINER.getBootstrapServers();
            registry.add("spring.kafka.bootstrap-servers", () -> bootstrapServers);
        }
        // Desabilita o envio de e-mail para o teste de integração
        registry.add("spring.mail.host", () -> "mock-smtp-host"); 
    }

    @Autowired
    private KafkaTemplate<String, AgendamentoMessage> kafkaTemplate;

    @MockBean
    private EmailService emailService;

    @SpyBean
    private AppointmentConsumer appointmentConsumer;

    private Consumer<String, AgendamentoMessage> dltConsumer;

    @BeforeEach
    void setUp() {
        // Limpa as interações do mock para garantir que não há resíduos de outros testes
        reset(emailService);

        // Configuração do consumidor DLT para verificar se a mensagem chegou lá
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-group", "true", bootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        // Corrige explicitamente a propriedade enable.auto.commit para evitar sobrescrita incorreta
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        // Corrige explicitamente a propriedade bootstrap.servers
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        DefaultKafkaConsumerFactory<String, AgendamentoMessage> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        dltConsumer = cf.createConsumer();
        dltConsumer.subscribe(Collections.singletonList(DLT_TOPIC));
    }

    @Test
    void whenInvalidMessageIsSent_thenValidationFailsAndEmailIsNotSent() throws Exception {
        // Arrange
        // Mensagem inválida: data no formato errado (dd/mm/aaaa)
        AgendamentoMessage invalidMessage = new AgendamentoMessage(
            "invalid@email.com", "Paciente Teste", "01/01/2026", "10:00", "Dr. Teste"
        );

        // Act
        kafkaTemplate.send(TOPIC, invalidMessage);

        // Espera um tempo para o consumidor processar a mensagem
        Thread.sleep(3000); 

        // Assert
        // O emailService não deve ser chamado
        verify(emailService, never()).sendNotificationEmail(any());
        // A mensagem não deve ir para o DLT (pois falhou na validação antes do processamento)
        ConsumerRecords<String, AgendamentoMessage> dltRecords = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(1));
        assertTrue(dltRecords.isEmpty());
    }

    @Test
    void sanityCheck_AppointmentConsumerUsesMockedEmailService() {
        assertNotNull(appointmentConsumer, "O AppointmentConsumer deve estar injetado");
        assertNotNull(emailService, "O EmailService mock deve estar injetado");
        // Verifica se o EmailService do AppointmentConsumer é o mesmo mock
        try {
            java.lang.reflect.Field field = AppointmentConsumer.class.getDeclaredField("emailService");
            field.setAccessible(true);
            Object injectedService = field.get(appointmentConsumer);
            assertSame(emailService, injectedService, "O AppointmentConsumer deve usar o mock do EmailService");
        } catch (Exception e) {
            fail("Não foi possível acessar o campo emailService do AppointmentConsumer: " + e.getMessage());
        }
    }

}
