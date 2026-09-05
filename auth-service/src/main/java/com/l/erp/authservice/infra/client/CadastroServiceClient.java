package com.l.erp.authservice.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Client HTTP pro cadastro-service via Eureka (RestClientConfig) — onboarding síncrono do
 * tenant (Fase 4, spec/estabelecimentos-filiais.md §6): cria a Pessoa própria (PJ) do tenant
 * recém-criado e marca a matriz dela como proprio=true. Repassa os mesmos headers internos
 * que o gateway injeta (mesmo padrão de operacoes-service/infra/client/CadastroServiceClient).
 */
@Component
public class CadastroServiceClient {

    private final RestClient restClient;

    @Value("${internal.gateway.secret}")
    private String internalSecret;

    public CadastroServiceClient(@LoadBalanced RestClient.Builder restClientBuilder,
                                  @Value("${cadastro-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /** Cria a Pessoa PJ própria do tenant e marca sua matriz como proprio=true. Retorna o id da Pessoa. */
    public UUID provisionarPessoaPropria(Tenant tenant, UUID userId) {
        try {
            PessoaRequest request = new PessoaRequest("PJ", tenant.getName(), tenant.getNomeFantasia(),
                    tenant.getCnpj(), tenant.getInscricaoEstadual(), null, null, null, Boolean.TRUE);
            PessoaRef pessoa = restClient.post()
                    .uri("/api/v1/pessoas")
                    .headers(headers -> headersInternos(headers, tenant.getId(), userId))
                    .body(request)
                    .retrieve()
                    .body(PessoaRef.class);
            if (pessoa == null || pessoa.id() == null) {
                throw new BusinessException(Constants.CADASTRO_SERVICE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
            }

            restClient.patch()
                    .uri("/api/v1/pessoas/{pessoaId}/estabelecimentos/matriz/proprio", pessoa.id())
                    .headers(headers -> headersInternos(headers, tenant.getId(), userId))
                    .retrieve()
                    .toBodilessEntity();

            return pessoa.id();
        } catch (RestClientException e) {
            throw new BusinessException(Constants.CADASTRO_SERVICE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void headersInternos(HttpHeaders headers, Long tenantId, UUID userId) {
        headers.add(Constants.HEADER_INTERNAL_SECRET, internalSecret);
        headers.add(Constants.HEADER_TENANT_ID, String.valueOf(tenantId));
        headers.add(Constants.HEADER_USER_ID, userId.toString());
    }

    // Records locais espelhando só os campos usados de PessoaRequestDTO/PessoaResponseDTO do
    // cadastro-service — evita acoplar auth-service ao módulo cadastro-service.
    private record PessoaRequest(String tipo, String nomeRazao, String apelidoFantasia, String documento,
                                  String ie, String im, String rg, java.time.LocalDate dataNascimento, Boolean ativo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PessoaRef(UUID id) {
    }
}
