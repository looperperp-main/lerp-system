package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * O cabeçalho do arquivo não traz todas as colunas obrigatórias do modelo: é o critério de entrada da Trilha 2
 * (spec §7). Não é falha do batch — o chamador o põe para aguardar o mapeamento de colunas.
 */
class ColunasForaDoModeloException extends BusinessException {

    ColunasForaDoModeloException(List<String> faltando) {
        super(String.format(Constants.IMPORT_COLUNAS_AUSENTES, String.join(", ", faltando)), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
