package com.l.erp.emissaofiscalservice.services.nfse;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Escolhe o {@link ProvedorNfse}: nota nova segue a configuração do emitente
 * ({@code emissao.configuracao_nfse}); nota existente segue o provedor gravado nela.
 */
@Service
public class ProvedorNfseResolver {

    private final ConfiguracaoNfseRepository configuracaoRepository;
    private final Map<CodigoProvedorNfse, ProvedorNfse> provedores = new EnumMap<>(CodigoProvedorNfse.class);

    public ProvedorNfseResolver(ConfiguracaoNfseRepository configuracaoRepository, List<ProvedorNfse> adaptadores) {
        this.configuracaoRepository = configuracaoRepository;
        adaptadores.forEach(adaptador -> provedores.put(adaptador.codigo(), adaptador));
    }

    /** Configuração de NFS-e do emitente; 422 se ele ainda não foi configurado. */
    public ConfiguracaoNfse configuracaoDe(Long tenantId, UUID emitenteId) {
        return configuracaoRepository.findByTenantIdAndEmitenteId(tenantId, emitenteId)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_NFSE_SEM_CONFIGURACAO, HttpStatus.UNPROCESSABLE_ENTITY));
    }

    public ProvedorNfse porCodigo(CodigoProvedorNfse codigo) {
        ProvedorNfse provedor = codigo == null ? null : provedores.get(codigo);
        if (provedor == null) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_NFSE_PROVEDOR_INDISPONIVEL.formatted(codigo), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return provedor;
    }
}
