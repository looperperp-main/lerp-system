package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.OutboxEvento;
import com.l.erp.emissaofiscalservice.repository.OutboxEventoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/** O alerta vai pelo outbox (mesma transação do estado), com o destinatário dentro do payload. */
@ExtendWith(MockitoExtension.class)
class AlertaOperacionalServiceTest {

    @Mock
    private OutboxEventoRepository outbox;

    @Test
    void gravaEventoDeAlertaNoOutboxComDestinatarioEDadosDoDocumento() throws Exception {
        var json = JsonMapper.builder().build();
        var service = new AlertaOperacionalService(outbox, json, "responsavel@exemplo.com");
        DocumentoFiscal documento = new DocumentoFiscal();
        documento.setTenantId(9L);
        documento.setEmitenteId(UUID.randomUUID());
        documento.setDocumento("NFE");
        documento.setSerie("1");
        documento.setNumero(42L);
        documento.setUltimoErro(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO);
        documento.setTentativasAssinatura((short) 1);
        UUID id = UUID.randomUUID();
        // id é gerado pelo JPA; sem contexto de persistência, injeta por reflexão
        var campo = DocumentoFiscal.class.getDeclaredField("id");
        campo.setAccessible(true);
        campo.set(documento, id);

        service.alertarDocumentoEmErro(documento);

        ArgumentCaptor<OutboxEvento> captor = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(outbox).save(captor.capture());
        OutboxEvento evento = captor.getValue();
        assertEquals(Constants.EMISSAO_EVENTO_ALERTA_OPERACIONAL, evento.getTipoEvento());
        assertEquals(id, evento.getDocumentoId());
        assertNotNull(evento.getCriadoEm());

        JsonNode payload = json.readTree(evento.getPayload());
        assertEquals(Constants.EMISSAO_ALERTA_TIPO_DOCUMENTO_EM_ERRO, payload.get("type").asString());
        assertEquals("responsavel@exemplo.com", payload.get("to").asString());
        assertEquals("NFE", payload.get("documento").asString());
        assertEquals(42, payload.get("numero").asInt());
        assertEquals(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, payload.get("motivo").asString());
        assertEquals(1, payload.get("tentativas").asInt());
    }
}
