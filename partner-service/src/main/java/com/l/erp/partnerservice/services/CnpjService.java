package com.l.erp.partnerservice.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.l.erp.partnerservice.api.dto.CnpjConsultaResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CnpjService {

    private static final Logger logger = LoggerFactory.getLogger(CnpjService.class);
    private static final String OPEN_CNPJ_URL = "https://publica.cnpj.ws/cnpj/";

    private final RestClient restClient = buildRestClient();

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public CnpjConsultaResponseDTO consultar(String cnpj) {
        String normalized = cnpj.replaceAll("[.\\-/]", "").toUpperCase();
        if (normalized.length() != 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ deve conter 14 caracteres");
        }

        logger.debug("Consultando CNPJ na OpenCNPJ");

        try {
            OpenCnpjRawResponse raw = restClient.get()
                    .uri(OPEN_CNPJ_URL + normalized)
                    .retrieve()
                    .body(OpenCnpjRawResponse.class);

            if (raw == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta vazia da OpenCNPJ");
            }

            String situacao = raw.estabelecimento() != null
                    ? raw.estabelecimento().situacaoCadastral()
                    : "";
            boolean ativa = "Ativa".equalsIgnoreCase(situacao);

            String email = raw.estabelecimento() != null ? raw.estabelecimento().email() : null;
            String telefone = buildTelefone(raw.estabelecimento());
            String nomeFantasia = raw.estabelecimento() != null ? raw.estabelecimento().nomeFantasia() : null;

            return new CnpjConsultaResponseDTO(
                    normalized,
                    raw.razaoSocial(),
                    nomeFantasia,
                    situacao,
                    ativa,
                    email,
                    telefone
            );

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CNPJ não encontrado na Receita Federal");
            }
            logger.error("Erro HTTP ao consultar OpenCNPJ: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erro ao consultar OpenCNPJ: " + e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao consultar OpenCNPJ", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erro ao consultar OpenCNPJ");
        }
    }

    private String buildTelefone(Estabelecimento est) {
        if (est == null || est.telefones() == null || est.telefones().isEmpty()) return null;
        Telefone t = est.telefones().getFirst();
        if (t.ddd() != null && t.numero() != null) {
            return "(" + t.ddd() + ") " + t.numero();
        }
        return null;
    }

    // ── Internal DTOs for OpenCNPJ deserialization ─────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenCnpjRawResponse(
            @JsonProperty("razao_social") String razaoSocial,
            @JsonProperty("estabelecimento") Estabelecimento estabelecimento
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Estabelecimento(
            @JsonProperty("nome_fantasia") String nomeFantasia,
            @JsonProperty("situacao_cadastral") String situacaoCadastral,
            @JsonProperty("email") String email,
            @JsonProperty("telefones") List<Telefone> telefones
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Telefone(
            @JsonProperty("ddd") String ddd,
            @JsonProperty("numero") String numero
    ) {}
}