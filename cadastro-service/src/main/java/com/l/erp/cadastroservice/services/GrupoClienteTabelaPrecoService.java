package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoClienteId;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoGrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.exception.custom.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Slf4j
@Service
public class GrupoClienteTabelaPrecoService {

    private final Logger logger = LoggerFactory.getLogger(GrupoClienteTabelaPrecoService.class);

    private final TabelaPrecoGrupoClienteRepository repository;
    private final GrupoClienteRepository grupoClienteRepository;
    private final TabelaPrecoRepository tabelaPrecoRepository;
    private final AuditProducerService auditProducer;

    public GrupoClienteTabelaPrecoService(TabelaPrecoGrupoClienteRepository repository, GrupoClienteRepository grupoClienteRepository, TabelaPrecoRepository tabelaPrecoRepository, AuditProducerService auditProducer) {
        this.repository = repository;
        this.grupoClienteRepository = grupoClienteRepository;
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public List<TabelaPrecoGrupoCliente> getAssociacoes(UUID grupoClienteId, Long tenantId) {
        log.info("Buscando tabelas de preço do grupo de cliente {} do tenant {}", grupoClienteId, tenantId);
        return repository.findAllByGrupoClienteIdAndTenantId(grupoClienteId, tenantId);
    }

    @Transactional
    public void sincronizarAssociacoes(UUID grupoClienteId, List<UUID> tabelaPrecoIds, Long tenantId, UUID userId) {
        log.info("Sincronizando tabelas de preço para o grupo {} do tenant {}", grupoClienteId, tenantId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        GrupoCliente grupo = grupoClienteRepository.findByIdAndTenantId(grupoClienteId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.GROUP_C_NOT_FOUND));

        // Removemos todas as associações atuais DESTE GRUPO
        repository.deleteAllByGrupoClienteIdAndTenantId(grupoClienteId, tenantId);

        // Se vieram IDs na requisição, adicionamos as novas associações
        if (tabelaPrecoIds != null && !tabelaPrecoIds.isEmpty()) {
            List<TabelaPrecoGrupoCliente> novasAssociacoes = tabelaPrecoIds.stream().map(tabelaId -> {
                TabelaPreco tabela = tabelaPrecoRepository.findByIdAndTenantId(tabelaId, tenantId)
                        .orElseThrow(() -> new RuntimeException(Constants.TABELA_PRECO_NOT_FOUND + ": " + tabelaId));

                TabelaPrecoGrupoClienteId id = new TabelaPrecoGrupoClienteId();
                id.setGrupoClienteId(grupoClienteId);
                id.setTabelaPrecoId(tabelaId);

                TabelaPrecoGrupoCliente associacao = new TabelaPrecoGrupoCliente();
                associacao.setId(id);
                associacao.setGrupoCliente(grupo);
                associacao.setTabelaPreco(tabela);
                associacao.setTenantId(tenantId);

                return associacao;
            }).collect(Collectors.toList());

            repository.saveAll(novasAssociacoes);

            sendAuditEvent(Constants.GRP_C_TABELA_PRECO_ASSOCIACAO, userId, grupoClienteId, Constants.SUCCESS, null, correlationId);
        }
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.GRP_C_TABELA_PRECO, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }
}
