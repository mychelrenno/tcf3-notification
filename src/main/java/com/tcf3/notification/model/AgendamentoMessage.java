package com.tcf3.notification.model;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgendamentoMessage {

    @NotBlank(message = "O e-mail de contato do paciente é obrigatório.")
    @Email(message = "O e-mail de contato do paciente deve ser um endereço de e-mail válido.")
    private String emailPaciente;

    @NotBlank(message = "O nome do paciente é obrigatório.")
    private String nomePaciente;
    
    @NotBlank(message = "A data do atendimento é obrigatória.")
    @Pattern(regexp = "\\d{8}", message = "A data do atendimento deve estar no formato ddmmaaaa (8 dígitos).")
    private String dataAtendimento; // Formato ddmmaaa
    
    @NotBlank(message = "A hora do atendimento é obrigatória.")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "A hora do atendimento deve estar no formato hh:mm (ex: 14:30).")
    private String horaAtendimento; // Formato hh:mm
    
    @NotBlank(message = "O nome do responsável pelo atendimento é obrigatório.")
    private String nomeResponsavel;

    private Boolean cancelado;

    // Opcional: Adicionar um método toString para facilitar o log
    @Override
    public String toString() {
        return "AgendamentoMessage{" +
                "emailPaciente='" + emailPaciente + '\'' +
                ", nomePaciente='" + nomePaciente + '\'' +
                ", dataAtendimento='" + dataAtendimento + '\'' +
                ", horaAtendimento='" + horaAtendimento + '\'' +
                ", nomeResponsavel='" + nomeResponsavel + '\'' +
                ", cancelado='" + cancelado + '\'' +
                '}';
    }
}
