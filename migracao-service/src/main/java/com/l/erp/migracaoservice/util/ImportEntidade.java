package com.l.erp.migracaoservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** Entidades que o import aceita (CUSTOMER, PRODUCT). Um lugar só para o 400 de entidade inválida. */
public final class ImportEntidade {

    private ImportEntidade() {}

    public static String normalizar(String entidade) {
        String e = entidade == null ? "" : entidade.strip().toUpperCase(Locale.ROOT);
        if (!Constants.IMPORT_ENTIDADES.contains(e)) {
            throw new BusinessException(Constants.IMPORT_ENTIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
        }
        return e;
    }
}
