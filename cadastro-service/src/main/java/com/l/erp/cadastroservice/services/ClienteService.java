package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.ClienteDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.repository.ClienteRepository;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.VendedorRepository;
import com.l.erp.common.util.Constants;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.exception.custom.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class ClienteService {
    private final Logger logger = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clienteRepository;
    private final PessoaRepository pessoaRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final GrupoClienteRepository grupoClienteRepository;
    private final VendedorRepository vendedorRepository;
    private final AuditProducerService auditProducer;

    public ClienteService(ClienteRepository clienteRepository,
                          PessoaRepository pessoaRepository,
                          CondicaoPagamentoRepository condicaoPagamentoRepository,
                          GrupoClienteRepository grupoClienteRepository,
                          VendedorRepository vendedorRepository,
                          AuditProducerService auditProducer) {
        this.clienteRepository = clienteRepository;
        this.pessoaRepository = pessoaRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.grupoClienteRepository = grupoClienteRepository;
        this.vendedorRepository = vendedorRepository;
        this.auditProducer = auditProducer;
    }

    @Transactional(readOnly = true)
    public Page<Cliente> getAllClientes(Long tenantId, Pageable pageable) {
        logger.info("Buscando todos os clientes do tenant {}", tenantId);
        return clienteRepository.findAllByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Cliente findById(UUID id, Long tenantId) {
        return clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException(Constants.CLIENTE_NOT_FOUND + Constants._ID + id));
    }

    @Transactional
    public Cliente save(ClienteDTO dto, Long tenantId, UUID userId) {
        logger.info("Salvando novo cliente no tenant {}", tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        // 1. Validação de Unicidade: Uma Pessoa só pode ser um Cliente uma única vez por Tenant
        if (clienteRepository.existsByTenantIdAndPessoaId(tenantId, dto.pessoaId())) {
            sendAuditEvent(Constants.CLIENTE_CREATION, userId, null, Constants.ERROR, "{ERROR: Cliente já existe para esta Pessoa}", correlationID);
            throw new BusinessException(Constants.CLIENTE_ALREADY_EXISTS + " - pessoaId: " + dto.pessoaId(), HttpStatus.BAD_REQUEST);
        }

        // 2. Busca e valida entidades vinculadas
        Pessoa pessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                .orElseThrow(() -> new BusinessException(Constants.PESSOA_NOT_FOUND, HttpStatus.BAD_REQUEST));

        CondicaoPagamento condicaoPagamento = null;
        if (dto.condicaoPagamentoId() != null) {
            condicaoPagamento = condicaoPagamentoRepository.findByIdAndTenantId(dto.condicaoPagamentoId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.COND_PAG_NOT_FOUND, HttpStatus.BAD_REQUEST));
        }

        GrupoCliente grupoCliente = null;
        if (dto.grupoClienteId() != null) {
            grupoCliente = grupoClienteRepository.findByIdAndTenantId(dto.grupoClienteId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.GROUP_C_NOT_FOUND, HttpStatus.BAD_REQUEST));
        }

        Vendedor vendedor = null;
        if (dto.vendedorId() != null) {
            vendedor = vendedorRepository.findByIdAndTenantId(dto.vendedorId(), tenantId)
                    .orElseThrow(() -> new BusinessException(Constants.VENDEDOR_NOT_FOUND, HttpStatus.BAD_REQUEST));
        }

        // 3. Monta a entidade
        Cliente cliente = Cliente.builder()
                .pessoa(pessoa)
                .codigoInterno(dto.codigoInterno())
                .condicaoPagamento(condicaoPagamento)
                .grupoCliente(grupoCliente)
                .vendedor(vendedor)
                .limiteCredito(dto.limiteCredito())
                .classificacaoRisco(dto.classificacaoRisco())
                .prazoMedioPagamentoDias(dto.prazoMedioPagamentoDias())
                .ativo(dto.ativo())
                .createdAt(Instant.now())
                .createdBy(userId)
                .build();
        cliente.setTenantId(tenantId);

        Cliente saved = clienteRepository.save(cliente);

        sendAuditEvent(Constants.CLIENTE_CREATION, userId, saved.getId(), Constants.SUCCESS, null, correlationID);
        return saved;
    }

    @Transactional
    public Cliente update(UUID id, ClienteDTO dto, Long tenantId, UUID userId) {
        logger.info("Atualizando cliente {} no tenant {}", id, tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.CLIENTE_UPDATE, userId, null, Constants.ERROR, "{ERROR: Cliente não encontrado}", correlationID);
                    return new BusinessException(Constants.CLIENTE_NOT_FOUND+Constants._ID + id,HttpStatus.BAD_REQUEST);
                });

        // Se trocou de Pessoa (raro mas possível), valida se a nova pessoa já não é cliente
        if (!cliente.getPessoa().getId().equals(dto.pessoaId())) {
            if (clienteRepository.existsByTenantIdAndPessoaId(tenantId, dto.pessoaId())) {
                sendAuditEvent(Constants.CLIENTE_UPDATE, userId, null, Constants.ERROR, "{ERROR: A nova Pessoa já possui cadastro de Cliente}", correlationID);
                throw new BusinessException(Constants.CLIENTE_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
            }
            Pessoa novaPessoa = pessoaRepository.findByIdAndTenantId(dto.pessoaId(), tenantId)
                    .orElseThrow(() -> new RuntimeException(Constants.PESSOA_NOT_FOUND));
            cliente.setPessoa(novaPessoa);
        }

        // Atualiza Relacionamentos Nulos ou Novos
        cliente.setCondicaoPagamento(dto.condicaoPagamentoId() != null ?
                condicaoPagamentoRepository.findByIdAndTenantId(dto.condicaoPagamentoId(), tenantId).orElse(null) : null);

        cliente.setGrupoCliente(dto.grupoClienteId() != null ?
                grupoClienteRepository.findByIdAndTenantId(dto.grupoClienteId(), tenantId).orElse(null) : null);

        cliente.setVendedor(dto.vendedorId() != null ?
                vendedorRepository.findByIdAndTenantId(dto.vendedorId(), tenantId).orElse(null) : null);

        // Atualiza campos
        cliente.setCodigoInterno(dto.codigoInterno());
        cliente.setLimiteCredito(dto.limiteCredito());
        cliente.setClassificacaoRisco(dto.classificacaoRisco());
        cliente.setPrazoMedioPagamentoDias(dto.prazoMedioPagamentoDias());
        cliente.setAtivo(dto.ativo());
        cliente.setUpdatedAt(Instant.now());
        cliente.setLastUpdatedBy(userId);

        Cliente updated = clienteRepository.save(cliente);

        sendAuditEvent(Constants.CLIENTE_UPDATE, userId, updated.getId(), Constants.SUCCESS, null, correlationID);
        return updated;
    }

    @Transactional
    public void updateStatus(UUID id, Long tenantId, UUID userId) {
        logger.info("Alterando status do cliente {} no tenant {}", id, tenantId);
        UUID correlationID = getCorrelationIdFromRequest(logger);

        Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> {
                    sendAuditEvent(Constants.CLIENTE_UPDATE, userId, null, Constants.ERROR, "{ERROR: Cliente não encontrado}", correlationID);
                    return new BusinessException(Constants.CLIENTE_NOT_FOUND + Constants._ID + id, HttpStatus.BAD_REQUEST);
                });

        cliente.setAtivo(!cliente.getAtivo());
        cliente.setUpdatedAt(Instant.now());
        cliente.setLastUpdatedBy(userId);

        clienteRepository.save(cliente);

        sendAuditEvent(Constants.CLIENTE_UPDATE, userId, id, Constants.SUCCESS, "{\"status_alterado\": " + cliente.getAtivo() + "}", correlationID);
    }

    private void sendAuditEvent(String action, UUID actorId, UUID targetId, String result, String detailsJson, UUID correlationId) {
        AuditEventDTO auditEvent = new AuditEventDTO(
                action, actorId, Constants.CLIENTE, targetId, result, detailsJson, correlationId, Instant.now()
        );
        auditProducer.sendAuditEvent(auditEvent);
    }

    @Transactional
    public void delete(UUID id, Long tenantId, UUID userId) {
        UUID correlationID = getCorrelationIdFromRequest(logger);
        logger.info("Deletando cliente {} no tenant {}", id, tenantId);
        Cliente cliente = clienteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.CLIENTE_NOT_FOUND + Constants._ID + id, HttpStatus.BAD_REQUEST));
        if(Boolean.TRUE.equals(cliente.getAtivo())){
            sendAuditEvent(Constants.CLIENTE_DELETE, userId, id, Constants.ERROR, "{"+Constants.ERROR+" Erro ao excluir Cliente Ativo}", correlationID);
            throw new BusinessException("Erro ao remover Cliente: Cliente Ativo", HttpStatus.BAD_REQUEST);
        }
        long deletedCount = clienteRepository.deleteByIdAndTenantId(id, tenantId);
        if (deletedCount == 0) {
            sendAuditEvent(Constants.CLIENTE_DELETE, userId, id, Constants.ERROR, "{"+Constants.ERROR+" Nenhum registro deletado/encontrado}", correlationID);
            throw new BusinessException(Constants.CLIENTE_NOT_FOUND + Constants._ID + id, HttpStatus.BAD_REQUEST);
        }
        sendAuditEvent(Constants.CLIENTE_DELETE, userId, id, Constants.SUCCESS, null, correlationID);
    }
}
