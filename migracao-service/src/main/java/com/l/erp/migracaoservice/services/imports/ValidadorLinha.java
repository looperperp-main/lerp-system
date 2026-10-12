package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.CampoCanonico;
import com.l.erp.migracaoservice.domain.ErroCampo;
import com.l.erp.migracaoservice.util.DocumentoValidator;
import com.l.erp.migracaoservice.util.ValorImport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Valida uma linha já mapeada contra os campos de canonical_schema_field. A tabela decide QUAIS
 * regras valem para cada campo; o mapa abaixo decide COMO validar cada uma (spec migracao §4.3).
 * Um erro por campo (o primeiro): obrigatório → tamanho → tipo → regra.
 */
@Component
public class ValidadorLinha {

    private static final Set<String> IND_IE_DEST = Set.of("CONTRIBUINTE_ICMS", "ISENTO", "NAO_CONTRIBUINTE");

    /** (valor, linha inteira) → mensagem de erro, ou vazio se válido. */
    private final Map<String, BiFunction<String, Map<String, String>, Optional<String>>> regras = Map.of(
            Constants.IMPORT_REGRA_ENUM_PF_PJ, (v, l) -> erroSe(!Set.of("PF", "PJ").contains(maiusculo(v)), Constants.IMPORT_PF_PJ_INVALIDO),
            Constants.IMPORT_REGRA_CPF_CNPJ_DV, this::documento,
            Constants.IMPORT_REGRA_NCM_8, (v, l) -> erroSe(!v.replace(".", "").matches("\\d{8}"), Constants.IMPORT_NCM_INVALIDO),
            Constants.IMPORT_REGRA_ENUM_TIPO_PRODUTO, (v, l) -> erroSe(
                    !Set.of("MERCADORIA", "SERVICO").contains(maiusculo(v).replace("Ç", "C")), Constants.IMPORT_TIPO_PRODUTO_INVALIDO),
            Constants.IMPORT_REGRA_ENUM_IND_IE_DEST, (v, l) -> erroSe(!IND_IE_DEST.contains(maiusculo(v)), Constants.IMPORT_IND_IE_INVALIDO),
            Constants.IMPORT_REGRA_EAN, (v, l) -> erroSe(!v.matches("\\d{8}|\\d{12,14}"), Constants.IMPORT_EAN_INVALIDO));

    public List<ErroCampo> validar(List<CampoCanonico> campos, Map<String, String> linha) {
        List<ErroCampo> erros = new ArrayList<>();
        for (CampoCanonico campo : campos) {
            String valor = linha.get(campo.nome());
            if (valor == null || valor.isBlank()) {
                if (campo.obrigatorio()) erros.add(new ErroCampo(campo.nome(), Constants.IMPORT_CAMPO_OBRIGATORIO));
                continue;
            }
            verificar(campo, valor.strip(), linha).ifPresent(msg -> erros.add(new ErroCampo(campo.nome(), msg)));
        }
        return erros;
    }

    private Optional<String> verificar(CampoCanonico campo, String valor, Map<String, String> linha) {
        if (campo.tamanhoMaximo() != null && valor.length() > campo.tamanhoMaximo()) {
            return Optional.of(String.format(Constants.IMPORT_CAMPO_TAMANHO, campo.tamanhoMaximo()));
        }
        Optional<String> erroTipo = switch (campo.tipo()) {
            case Constants.IMPORT_TIPO_DATE -> erroSe(ValorImport.data(valor).isEmpty(), Constants.IMPORT_DATA_INVALIDA);
            case Constants.IMPORT_TIPO_BOOLEAN -> erroSe(ValorImport.booleano(valor).isEmpty(), Constants.IMPORT_BOOLEAN_INVALIDO);
            case Constants.IMPORT_TIPO_DECIMAL -> erroSe(ValorImport.decimal(valor).isEmpty(), Constants.IMPORT_DECIMAL_INVALIDO);
            default -> Optional.empty();
        };
        if (erroTipo.isPresent() || campo.regra() == null) return erroTipo;
        var regra = regras.get(campo.regra());
        return regra == null ? Optional.empty() : regra.apply(valor, linha);
    }

    /** CPF (11) ou CNPJ (14, inclusive alfanumérico) com DV certo, coerente com o tipo PF/PJ da linha. */
    private Optional<String> documento(String valor, Map<String, String> linha) {
        String doc = DocumentoValidator.normalizar(valor);
        boolean cpf = doc.length() == 11 && DocumentoValidator.cpfValido(doc);
        boolean cnpj = doc.length() == 14 && DocumentoValidator.cnpjValido(doc);
        if (!cpf && !cnpj) return Optional.of(Constants.IMPORT_DOCUMENTO_INVALIDO);
        String tipo = maiusculo(linha.get("tipo"));
        boolean incoerente = (cpf && "PJ".equals(tipo)) || (cnpj && "PF".equals(tipo));
        return erroSe(incoerente, Constants.IMPORT_DOCUMENTO_TIPO);
    }

    private static Optional<String> erroSe(boolean invalido, String mensagem) {
        return invalido ? Optional.of(mensagem) : Optional.empty();
    }

    private static String maiusculo(String s) {
        return s == null ? "" : s.strip().toUpperCase(Locale.ROOT);
    }
}
