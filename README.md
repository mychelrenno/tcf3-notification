# Documentação Técnica do Projeto tcf3-notification

## 1. Introdução

O projeto **tcf3-notification** é um microsserviço assíncrono desenvolvido em Spring Boot, projetado para consumir mensagens de agendamento de atendimentos de um tópico Apache Kafka e enviar notificações por e-mail aos pacientes. O serviço incorpora mecanismos robustos de validação de dados, retentativa (retry) para falhas temporárias e um tópico de Dead Letter (DLQ) para tratamento de mensagens inválidas.

## 2. Visão Geral da Arquitetura

A arquitetura do `tcf3-notification` é baseada em eventos e segue o padrão de microsserviços, utilizando o Kafka como principal canal de comunicação.

O fluxo de processamento de uma mensagem é o seguinte:

1.  **Produção de Mensagem**: Um serviço externo (simulado pelo `AgendamentoController` para testes) envia uma mensagem JSON contendo os detalhes do agendamento para o tópico Kafka `agendamentos`.
2.  **Consumo e Validação**: O `AgendamentoConsumer` recebe a mensagem e realiza a validação do formato dos dados (data e hora) usando o Jakarta Bean Validation.
    - Se houver violações de validação, a mensagem é imediatamente enviada para o tópico DLQ (`agendamentos-dlq`).
3.  **Processamento de E-mail**: Se a mensagem for válida, o `EmailService` é acionado para construir o corpo do e-mail a partir de um template em cache e tentar o envio via SMTP.
4.  **Mecanismo de Retry**: Em caso de falha temporária no envio do e-mail (ex: problema de conexão SMTP), o mecanismo de `DefaultErrorHandler` do Spring Kafka tenta reprocessar a mensagem por **3 vezes** com um intervalo de **5 segundos**.
5.  **Mensagens com falha no envio de e-mail**: Se todas as retentativas falharem, a mensagem permanece no tópico original para ser reprocessada futuramente. Não é enviada para o DLQ.

## 3. Tecnologias Utilizadas

O projeto foi construído com as seguintes tecnologias:

| Tecnologia | Versão | Propósito |
| :--- | :--- | :--- |
| **Java** | 17 (mínimo) | Linguagem de programação principal. |
| **Spring Boot** | 3.2.0 | Framework para desenvolvimento rápido de microsserviços. |
| **Spring Kafka** | 3.1.0 | Integração com o Apache Kafka. |
| **Spring Mail** | 3.2.0 | Envio de notificações por e-mail (SMTP). |
| **Jakarta Validation** | 3.0.0 | Validação de dados no DTO. |
| **Lombok** | 1.18.30 | Redução de código boilerplate (getters, setters, construtores). |
| **Testcontainers** | 1.19.3 | Testes de integração com Kafka e MailHog em contêineres. |

> **Nota sobre a Versão do Spring Boot**: A versão "Spring Boot 5" solicitada não existe. Foi utilizada a versão **3.2.0**, que é a mais recente e compatível com o Java 17 (mínimo) e Java 21.

## 4. Pré-requisitos

Para configurar e executar o projeto, você precisará dos seguintes itens instalados:

*   **Java Development Kit (JDK)**: Versão 17 ou superior.
*   **Apache Maven**: Versão 3.6.0 ou superior.
*   **Docker**: Necessário para executar os testes de integração com Testcontainers.
*   **Servidor Kafka**: Necessário para a execução da aplicação.
*   **Servidor SMTP**: Necessário para o envio de e-mails.

## 5. Setup e Execução

### 5.1. Configuração do Projeto

1.  **Descompacte** o arquivo do projeto.
2.  **Configuração de E-mail**: Edite o arquivo `src/main/resources/application.properties` com as suas credenciais de SMTP.
    ```properties
    # Configurações do Kafka
    spring.kafka.bootstrap-servers=localhost:9092
    
    # Configurações do Email (Substitua pelos seus dados reais)
    spring.mail.host=smtp.example.com
    spring.mail.port=587
    spring.mail.username=seu-email@example.com
    spring.mail.password=sua-senha
    spring.mail.properties.mail.smtp.auth=true
    spring.mail.properties.mail.smtp.starttls.enable=true
    spring.mail.properties.mail.smtp.ssl.trust=smtp.example.com
    ```

