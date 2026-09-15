package com.l.erp.fiscalservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do cálculo fiscal de uma operação (§1.4.10). Sem persistência nesta fatia.
 * {@code memoriaCalculo} é a memória de cálculo citável, regra por regra.
 *
 * <p>{@code NON_NULL}: com o split payment desligado, {@code valorSplitIbs/valorSplitCbs} são
 * {@code null} e não saem no JSON — o documento fiscal não carrega esses campos.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperacaoFiscalDTO {

    private BigDecimal baseCalculo;
    private BigDecimal valorIs;
    private BigDecimal valorIbsEstadual;
    private BigDecimal valorIbsMunicipal;
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorSplitIbs;
    private BigDecimal valorSplitCbs;
    // Legado da transição (fatia 3c) — ICMS em produto OU ISS em serviço, nunca os dois; null
    // quando pctRemanescente = 0 (regime permanente) ou fora do escopo (early-return de zerado()).
    private BigDecimal valorIcms;
    private BigDecimal valorIss;
    // Retenção na fonte (fatia 3e) — valores retidos, não guias/títulos (isso é do AR). Todos
    // null quando a flag de retenção correspondente não foi declarada no request.
    private BigDecimal valorIssRetido;
    private BigDecimal valorIrrf;
    private BigDecimal valorCsrf;
    private BigDecimal valorInss;
    // Crédito de entrada (item 4) — só preenchido quando o CFOP é de ENTRADA; null em saída.
    // Quem persiste saldo/aproveitamento é o operacoes-service (AP); aqui é só o valor calculado.
    private BigDecimal valorCreditoIbs;
    private BigDecimal valorCreditoCbs;
    private String regimeAplicado;
    private List<String> memoriaCalculo;

    // Etapa 0 do contrato XML-ready (spec/modulos/emissao-fiscal/emissao-fiscal.md §10) — valores
    // que o motor já resolve internamente (TabelaFiscal/RegimeDiferenciado/AliquotaIbs/AliquotaCbs)
    // pra chegar no valor final, agora propagados em vez de descartados. Preenchidos nos caminhos
    // SAÍDA e ENTRADA que passam pela Etapa 3 (alíquotas vigentes); MEI/alíquota-zero/monofásico
    // retornam antes dessa etapa e não têm esses valores pra propagar (mesmo escopo do ponytail de
    // {@code zerado()} — ver MotorFiscalService).
    private String cClassTrib;
    private BigDecimal percentualIbsUf;
    private BigDecimal percentualIbsMunicipal;
    private BigDecimal percentualCbs;
    private BigDecimal percentualReducaoAplicado;

    // ponytail: cst (CST do IBS/CBS, Anexo NT 2023.001) e cstIcms/csosn não têm fonte resolvida
    // internamente — o motor não modela essa classificação hoje, só reducaoPercentual/aliquotaZero/
    // monofasico. Inventar o mapeamento aqui seria lógica fiscal nova (e arriscada: CST errado
    // rejeita a NF-e na SEFAZ), fora do escopo desta fatia ("propagar, não recalcular"). Ficam
    // sempre null até existir uma tabela real de resolução — upgrade quando o emissao-fiscal-service
    // tiver o dado.
    private String cst;
    private String cstIcms;
    private String csosn;

    // Legado ICMS (fatia 3c) — só preenchidos quando calcularLegado resolve RegimeIcms (produto,
    // transição ativa); null nos demais casos, mesmo padrão de valorIcms.
    private BigDecimal percentualIcmsNominal;
    private BigDecimal percentualReducaoBaseIcms;
    /** modBC da NF-e — sempre "3" (Valor da Operação): o motor não modela pauta/margem/preço
     * tabelado (fiscal-schema-011), então essa é a única modalidade que este cálculo produz. */
    private String modalidadeBaseCalculoIcms;
}