package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.exception.custom.BusinessException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.Map;

/**
 * Chave de acesso da NF-e (44 posições) — MOC 7.0 §2.2.6 e NT Conjunta 2025.001 (CNPJ alfanumérico), §5:
 * {@code cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1)}.
 *
 * <p>O DV é módulo 11 com pesos 2..9 da direita pra esquerda, e cada caractere vira {@code ASCII - 48}
 * (dígitos ficam iguais, letras A=17, B=18...) — a mesma regra do DV do CNPJ alfanumérico, então o
 * exemplo da NT ({@code 12ABC34501DE} → {@code 35}) serve de vetor de teste.</p>
 */
public final class ChaveAcesso {

    /** Código IBGE da UF (tabela do cUF). */
    private static final Map<String, String> CODIGO_UF = Map.ofEntries(
            Map.entry("RO", "11"), Map.entry("AC", "12"), Map.entry("AM", "13"), Map.entry("RR", "14"),
            Map.entry("PA", "15"), Map.entry("AP", "16"), Map.entry("TO", "17"), Map.entry("MA", "21"),
            Map.entry("PI", "22"), Map.entry("CE", "23"), Map.entry("RN", "24"), Map.entry("PB", "25"),
            Map.entry("PE", "26"), Map.entry("AL", "27"), Map.entry("SE", "28"), Map.entry("BA", "29"),
            Map.entry("MG", "31"), Map.entry("ES", "32"), Map.entry("RJ", "33"), Map.entry("SP", "35"),
            Map.entry("PR", "41"), Map.entry("SC", "42"), Map.entry("RS", "43"), Map.entry("MS", "50"),
            Map.entry("MT", "51"), Map.entry("GO", "52"), Map.entry("DF", "53"));

    private static final SecureRandom RANDOM = new SecureRandom();

    private ChaveAcesso() {
    }

    /** Resultado da geração: a chave completa e as partes que o XML precisa (cUF, cNF, cDV). */
    public record Resultado(String chave, String codigoUf, String codigoNumerico, String digitoVerificador) {
    }

    public static Resultado gerar(String uf, YearMonth emissao, String cnpj, String modelo, String serie,
                                  long numero, String tpEmis, String codigoNumerico) {
        String cUf = CODIGO_UF.get(uf);
        if (cUf == null) {
            throw new BusinessException("UF inválida para a chave de acesso: " + uf, HttpStatus.BAD_REQUEST);
        }
        String base = cUf
                + "%02d%02d".formatted(emissao.getYear() % 100, emissao.getMonthValue())
                + cnpj
                + modelo
                + "%03d".formatted(Integer.parseInt(serie))
                + "%09d".formatted(numero)
                + tpEmis
                + codigoNumerico;
        String dv = String.valueOf(calcularDv(base));
        return new Resultado(base + dv, cUf, codigoNumerico, dv);
    }

    /**
     * cNF de 8 dígitos. Regra do leiaute (B03-10): não pode ser igual ao nNF nem uma sequência trivial;
     * por isso sorteia de novo nesses casos.
     */
    public static String gerarCodigoNumerico(long numero) {
        String nNF = "%08d".formatted(numero % 100_000_000L);
        String cNF;
        do {
            cNF = "%08d".formatted(RANDOM.nextInt(100_000_000));
        } while (cNF.equals(nNF) || cNF.chars().distinct().count() == 1);
        return cNF;
    }

    /** DV módulo 11: resto 0 ou 1 → 0; senão 11 - resto. */
    public static int calcularDv(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
