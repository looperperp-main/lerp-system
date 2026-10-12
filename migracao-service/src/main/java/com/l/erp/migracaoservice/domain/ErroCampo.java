package com.l.erp.migracaoservice.domain;

/** Item de raw_import_row.error_detail: {field, message} (spec migracao §4). */
public record ErroCampo(String field, String message) {
}
