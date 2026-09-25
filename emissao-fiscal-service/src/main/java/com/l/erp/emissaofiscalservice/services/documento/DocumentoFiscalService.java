package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.IdempotencyKey;
import com.l.erp.emissaofiscalservice.domain.OutboxEvento;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.repository.IdempotencyKeyRepository;
import com.l.erp.emissaofiscalservice.repository.OutboxEventoRepository;
import com.l.erp.emissaofiscalservice.services.numeracao.NumeracaoDocumentoService;
import com.l.erp.emissaofiscalservice.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Orquestra a criação de um documento fiscal — spec §3 itens 3/7/9/10. Reúne, numa única
 * transação: idempotência (fingerprint), numeração (lock FOR UPDATE), persistência do documento em
 * {@code RASCUNHO} e o evento de outbox — as quatro peças da Etapa 1 que precisam ser atômicas
 * entre si (spec §3 item 10, "escrita na mesma transação que muda o estado do documento").
 *
 * <p>Este serviço nunca chama {@code fiscal-service} nem {@code cadastro-service} (spec §2) — o
 * payload já chega com tudo que é preciso.</p>
 */
@Service
public class DocumentoFiscalService {

    private static final String EVENTO_DOCUMENTO_CRIADO = "DOCUMENTO_CRIADO";
    private static final String EVENTO_DOCUMENTO_TRANSICIONADO = "DOCUMENTO_TRANSICIONADO";

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventoRepository outboxEventoRepository;
    private final NumeracaoDocumentoService numeracaoDocumentoService;
    private final ObjectMapper objectMapper;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                   IdempotencyKeyRepository idempotencyKeyRepository,
                                   OutboxEventoRepository outboxEventoRepository,
                                   NumeracaoDocumentoService numeracaoDocumentoService,
                                   ObjectMapper objectMapper) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventoRepository = outboxEventoRepository;
        this.numeracaoDocumentoService = numeracaoDocumentoService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentoFiscal criar(DocumentoFiscalRequestDTO request, String idempotencyKey) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));

        String fingerprint = calcularFingerprint(request);

        var existente = idempotencyKeyRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existente.isPresent()) {
            if (!existente.get().getFingerprint().equals(fingerprint)) {
                throw new BusinessException(
                        "Idempotency-Key já usada para um documento diferente. Gere uma chave nova.",
                        HttpStatus.CONFLICT);
            }
            return documentoFiscalRepository.findByIdAndTenantId(existente.get().getDocumentoId(), tenantId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency-Key aponta para documento inexistente — inconsistência de dados."));
        }

        long numero = numeracaoDocumentoService.proximoNumero(tenantId, request.emitenteId(), request.documento(), request.serie());

        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setTenantId(tenantId);
        documento.setEmitenteId(request.emitenteId());
        documento.setDocumento(request.documento());
        documento.setModelo(request.modelo());
        documento.setSerie(request.serie());
        documento.setNumero(numero);
        documento.setStatus(StatusDocumentoFiscal.RASCUNHO);
        documento.setAmbiente(request.ambiente());
        documento.setPayloadRecebido(objectMapper.writeValueAsString(request));
        documento.setIdempotencyKey(idempotencyKey);
        OffsetDateTime agora = OffsetDateTime.now();
        documento.setCreatedAt(agora);
        documento.setUpdatedAt(agora);
        documento = documentoFiscalRepository.save(documento);

        IdempotencyKey registroIdempotencia = new IdempotencyKey();
        registroIdempotencia.setTenantId(tenantId);
        registroIdempotencia.setIdempotencyKey(idempotencyKey);
        registroIdempotencia.setDocumentoId(documento.getId());
        registroIdempotencia.setFingerprint(fingerprint);
        registroIdempotencia.setCreatedAt(agora);
        idempotencyKeyRepository.save(registroIdempotencia);

        publicarEvento(documento, EVENTO_DOCUMENTO_CRIADO);

        return documento;
    }

    /** Transição genérica de estado — usada pela Etapa 2+ (assinatura/transmissão/etc.). */
    @Transactional
    public DocumentoFiscal transicionar(UUID documentoId, StatusDocumentoFiscal novoStatus) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));

        DocumentoFiscal documento = documentoFiscalRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new BusinessException("Documento fiscal não encontrado.", HttpStatus.NOT_FOUND));

        if (!documento.getStatus().podeTransicionarPara(novoStatus)) {
            throw new BusinessException(
                    "Transição de " + documento.getStatus() + " para " + novoStatus + " não é permitida.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        documento.setStatus(novoStatus);
        documento.setUpdatedAt(OffsetDateTime.now());
        documento = documentoFiscalRepository.save(documento);

        publicarEvento(documento, EVENTO_DOCUMENTO_TRANSICIONADO);

        return documento;
    }

    private void publicarEvento(DocumentoFiscal documento, String tipoEvento) {
        OutboxEvento evento = new OutboxEvento();
        evento.setDocumentoId(documento.getId());
        evento.setTipoEvento(tipoEvento);
        evento.setPayload(objectMapper.writeValueAsString(DocumentoFiscalEventoPayload.from(documento)));
        evento.setCriadoEm(OffsetDateTime.now());
        outboxEventoRepository.save(evento);
    }

    /** SHA-256 de emitenteId+modelo+destinatário+valorTotal+qtdItens (spec §3 item 10) — nunca o payload cru, nunca o tenantId. */
    private String calcularFingerprint(DocumentoFiscalRequestDTO request) {
        String base = request.emitenteId() + "|" + request.modelo() + "|" + request.destinatarioDocumento()
                + "|" + request.valorTotal().stripTrailingZeros().toPlainString() + "|" + request.quantidadeItens();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM — não deveria acontecer.", e);
        }
    }
}
