package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.compras.CompraNumeracao;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.repository.compras.CompraNumeracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mesmo padrão de PedidoNumeroService (vendas): upsert + SELECT ... FOR UPDATE, mesma transação do documento. */
@Service
public class CompraNumeroService {

    private final CompraNumeracaoRepository compraNumeracaoRepository;

    public CompraNumeroService(CompraNumeracaoRepository compraNumeracaoRepository) {
        this.compraNumeracaoRepository = compraNumeracaoRepository;
    }

    @Transactional
    public Long proximoNumero(Long tenantId, TipoDocumentoCompra tipoDocumento) {
        compraNumeracaoRepository.inicializarSeNaoExiste(tenantId, tipoDocumento.name());
        CompraNumeracao numeracao = compraNumeracaoRepository.findByIdForUpdate(tenantId, tipoDocumento)
                .orElseThrow(() -> new IllegalStateException(
                        String.format(Constants.REQUISICAO_COMPRA_NUMERACAO_FALHA, tenantId)));
        Long numero = numeracao.getProximoNumero();
        numeracao.setProximoNumero(numero + 1);
        return numero;
    }
}
