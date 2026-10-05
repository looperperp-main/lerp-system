package com.l.erp.emissaofiscalservice.services.nfse;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.ConfiguracaoNfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import com.l.erp.emissaofiscalservice.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cadastro de como emitir NFS-e para um emitente (município + provedor) — o que o {@link ProvedorNfseResolver} lê. */
@Service
public class ConfiguracaoNfseService {

    private final ConfiguracaoNfseRepository repository;

    public ConfiguracaoNfseService(ConfiguracaoNfseRepository repository) {
        this.repository = repository;
    }

    /** Cria ou atualiza: um emitente tem uma única configuração (unique tenant+emitente). */
    @Transactional
    public ConfiguracaoNfse salvar(UUID emitenteId, ConfiguracaoNfseRequestDTO request) {
        Long tenantId = tenantIdAtual();
        ConfiguracaoNfse configuracao = repository.findByTenantIdAndEmitenteId(tenantId, emitenteId)
                .orElseGet(ConfiguracaoNfse::new);

        OffsetDateTime agora = OffsetDateTime.now();
        configuracao.setTenantId(tenantId);
        configuracao.setEmitenteId(emitenteId);
        configuracao.setMunicipioIbge(request.municipioIbge());
        configuracao.setProvedor(request.provedor() == null ? CodigoProvedorNfse.ADN : request.provedor());
        configuracao.setInscricaoMunicipal(request.inscricaoMunicipal());
        if (configuracao.getCreatedAt() == null) {
            configuracao.setCreatedAt(agora);
        }
        configuracao.setUpdatedAt(agora);
        return repository.save(configuracao);
    }

    @Transactional(readOnly = true)
    public ConfiguracaoNfse buscar(UUID emitenteId) {
        return repository.findByTenantIdAndEmitenteId(tenantIdAtual(), emitenteId)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_NFSE_SEM_CONFIGURACAO, HttpStatus.NOT_FOUND));
    }

    private Long tenantIdAtual() {
        return SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
    }
}
