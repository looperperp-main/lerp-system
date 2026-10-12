package com.l.erp.migracaoservice.services.imports;

import com.l.erp.migracaoservice.domain.CampoCanonico;

import java.util.List;

/** Os campos de canonical_schema_field como ficam depois do seed + migracao-009 (usados só nos testes). */
final class CamposFixture {

    private CamposFixture() {}

    static List<CampoCanonico> cliente() {
        return List.of(
                new CampoCanonico("tipo", 1, "STRING", true, "ENUM_PF_PJ", "Tipo de pessoa: PF ou PJ", 2),
                new CampoCanonico("nome_razao", 2, "STRING", true, null, "Nome ou razão social", 200),
                new CampoCanonico("documento", 3, "CNPJ", true, "CPF_CNPJ_DV", "CPF ou CNPJ", 18),
                new CampoCanonico("data_nascimento", 4, "DATE", false, null, "Data de nascimento", null),
                new CampoCanonico("ind_ie_dest", 5, "STRING", false, "ENUM_IND_IE_DEST", "Indicador de IE", null),
                new CampoCanonico("ativo", 6, "BOOLEAN", false, null, "Cadastro ativo", null));
    }

    static List<CampoCanonico> produto() {
        return List.of(
                new CampoCanonico("sku", 1, "STRING", true, null, "SKU", 80),
                new CampoCanonico("nome", 2, "STRING", true, null, "Nome", 200),
                new CampoCanonico("tipo", 3, "STRING", true, "ENUM_TIPO_PRODUTO", "Tipo do produto", null),
                new CampoCanonico("unidade", 4, "STRING", true, null, "Unidade", 10),
                new CampoCanonico("ncm", 5, "STRING", false, "NCM_8", "NCM", 10),
                new CampoCanonico("ean", 6, "STRING", false, "EAN_DIGITOS", "EAN", 14),
                new CampoCanonico("fator", 7, "DECIMAL", false, null, "Fator", null));
    }
}