### 5.2. Execução da Aplicação

Navegue até o diretório raiz do projeto (`tcf3-notification`) e execute:

```bash
./mvnw spring-boot:run
```

### 5.3. Execução dos Testes

Os testes unitários e de integração podem ser executados com o Maven. **Atenção**: Os testes de integração requerem o **Docker** em execução.

```bash
# Executa todos os testes (unitários e de integração)
mvn test
```

## 6. Componentes Principais

| Componente | Tipo | Descrição |
| :--- | :--- | :--- |
| `AgendamentoMessage` | DTO | Modelo de dados da mensagem Kafka, com validação de formato de data (`ddmmaaaa`) e hora (`hh:mm`). |
| `AgendamentoConsumer` | Consumer | Escuta o tópico `agendamentos`, valida a mensagem e chama o `EmailService`. Mensagens inválidas são enviadas para o DLQ. |
| `DltConsumer` | Consumer | Escuta o tópico DLQ (`agendamentos-dlq`) para processar mensagens inválidas. |
| `EmailService` | Service | Responsável por carregar o template de e-mail (com cache) e realizar o envio via `JavaMailSender`. Lança exceção em caso de falha para acionar o retry. |
| `AgendamentoProducerService` | Service | Encapsula a lógica de envio de mensagens para o Kafka. |
| `AgendamentoController` | Controller | Endpoint REST (`POST /api/agendamentos/enviar`) para facilitar o envio de mensagens de teste. |
| `HealthCheckService` | Service | Verifica o status de conexão com o Kafka e o servidor SMTP. |
| `KafkaConsumerConfig` | Config | Configuração do Kafka, incluindo o `DefaultErrorHandler` com retry (3 tentativas, 5s de intervalo). Mensagens inválidas vão para o DLQ; mensagens válidas com falha no envio de e-mail permanecem no tópico original após os retries. |

## 7. Endpoints de Teste

O projeto expõe dois endpoints REST para facilitar a interação e o monitoramento:

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| **POST** | `/api/agendamentos/enviar` | Envia uma mensagem de agendamento para o tópico Kafka `agendamentos`. Útil para testes manuais. |
| **GET** | `/api/agendamentos/status` | Realiza um Health Check, verificando o status da aplicação, Kafka e SMTP. Retorna HTTP 503 se algum serviço estiver `DOWN`. |

### Exemplo de Requisição POST

**URL:** `http://localhost:8080/api/agendamentos/enviar`

**Corpo (JSON):**

```json
{
  "emailPaciente": "paciente.teste@exemplo.com",
  "nomePaciente": "Maria da Silva",
  "dataAtendimento": "20122025",
  "horaAtendimento": "10:00",
  "nomeResponsavel": "Dr. Ausio Varela"
}
```

## 8. Detalhes de Implementação

### 8.1. Template de E-mail com Cache

O `EmailService` utiliza um mecanismo de cache simples para o template de e-mail, lendo o arquivo `email_template.txt` apenas uma vez na inicialização do serviço.

**Template:**
> Prezado [nome do paciente], você possui um atendimento com [nome do responsável pelo atendimento] agendado para o dia [data do agendamento] às [hora do agendamento].
> Atenciosamente,
> Equipe de atendimento

### 8.2. Mecanismo de Retry e DLQ

O tratamento de falhas é configurado no `KafkaConsumerConfig`:

*   **Validação**: Mensagens com violações de dados são enviadas imediatamente para o DLQ (`agendamentos-dlq`).
*   **Retry**: Mensagens válidas que falham no envio de e-mail são reprocessadas automaticamente até **3 retentativas** com um `FixedBackOff` de **5 segundos**.
*   **Mensagens com falha no envio de e-mail**: Após todas as retentativas, permanecem no tópico original para novo processamento futuro. Não são enviadas para o DLQ.

Este mecanismo garante que mensagens inválidas sejam isoladas para análise posterior, enquanto falhas temporárias de e-mail sejam tratadas automaticamente e, em caso de falha permanente, fiquem disponíveis para reprocessamento.

***
