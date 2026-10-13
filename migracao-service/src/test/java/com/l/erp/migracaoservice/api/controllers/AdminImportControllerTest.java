package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.exception.handlers.GlobalExceptionHandler;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.AdminImportResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.OnboardingSla;
import com.l.erp.migracaoservice.api.dto.ImportDtos.PainelSla;
import com.l.erp.migracaoservice.services.imports.ImportConciergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone: status, repasse dos parâmetros e download. O @PreAuthorize (APP_OWNER) fica para o teste de integração. */
@ExtendWith(MockitoExtension.class)
class AdminImportControllerTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock ImportConciergeService concierge;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminImportController(concierge))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static AdminImportResponse item(boolean vencido) {
        return new AdminImportResponse(7L, new ImportBatchResponse(ID, "CUSTOMER", "AWAITING_CONCIERGE", "CONCIERGE",
                "clientes.pdf", 0, 0, 0, 0, 0, null, null, OffsetDateTime.now(), OffsetDateTime.now(),
                OffsetDateTime.now().minusHours(3), 3, true), vencido);
    }

    @Test
    void filaRepassaAPaginaEMostraPrazoVencidoEEscalonamento() throws Exception {
        when(concierge.listarFila(1, 10)).thenReturn(new Pagina<>(List.of(item(true)), 1, 1, 10));

        mvc.perform(get("/migracao/admin/imports").param("pagina", "1").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].tenantId").value(7))
                .andExpect(jsonPath("$.itens[0].prazoVencido").value(true))
                .andExpect(jsonPath("$.itens[0].lote.escalado").value(true))
                .andExpect(jsonPath("$.itens[0].lote.recusasConcierge").value(3));
    }

    @Test
    void painelDeSlaTrazAFilaEOsOnboardings() throws Exception {
        when(concierge.painel()).thenReturn(new PainelSla(1, 1, 1, List.of(item(true)),
                List.of(new OnboardingSla(7L, OffsetDateTime.now().minusDays(9), null, 216, List.of("CUSTOMER"), true))));

        mvc.perform(get("/migracao/admin/imports/sla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prazosVencidos").value(1))
                .andExpect(jsonPath("$.onboardings[0].atrasado").value(true))
                .andExpect(jsonPath("$.onboardings[0].entidadesConcluidas[0]").value("CUSTOMER"));
    }

    @Test
    void previaEnviaOArquivoEDevolveOLote() throws Exception {
        when(concierge.carregarPrevia(any(), any())).thenReturn(item(false));

        mvc.perform(multipart("/migracao/admin/imports/" + ID + "/previa")
                        .file(new MockMultipartFile("arquivo", "normalizado.csv", "text/csv", "a;b\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lote.trilha").value("CONCIERGE"));
    }

    @Test
    void previaEmLoteForaDaFilaE409() throws Exception {
        when(concierge.carregarPrevia(any(), any()))
                .thenThrow(new BusinessException(Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL, HttpStatus.CONFLICT));

        mvc.perform(multipart("/migracao/admin/imports/" + ID + "/previa")
                        .file(new MockMultipartFile("arquivo", "n.csv", "text/csv", "a\n".getBytes())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL));
    }

    @Test
    void arquivoDoClienteBaixaComONomeOriginalESemCache() throws Exception {
        when(concierge.arquivoDoCliente(ID)).thenReturn(new ByteArrayResource("pdf".getBytes()));
        when(concierge.nomeDoArquivo(ID)).thenReturn("clientes.pdf");

        mvc.perform(get("/migracao/admin/imports/" + ID + "/arquivo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("clientes.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes("pdf".getBytes()));
    }

    @Test
    void pedidoSemArquivoE404() throws Exception {
        when(concierge.arquivoDoCliente(ID)).thenThrow(new BusinessException(Constants.IMPORT_CONCIERGE_SEM_ARQUIVO, HttpStatus.NOT_FOUND));

        mvc.perform(get("/migracao/admin/imports/" + ID + "/arquivo")).andExpect(status().isNotFound());
    }
}
