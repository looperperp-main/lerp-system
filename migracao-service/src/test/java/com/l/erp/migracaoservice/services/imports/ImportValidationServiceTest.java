package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportValidationServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID ID = UUID.randomUUID();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Mock ImportBatchRepository batches;
    @Mock ImportRowRepository rows;
    @Mock CanonicalSchemaRepository schema;

    private ImportValidationService service;

    @BeforeEach
    void setUp() {
        service = new ImportValidationService(batches, rows, schema, new ValidadorLinha(), MAPPER);
    }

    private void batchDe(String entidade) {
        when(batches.iniciarValidacao(ID, TENANT)).thenReturn(true);
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(new ImportBatch(ID, TENANT, UUID.randomUUID(), entidade, "1.0",
                "VALIDATING", "TEMPLATE", "a.csv", "7/x/original.csv", 3, null, OffsetDateTime.now(), OffsetDateTime.now())));
        when(schema.ativos(entidade, "1.0")).thenReturn(
                entidade.equals("CUSTOMER") ? CamposFixture.cliente() : CamposFixture.produto());
    }

    private static LinhaStaging cliente(long id, int numero, String tipo, String nome, String documento) {
        Map<String, String> m = new HashMap<>();
        m.put("tipo", tipo);
        m.put("nome_razao", nome);
        m.put("documento", documento);
        return new LinhaStaging(id, numero, MAPPER.writeValueAsString(m));
    }

    private static LinhaStaging produto(long id, int numero, String sku) {
        Map<String, String> m = new HashMap<>();
        m.put("sku", sku);
        m.put("nome", "Cabo");
        m.put("tipo", "MERCADORIA");
        m.put("unidade", "UN");
        return new LinhaStaging(id, numero, MAPPER.writeValueAsString(m));
    }

    @SuppressWarnings("unchecked")
    private List<Resultado> resultadosGravados() {
        ArgumentCaptor<List<Resultado>> captor = ArgumentCaptor.forClass(List.class);
        verify(rows).gravarResultados(eq(TENANT), captor.capture());
        return captor.getValue();
    }

    @Test
    void tudoValidoFechaEmReady() {
        batchDe("CUSTOMER");
        when(rows.proximas(ID, TENANT, 0L, Constants.IMPORT_LOTE)).thenReturn(List.of(
                cliente(10, 2, "PF", "Maria", "529.982.247-25"), cliente(11, 3, "PJ", "Acme", "11.222.333/0001-81")));
        when(rows.proximas(ID, TENANT, 11L, Constants.IMPORT_LOTE)).thenReturn(List.of());

        service.validar(ID, TENANT);

        List<Resultado> r = resultadosGravados();
        assertThat(r).extracting(Resultado::status).containsOnly("VALID");
        assertThat(r).extracting(Resultado::errosJson).containsOnlyNulls();
        verify(batches).concluirValidacao(ID, TENANT, Constants.IMPORT_STATUS_READY);
    }

    @Test
    void linhaInvalidaEChaveRepetidaFechamEmAwaitingFix() {
        batchDe("CUSTOMER");
        when(rows.proximas(ID, TENANT, 0L, Constants.IMPORT_LOTE)).thenReturn(List.of(
                cliente(10, 2, "PF", "Maria", "529.982.247-25"),
                cliente(11, 3, "PF", "Maria de novo", "52998224725"),        // mesmo CPF sem máscara
                cliente(12, 4, "PF", "Joao", "529.982.247-24")));            // dígito errado
        when(rows.proximas(ID, TENANT, 12L, Constants.IMPORT_LOTE)).thenReturn(List.of());

        service.validar(ID, TENANT);

        List<Resultado> r = resultadosGravados();
        assertThat(r).extracting(Resultado::status).containsExactly("VALID", "ERROR", "ERROR");
        assertThat(r.get(1).errosJson()).contains("\"field\":\"documento\"").contains("já aparece na linha 2");
        assertThat(r.get(2).errosJson()).contains("dígito verificador");
        verify(batches).concluirValidacao(ID, TENANT, Constants.IMPORT_STATUS_AWAITING_FIX);
    }

    @Test
    void skuRepetidoEErroMasSkuComCaixaDiferenteNao() {
        batchDe("PRODUCT");
        when(rows.proximas(ID, TENANT, 0L, Constants.IMPORT_LOTE)).thenReturn(List.of(
                produto(1, 2, "SKU-1"), produto(2, 3, "SKU-1"), produto(3, 4, "sku-1")));
        when(rows.proximas(ID, TENANT, 3L, Constants.IMPORT_LOTE)).thenReturn(List.of());

        service.validar(ID, TENANT);

        assertThat(resultadosGravados()).extracting(Resultado::status).containsExactly("VALID", "ERROR", "VALID");
    }

    @Test
    void leOStagingEmLotesPelaUltimaChave() {
        batchDe("CUSTOMER");
        when(rows.proximas(ID, TENANT, 0L, Constants.IMPORT_LOTE)).thenReturn(List.of(cliente(10, 2, "PF", "Maria", "52998224725")));
        when(rows.proximas(ID, TENANT, 10L, Constants.IMPORT_LOTE)).thenReturn(List.of(cliente(20, 3, "PJ", "Acme", "11222333000181")));
        when(rows.proximas(ID, TENANT, 20L, Constants.IMPORT_LOTE)).thenReturn(List.of());

        service.validar(ID, TENANT);

        verify(rows, times(2)).gravarResultados(eq(TENANT), any());
        verify(batches).concluirValidacao(ID, TENANT, Constants.IMPORT_STATUS_READY);
    }

    @Test
    void batchJaEmAndamentoNaoValidaDeNovo() {
        when(batches.iniciarValidacao(ID, TENANT)).thenReturn(false);

        service.validar(ID, TENANT);

        verifyNoInteractions(rows, schema);
        verify(batches, never()).concluirValidacao(any(), anyLong(), any());
    }

    @Test
    void falhaInesperadaViraFailedEnaoFicaPresoEmValidating() {
        batchDe("CUSTOMER");
        when(rows.proximas(ID, TENANT, 0L, Constants.IMPORT_LOTE)).thenThrow(new IllegalStateException("banco caiu"));

        service.validar(ID, TENANT);

        verify(batches).falhar(ID, TENANT, Constants.IMPORT_FALHA_VALIDACAO);
        verify(batches, never()).concluirValidacao(any(), anyLong(), any());
    }
}
