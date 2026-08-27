package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoEstoqueConfigDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoPrecoDTO;
import com.l.erp.cadastroservice.api.mappers.ProdutoMapper;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import com.l.erp.cadastroservice.domain.ProdutoPreco;
import com.l.erp.cadastroservice.repository.DepositoRepository;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.ProdutoCategoriaRepository;
import com.l.erp.cadastroservice.repository.ProdutoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private ProdutoCategoriaRepository categoriaRepository;
    @Mock private FornecedorRepository fornecedorRepository;
    @Mock private TabelaPrecoRepository tabelaPrecoRepository;
    @Mock private ProdutoMapper mapper;
    @Mock private DepositoRepository depositoRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private ProdutoService produtoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private ProdutoDTO dto(UUID categoriaId, List<ProdutoEstoqueConfigDTO> estoqueConfigs) {
        return new ProdutoDTO(null, TENANT_ID, categoriaId, "SKU1", null, "Produto 1", null, "UN",
                null, null, null, null, null, null, null, null, null, null, null, true,
                null, null, null, null, null, null, estoqueConfigs);
    }

    @Test
    void findById_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.findById(id, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findById_mesmoTenant_usaQueryComEscopoDeTenant() {
        UUID id = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(id);
        produto.setTenantId(TENANT_ID);
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(produto));

        assertThat(produtoService.findById(id, TENANT_ID).getId()).isEqualTo(id);
        verify(produtoRepository).findByIdAndTenantId(id, TENANT_ID);
    }

    @Test
    void create_semCategoria_naoConsultaCategoriaRepository() {
        Produto mapped = new Produto();
        when(mapper.toEntity(any(ProdutoDTO.class))).thenReturn(mapped);
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto saved = produtoService.create(TENANT_ID, USER_ID, dto(null, null));

        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getCategoria()).isNull();
        verify(categoriaRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void create_comCategoriaValida_vinculaCategoria() {
        UUID categoriaId = UUID.randomUUID();
        ProdutoCategoria categoria = ProdutoCategoria.builder().id(categoriaId).build();
        Produto mapped = new Produto();

        when(mapper.toEntity(any(ProdutoDTO.class))).thenReturn(mapped);
        when(categoriaRepository.findByIdAndTenantId(categoriaId, TENANT_ID)).thenReturn(Optional.of(categoria));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto saved = produtoService.create(TENANT_ID, USER_ID, dto(categoriaId, null));

        assertThat(saved.getCategoria()).isEqualTo(categoria);
        verify(produtoRepository, times(2)).save(any(Produto.class));
    }

    @Test
    void create_estoqueConfigSemDeposito_lancaBadRequest() {
        Produto mapped = new Produto();
        ProdutoEstoqueConfigDTO configSemDeposito = new ProdutoEstoqueConfigDTO(
                null, TENANT_ID, null, null, null, null, null, null, null, null, null, null);

        when(mapper.toEntity(any(ProdutoDTO.class))).thenReturn(mapped);
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> produtoService.create(TENANT_ID, USER_ID, dto(null, List.of(configSemDeposito))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("obrigatório")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.update(id, USER_ID, dto(null, null), TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_semCategoriaId_removeCategoriaExistente() {
        UUID id = UUID.randomUUID();
        Produto existing = new Produto();
        existing.setId(id);
        existing.setCategoria(ProdutoCategoria.builder().id(UUID.randomUUID()).build());

        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto updated = produtoService.update(id, USER_ID, dto(null, null), TENANT_ID);

        assertThat(updated.getCategoria()).isNull();
    }

    @Test
    void update_precoSemCreatedAtCreatedBy_naoLancaExcecao() {
        // Regressão: o front nunca envia createdAt/createdBy nas linhas de preço/fornecedor/
        // estoqueConfig (o formulário Angular não tem esses campos), então esses valores chegam
        // sempre nulos no update — sem fallback, isso violava o @NotNull da entidade.
        UUID id = UUID.randomUUID();
        Produto existing = new Produto();
        existing.setId(id);

        ProdutoPrecoDTO precoSemAuditoria = new ProdutoPrecoDTO(
                null, TENANT_ID, UUID.randomUUID(), BigDecimal.TEN, LocalDate.now(), null,
                null, null, null, null);
        ProdutoDTO dtoComPreco = new ProdutoDTO(null, TENANT_ID, null, "SKU1", null, "Produto 1", null, "UN",
                null, null, null, null, null, null, null, null, null, null, null, true,
                null, null, null, null, List.of(precoSemAuditoria), null, null);

        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(produtoRepository.saveAndFlush(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> inv.getArgument(0));

        Produto updated = produtoService.update(id, USER_ID, dtoComPreco, TENANT_ID);

        ProdutoPreco preco = updated.getProdutoPrecos().iterator().next();
        assertThat(preco.getCreatedAt()).isNotNull();
        assertThat(preco.getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void updateStatus_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.updateStatus(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(id);
        produto.setAtivo(true);

        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(produto));

        produtoService.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(produto.getAtivo()).isFalse();
        verify(produtoRepository).save(produto);
    }
}
