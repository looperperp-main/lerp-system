package com.l.erp.fiscalservice.services.fiscal;

import java.math.BigDecimal;

/**
 * Onde o ano está na curva de extinção de ICMS/ISS da LC 214 (tabela {@code fiscal.transicao_ano}).
 *
 * <p>{@code pctRemanescente} é quanto do ICMS/ISS CHEIO ainda é devido: 100 até 2028, 90/80/70/60
 * em 2029-2032 e 0 em 2033. A matriz tributária (fatia 3b) guarda só a alíquota cheia — quem aplica
 * o degrau do ano é o motor, e por isso a matriz não precisa ser recarregada todo ano.
 *
 * <p>{@code pisCofinsVigente} é boolean e não percentual: PIS/COFINS não têm curva, são extintos de
 * uma vez em 2027 — só a competência de 2026 (e retroativo) os tem.
 */
public record TransicaoAno(BigDecimal pctRemanescente, boolean pisCofinsVigente) {}
