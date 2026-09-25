package com.l.erp.emissaofiscalservice.services.numeracao;

import com.l.erp.emissaofiscalservice.domain.NumeracaoDocumento;
import com.l.erp.emissaofiscalservice.repository.NumeracaoDocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Numeração e série por (tenant, emitente, documento, série) — spec §3 item 3. Lock via
 * {@code SELECT ... FOR UPDATE} na linha do contador, não {@code DistributedLockService}/Redis.
 *
 * <p>{@code Propagation.MANDATORY}: obter um número só faz sentido dentro da transação que também
 * grava o {@code DocumentoFiscal} — se o processo falhar depois de incrementar mas antes de gravar
 * o documento, o rollback da transação desfaz o incremento junto, sem deixar buraco de sequência.</p>
 */
@Service
public class NumeracaoDocumentoService {

    private final NumeracaoDocumentoRepository repository;

    public NumeracaoDocumentoService(NumeracaoDocumentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long proximoNumero(Long tenantId, UUID emitenteId, String documento, String serie) {
        NumeracaoDocumento contador = repository
                .buscarComLockParaAtualizar(tenantId, emitenteId, documento, serie)
                .orElseGet(() -> criar(tenantId, emitenteId, documento, serie));

        long proximo = contador.getUltimoNumero() + 1;
        contador.setUltimoNumero(proximo);
        contador.setUpdatedAt(OffsetDateTime.now());
        repository.save(contador);
        return proximo;
    }

    private NumeracaoDocumento criar(Long tenantId, UUID emitenteId, String documento, String serie) {
        NumeracaoDocumento novo = new NumeracaoDocumento();
        novo.setTenantId(tenantId);
        novo.setEmitenteId(emitenteId);
        novo.setDocumento(documento);
        novo.setSerie(serie);
        novo.setUltimoNumero(0);
        // save+flush aqui garante que a linha exista pro FOR UPDATE de uma chamada concorrente
        // encontrar — sem isso, duas requisições simultâneas na primeira emissão da série
        // poderiam tentar criar a linha ao mesmo tempo e colidir na unique constraint.
        return repository.saveAndFlush(novo);
    }
}
