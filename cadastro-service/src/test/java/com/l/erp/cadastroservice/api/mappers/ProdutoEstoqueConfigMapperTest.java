package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoEstoqueConfigDTO;
import com.l.erp.cadastroservice.domain.Deposito;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoEstoqueConfigMapperTest {

    private final ProdutoEstoqueConfigMapper mapper = new ProdutoEstoqueConfigMapperImpl();

    @Test
    void toDto_depositoIdEFornecedorPreferencialId_vemPreenchidos() {
        // Regressão: depositoId nunca era mapeado (faltava @Mapping) e fornecedorPreferencialId
        // vinha com o id da linha de vínculo ProdutoFornecedor em vez do id do Fornecedor real —
        // em ambos os casos o dropdown de edição no front não conseguia casar a option (que é
        // keyed por Fornecedor.id / Deposito.id), então a tela mostrava os campos vazios.
        UUID depositoId = UUID.randomUUID();
        UUID fornecedorId = UUID.randomUUID();

        Deposito deposito = new Deposito();
        deposito.setId(depositoId);

        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setId(fornecedorId);

        ProdutoFornecedor vinculo = new ProdutoFornecedor();
        vinculo.setId(UUID.randomUUID());
        vinculo.setFornecedor(fornecedor);

        ProdutoEstoqueConfig config = new ProdutoEstoqueConfig();
        config.setDeposito(deposito);
        config.setFornecedorPreferencial(vinculo);

        ProdutoEstoqueConfigDTO dto = mapper.toDto(config);

        assertThat(dto.depositoId()).isEqualTo(depositoId);
        assertThat(dto.fornecedorPreferencialId()).isEqualTo(fornecedorId);
    }
}
