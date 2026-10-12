package com.l.erp.migracaoservice.services.imports;

import com.l.erp.migracaoservice.repository.CadastroDestinoRepository;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ProdutoNovo;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProdutoPromotorTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();

    private CadastroDestinoRepository destino;
    private ImportRowRepository rows;
    private ProdutoPromotor promotor;

    @BeforeEach
    void setUp() {
        destino = mock(CadastroDestinoRepository.class);
        rows = mock(ImportRowRepository.class);
        promotor = new ProdutoPromotor(destino, rows, JsonMapper.builder().build());
        when(destino.skus(TENANT)).thenReturn(new HashSet<>());
    }

    private void staging(LinhaStaging... linhas) {
        when(rows.proximasValidas(eq(BATCH), eq(TENANT), anyLong(), anyInt()))
                .thenReturn(List.of(linhas)).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<ProdutoNovo> produtosGravados() {
        ArgumentCaptor<List<ProdutoNovo>> c = ArgumentCaptor.forClass(List.class);
        verify(destino).inserirProdutos(eq(TENANT), eq(USER), c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Resultado> resultados() {
        ArgumentCaptor<List<Resultado>> c = ArgumentCaptor.forClass(List.class);
        verify(rows).gravarPromocao(eq(TENANT), c.capture());
        return c.getValue();
    }

    @Test
    void entidadeEProduto() {
        assertThat(promotor.entidade()).isEqualTo("PRODUCT");
    }

    @Test
    void produtoNovoNormalizaTipoUnidadeENcmEDeixaEmBrancoComoNulo() {
        staging(new LinhaStaging(1, 2, "{\"sku\":\" ABC-1 \",\"nome\":\" Parafuso \",\"tipo\":\"Serviço\",\"unidade\":\"un\","
                + "\"ncm\":\"7318.15.00\",\"ean\":\"  \",\"origem\":\"0\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(produtosGravados()).singleElement().satisfies(p -> {
            assertThat(p.sku()).isEqualTo("ABC-1");
            assertThat(p.nome()).isEqualTo("Parafuso");
            assertThat(p.tipo()).isEqualTo("SERVICO");          // o validador aceita a cedilha; o banco guarda o nome do enum
            assertThat(p.unidade()).isEqualTo("UN");
            assertThat(p.ncm()).isEqualTo("73181500");          // só dígitos
            assertThat(p.ean()).isNull();
            assertThat(p.codigoExterno()).isNull();
            assertThat(p.origem()).isEqualTo("0");
            assertThat(p.ativo()).isTrue();                     // em branco = ativo
        });
        assertThat(resultados()).containsExactly(new Resultado(1, "PROMOTED", null));
    }

    @Test
    void skuQueJaExisteNoCadastroEIgnoradoComMotivo() {
        when(destino.skus(TENANT)).thenReturn(new HashSet<>(Set.of("ABC-1")));
        staging(new LinhaStaging(1, 2, "{\"sku\":\"ABC-1\",\"nome\":\"Outro\",\"tipo\":\"MERCADORIA\",\"unidade\":\"UN\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(produtosGravados()).isEmpty();
        assertThat(resultados()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo("SKIPPED");
            assertThat(r.errosJson()).contains("sku").contains("o cadastro existente foi mantido");
        });
    }

    @Test
    void skuEhSensivelACaixaComoNoBanco() {
        when(destino.skus(TENANT)).thenReturn(new HashSet<>(Set.of("abc")));
        staging(new LinhaStaging(1, 2, "{\"sku\":\"ABC\",\"nome\":\"Maiúsculo\",\"tipo\":\"MERCADORIA\",\"unidade\":\"UN\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(produtosGravados()).hasSize(1);               // "ABC" e "abc" são SKUs diferentes na uq_produto_tenant_sku
    }

    @Test
    void produtoInativoRespeitaAColunaAtivo() {
        staging(new LinhaStaging(1, 2, "{\"sku\":\"X\",\"nome\":\"N\",\"tipo\":\"MERCADORIA\",\"unidade\":\"UN\",\"ativo\":\"NAO\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(produtosGravados()).singleElement().satisfies(p -> assertThat(p.ativo()).isFalse());
    }
}
