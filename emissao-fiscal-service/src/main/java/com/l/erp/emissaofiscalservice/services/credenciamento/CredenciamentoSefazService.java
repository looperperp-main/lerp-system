package com.l.erp.emissaofiscalservice.services.credenciamento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CredenciamentoSefaz;
import com.l.erp.emissaofiscalservice.domain.StatusCredenciamento;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CredenciamentoSefazRepository;
import com.l.erp.emissaofiscalservice.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Credenciamento do tenant como emissor na SEFAZ — spec §3 item 11. Vira checklist de onboarding
 * fiscal na UI (item 5) e o gate consultado antes de emissão em produção (Etapa 2).
 */
@Service
public class CredenciamentoSefazService {

    private final CredenciamentoSefazRepository repository;

    public CredenciamentoSefazService(CredenciamentoSefazRepository repository) {
        this.repository = repository;
    }

    /** Chamado pela Etapa 2 antes de transmitir em PRODUCAO — homologação não exige credenciamento. */
    public void exigirCredenciadoParaProducao(UUID emitenteId, String uf, TipoDocumentoFiscal modelo,
                                               AmbienteEmissao ambiente) {
        if (ambiente != AmbienteEmissao.PRODUCAO) {
            return;
        }

        Long tenantId = tenantIdAtual();
        CredenciamentoSefaz credenciamento = repository
                .findByTenantIdAndEmitenteIdAndUfAndModelo(tenantId, emitenteId, uf, modelo)
                .orElseThrow(() -> new BusinessException(
                        "Emitente ainda não credenciado como emissor de " + modelo + " na SEFAZ-" + uf
                                + ". Solicite o credenciamento antes de emitir em produção.",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        if (credenciamento.getStatus() != StatusCredenciamento.CREDENCIADO) {
            throw new BusinessException(
                    "Credenciamento de " + modelo + " na SEFAZ-" + uf + " está " + credenciamento.getStatus()
                            + " — não é possível emitir em produção.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public CredenciamentoSefaz registrar(UUID emitenteId, String uf, TipoDocumentoFiscal modelo,
                                          StatusCredenciamento status) {
        Long tenantId = tenantIdAtual();

        CredenciamentoSefaz credenciamento = repository
                .findByTenantIdAndEmitenteIdAndUfAndModelo(tenantId, emitenteId, uf, modelo)
                .orElseGet(CredenciamentoSefaz::new);

        OffsetDateTime agora = OffsetDateTime.now();
        credenciamento.setTenantId(tenantId);
        credenciamento.setEmitenteId(emitenteId);
        credenciamento.setUf(uf);
        credenciamento.setModelo(modelo);
        credenciamento.setStatus(status);
        credenciamento.setDataCredenciamento(status == StatusCredenciamento.CREDENCIADO ? agora : null);
        if (credenciamento.getCreatedAt() == null) {
            credenciamento.setCreatedAt(agora);
        }
        credenciamento.setUpdatedAt(agora);

        return repository.save(credenciamento);
    }

    public List<CredenciamentoSefaz> listarPorEmitente(UUID emitenteId) {
        return repository.findByTenantIdAndEmitenteId(tenantIdAtual(), emitenteId);
    }

    private Long tenantIdAtual() {
        return SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
    }
}
