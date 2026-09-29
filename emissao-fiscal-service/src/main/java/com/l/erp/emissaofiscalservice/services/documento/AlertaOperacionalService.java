package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.OutboxEvento;
import com.l.erp.emissaofiscalservice.repository.OutboxEventoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avisa o responsável técnico quando um documento precisa de intervenção humana (vai pra {@code ERRO}).
 *
 * <p>Este serviço não envia e-mail: não tem SMTP e é vendável separadamente (spec §2). Grava um evento
 * no outbox — na mesma transação que mudou o estado — e o {@code OutboxPublisherJob} o publica no tópico
 * de alerta; quem tem SMTP entrega. Um chamador externo consome o mesmo evento e avisa pelo canal dele.</p>
 *
 * <p>O destinatário vem de configuração ({@code emissao.alerta.email-destino}) e viaja no evento: o
 * consumidor não precisa conhecer este serviço. Erro de negócio não vai pro Grafana/Loki, que é só pra
 * erro de sistema — por isso o aviso é e-mail e não métrica.</p>
 */
@Service
public class AlertaOperacionalService {

    private final OutboxEventoRepository outboxEventoRepository;
    private final ObjectMapper objectMapper;
    private final String emailDestino;

    public AlertaOperacionalService(OutboxEventoRepository outboxEventoRepository, ObjectMapper objectMapper,
                                    @Value("${emissao.alerta.email-destino}") String emailDestino) {
        this.outboxEventoRepository = outboxEventoRepository;
        this.objectMapper = objectMapper;
        this.emailDestino = emailDestino;
    }

    /** Escreve na transação do chamador — precisa ser a mesma que colocou o documento em ERRO. */
    public void alertarDocumentoEmErro(DocumentoFiscal documento) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", Constants.EMISSAO_ALERTA_TIPO_DOCUMENTO_EM_ERRO);
        payload.put("to", emailDestino);
        payload.put("documentoId", documento.getId().toString());
        payload.put("tenantId", documento.getTenantId());
        payload.put("emitenteId", documento.getEmitenteId().toString());
        payload.put("documento", documento.getDocumento());
        payload.put("serie", documento.getSerie());
        payload.put("numero", documento.getNumero());
        payload.put("motivo", documento.getUltimoErro());
        payload.put("tentativas", documento.getTentativasAssinatura());

        OutboxEvento evento = new OutboxEvento();
        evento.setDocumentoId(documento.getId());
        evento.setTipoEvento(Constants.EMISSAO_EVENTO_ALERTA_OPERACIONAL);
        evento.setPayload(objectMapper.writeValueAsString(payload));
        evento.setCriadoEm(OffsetDateTime.now());
        outboxEventoRepository.save(evento);
    }
}
