package com.l.erp.billingservice.services.commission;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve a {@link CommissionStrategy} pelo modelo. Mantida para extensão futura (modelo ANUAL, etc.)
 * sem alterar o {@link CommissionEngine}.
 */
@Component
public class CommissionStrategyFactory {

    private final Map<String, CommissionStrategy> strategies;

    public CommissionStrategyFactory(List<CommissionStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(CommissionStrategy::getModel, Function.identity()));
    }

    public CommissionStrategy getStrategy(String model) {
        return Optional.ofNullable(strategies.get(model))
                .orElseThrow(() -> new IllegalArgumentException("Strategy de comissão não encontrada: " + model));
    }
}