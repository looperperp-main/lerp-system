package com.l.erp.billingservice.infra.partner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Leitura read-only da chave PIX do parceiro direto em {@code partner.partner} (Fase 6, decisão B).
 *
 * <p>billing e partner-service compartilham o MESMO Postgres ({@code loop-erp}); billing só faz
 * {@code SELECT} no schema {@code partner} — sem HTTP, sem staleness, sem acoplamento de runtime
 * (partner-service pode estar fora do ar). É a única dependência de dados do partner no payout;
 * o resto da máquina de estados e a integração Asaas vivem no billing.</p>
 */
@Component
public class PartnerPayoutReader {

    private final JdbcTemplate jdbc;

    public PartnerPayoutReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PartnerPayoutInfo> find(UUID partnerId) {
        return jdbc.query(
                "SELECT pix_key, pix_key_type FROM partner.partner WHERE id = ?",
                rs -> rs.next()
                        ? Optional.of(new PartnerPayoutInfo(rs.getString("pix_key"), rs.getString("pix_key_type")))
                        : Optional.empty(),
                partnerId);
    }
}
