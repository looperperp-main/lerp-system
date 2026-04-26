package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaRequestDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoParcelaMapper;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoParcelaRepository;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.cadastroservice.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCorrelationIdFromRequest;

@Service
public class CondicaoPagamentoParcelaService {
    private final Logger logger = LoggerFactory.getLogger(CondicaoPagamentoParcelaService.class);
    private final CondicaoPagamentoParcelaRepository parcelaRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final CondicaoPagamentoParcelaMapper mapper;
    private final Utils utils;

    public CondicaoPagamentoParcelaService(CondicaoPagamentoParcelaRepository parcelaRepository, CondicaoPagamentoRepository condicaoPagamentoRepository, CondicaoPagamentoParcelaMapper mapper, Utils utils) {
        this.parcelaRepository = parcelaRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.mapper = mapper;
        this.utils = utils;
    }

    @Transactional(readOnly = true)
    public List<CondicaoPagamentoParcela> findByCondicaoPagamentoId(UUID condicaoPagamentoId, Long tenantId) {
        logger.debug("Buscando parcelas para a condição de pagamento ID: {}", condicaoPagamentoId);

        condicaoPagamentoRepository.findByIdAndTenantId(condicaoPagamentoId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.COND_PAG_NOT_FOUND));

        return parcelaRepository.findAllByCondicaoPagamentoIdOrderByNumeroParcelaAsc(condicaoPagamentoId);
    }

    @Transactional
    public List<CondicaoPagamentoParcela> saveAll(UUID condicaoPagamentoId, List<CondicaoPagamentoParcelaRequestDTO> parcelasDto, Long tenantId, UUID userId) {
        logger.debug("Salvando/Atualizando lote de parcelas para a condição de pagamento ID: {}", condicaoPagamentoId);
        UUID correlationId = getCorrelationIdFromRequest(logger);

        CondicaoPagamento condicaoPagamento = condicaoPagamentoRepository.findByIdAndTenantId(condicaoPagamentoId, tenantId)
                .orElseThrow(() -> {
                    utils.sendAuditEvent(Constants.COND_PAG_PAR_CREATION, userId, Constants.COND_PAG_PAR,condicaoPagamentoId, Constants.ERROR, "{ERROR: Condicao Nao Encontrada}", correlationId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.COND_PAG_NOT_FOUND);
                });

        BigDecimal somaPercentual = parcelasDto.stream()
                .map(CondicaoPagamentoParcelaRequestDTO::percentual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaPercentual.compareTo(new BigDecimal(Constants.MAXIMUM_PAYMENT_VALUE_PERCENT)) != 0) {
            utils.sendAuditEvent(Constants.COND_PAG_PAR_CREATION, userId, Constants.COND_PAG_PAR, condicaoPagamentoId, Constants.ERROR, "{ERROR: Soma dos percentuais diferente de 100}", correlationId);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "A soma dos percentuais das parcelas deve ser 100. Valor recebido: " + somaPercentual);
        }

        try{
            // Limpa as parcelas antigas
            parcelaRepository.deleteAllByCondicaoPagamentoId(condicaoPagamentoId);
            parcelaRepository.flush();

            // Prepara as novas
            List<CondicaoPagamentoParcela> parcelas = parcelasDto.stream().map(dto -> {
                CondicaoPagamentoParcela parcela = mapper.toEntity(dto);
                parcela.setId(null); // Força insert
                parcela.setCondicaoPagamento(condicaoPagamento);
                parcela.setCreatedBy(userId);
                parcela.setCreatedAt(Instant.now());
                return parcela;
            }).toList();

            List<CondicaoPagamentoParcela> savedParcelas = parcelaRepository.saveAll(parcelas);
            utils.sendAuditEvent(
                    Constants.COND_PAG_PAR_CREATION,
                    userId,
                    Constants.COND_PAG_PAR,
                    condicaoPagamentoId,
                    Constants.SUCCESS,
                    "{\"action\":\"Lote de parcelas atualizado\"}",
                    correlationId
            );
            return savedParcelas;
        }catch (Exception e){
            utils.sendAuditEvent(Constants.COND_PAG_PAR_CREATION, userId,Constants.COND_PAG_PAR, condicaoPagamentoId, Constants.ERROR, "{ERROR: " + e.getMessage() + "}", correlationId);
            throw e;
        }
    }
}
