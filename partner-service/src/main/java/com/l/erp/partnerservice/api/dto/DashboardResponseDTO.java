package com.l.erp.partnerservice.api.dto;

import java.util.List;

public record DashboardResponseDTO(
        long statsConvidados,
        long statsAtivos,
        long statsTrial,
        long statsFollowup,
        long statsConvertidos,
        long trialsExpirando3Dias,
        java.math.BigDecimal comissaoMesAtual,
        java.math.BigDecimal totalComissoesPagas,
        List<TrialUrgenteDTO> trialsUrgentes,
        List<AtividadeItemDTO> atividadeRecente
) {}