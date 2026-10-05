package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
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
import java.util.function.Supplier;

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
    private final DocumentoFiscalGuardaService guardaService;
    private final GuardaNfseService guardaNfseService;
    private final ProvedorNfseResolver provedorNfseResolver;
    private final ObjectMapper objectMapper;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                   IdempotencyKeyRepository idempotencyKeyRepository,
                                   OutboxEventoRepository outboxEventoRepository,
                                   NumeracaoDocumentoService numeracaoDocumentoService,
                                   DocumentoFiscalGuardaService guardaService,
                                   GuardaNfseService guardaNfseService,
                                   ProvedorNfseResolver provedorNfseResolver,
                                   ObjectMapper objectMapper) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventoRepository = outboxEventoRepository;
        this.numeracaoDocumentoService = numeracaoDocumentoService;
        this.guardaService = guardaService;
        this.guardaNfseService = guardaNfseService;
        this.provedorNfseResolver = provedorNfseResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DocumentoFiscal buscar(UUID documentoId) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
        return documentoFiscalRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_DOCUMENTO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public DocumentoFiscal criar(DocumentoFiscalRequestDTO request, String idempotencyKey) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
        return registrar(tenantId, idempotencyKey, calcularFingerprint(request), request, request.emitenteId(),
                request.documento(), request.modelo(), request.serie(), request.ambiente(), null,
                () -> guardaService.validar(tenantId, request));
    }

    /**
     * NFS-e: mesma criação atômica da NF-e (idempotência, numeração, outbox), com a guarda própria e o
     * provedor gravado no documento — cancelar e consultar depois vão sempre pelo provedor que emitiu.
     */
    @Transactional
    public DocumentoFiscal criarNfse(NfseRequestDTO pedido, String idempotencyKey) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
        return registrar(tenantId, idempotencyKey, calcularFingerprint(pedido), pedido, pedido.emitenteId(),
                TipoDocumentoFiscal.NFSE.name(), null, pedido.serie(), pedido.ambiente(),
                () -> provedorNfseResolver.configuracaoDe(tenantId, pedido.emitenteId()).getProvedor(),
                () -> guardaNfseService.validar(tenantId, pedido));
    }

    private DocumentoFiscal registrar(Long tenantId, String idempotencyKey, String fingerprint, Object payload,
                                      UUID emitenteId, String tipo, String modelo, String serie, String ambiente,
                                      Supplier<CodigoProvedorNfse> provedor, Runnable guarda) {
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

        // Depois do replay de idempotência de propósito: reenvio de uma nota já aceita não pode falhar
        // porque o certificado venceu nesse meio-tempo. Antes da numeração: rejeição não gasta número.
        guarda.run();

        long numero = numeracaoDocumentoService.proximoNumero(tenantId, emitenteId, tipo, serie);

        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setTenantId(tenantId);
        documento.setEmitenteId(emitenteId);
        documento.setDocumento(tipo);
        documento.setModelo(modelo);
        documento.setSerie(serie);
        documento.setNumero(numero);
        documento.setStatus(StatusDocumentoFiscal.RASCUNHO);
        documento.setAmbiente(ambiente);
        documento.setProvedor(provedor == null ? null : provedor.get());
        documento.setPayloadRecebido(objectMapper.writeValueAsString(payload));
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

        return aplicarTransicao(documento, novoStatus);
    }

    /**
     * Transição chamada pelos jobs assíncronos, que já têm o documento carregado e rodam sem
     * requisição (sem tenant de header). Mesma máquina de estados e mesmo evento de outbox do
     * {@link #transicionar}; escreve na transação do chamador, que precisa ser a mesma que gravou o
     * que motivou a transição (spec §3 item 10, atomicidade estado + outbox).
     */
    public DocumentoFiscal aplicarTransicao(DocumentoFiscal documento, StatusDocumentoFiscal novoStatus) {
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
        return sha256(request.emitenteId() + "|" + request.modelo() + "|" + request.destinatario().documento()
                + "|" + request.valorTotal().stripTrailingZeros().toPlainString() + "|" + request.itens().size());
    }

    /** NFS-e: emitente + série + competência + item LC 116 + tomador + valor — o que distingue uma nota de outra. */
    private String calcularFingerprint(NfseRequestDTO pedido) {
        String tomador = pedido.tomador() == null ? "" : pedido.tomador().documento();
        return sha256(pedido.emitenteId() + "|NFSE|" + pedido.serie() + "|" + pedido.competencia() + "|"
                + pedido.servico().codigoTributacaoNacional() + "|" + tomador + "|"
                + pedido.valores().valorServico().stripTrailingZeros().toPlainString());
    }

    private static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM — não deveria acontecer.", e);
        }
    }
}
