package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pedido de emissão de NFS-e (spec §4.4), neutro ao provedor: o adaptador (ADN hoje) traduz para o
 * leiaute dele. Cobre o serviço simples de prestador do Simples Nacional/Regime Normal; grupos
 * raros da DPS (obra, exportação, dedução, benefício municipal) ainda não têm campo aqui.
 * Valida só o formato — as regras de negócio vivem na guarda da NFS-e.
 */
public record NfseRequestDTO(
        @NotNull UUID emitenteId,
        @NotBlank String ambiente,
        @NotBlank @Pattern(regexp = "[1-9]\\d{0,4}", message = "série deve ter de 1 a 5 dígitos") String serie,
        @NotNull LocalDate competencia,
        @NotNull @Valid Prestador prestador,
        @Valid Tomador tomador,
        @NotNull @Valid Servico servico,
        @NotNull @Valid Valores valores
) {

    /** {@code opcaoSimplesNacional}: 1=não optante, 2=MEI, 3=ME/EPP. {@code regimeEspecialTributacao}: 0 a 6. */
    public record Prestador(
            @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ do prestador deve ter 14 dígitos") String cnpj,
            @Size(min = 1, max = 15) String inscricaoMunicipal,
            @NotBlank @Size(max = 150) String razaoSocial,
            @NotNull @Valid EnderecoDTO endereco,
            @Email @Size(max = 80) String email,
            @NotBlank @Pattern(regexp = "[123]") String opcaoSimplesNacional,
            @Pattern(regexp = "[123]") String regimeApuracaoSimples,
            @NotBlank @Pattern(regexp = "[0-6]") String regimeEspecialTributacao
    ) {
    }

    /** Tomador é opcional na DPS; {@code documento} é CPF (11) ou CNPJ (14), só dígitos. */
    public record Tomador(
            @NotBlank @Pattern(regexp = "\\d{11}|\\d{14}") String documento,
            @NotBlank @Size(max = 150) String nome,
            @Email @Size(max = 80) String email,
            @Valid EnderecoDTO endereco
    ) {
    }

    /** {@code codigoTributacaoNacional}: 6 dígitos (item, subitem e desdobro da LC 116); municipal: 3 dígitos. */
    public record Servico(
            @NotBlank @Pattern(regexp = "\\d{6}") String codigoTributacaoNacional,
            @Pattern(regexp = "\\d{3}") String codigoTributacaoMunicipal,
            @NotBlank @Size(max = 2000) String descricao,
            @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (código IBGE)") String municipioPrestacao
    ) {
    }

    /**
     * {@code tributacaoIssqn}: 1=tributável, 2=exportação, 3=não incidência, 4=imunidade.
     * {@code aliquotaIssqn} em %, só quando o ISS sai por fora do Simples. {@code tipoRetencaoIssqn}:
     * 1=não retido, 2=retido pelo tomador, 3=retido pelo intermediário.
     */
    public record Valores(
            @NotNull @DecimalMin(value = "0.01") BigDecimal valorServico,
            @NotBlank @Pattern(regexp = "[1-4]") String tributacaoIssqn,
            @DecimalMin("0") @DecimalMax(value = "9.99", message = "alíquota deve ser menor que 10%") BigDecimal aliquotaIssqn,
            @NotBlank @Pattern(regexp = "[1-3]") String tipoRetencaoIssqn
    ) {
    }
}
