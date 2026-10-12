package com.l.erp.migracaoservice.domain;

/** Linha de migracao.canonical_schema_field: a fonte única do template, do parser e do validador. */
public record CampoCanonico(String nome, int ordem, String tipo, boolean obrigatorio, String regra,
                            String ajuda, Integer tamanhoMaximo) {
}
