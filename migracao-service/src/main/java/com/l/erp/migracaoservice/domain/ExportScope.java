package com.l.erp.migracaoservice.domain;

import java.time.LocalDate;
import java.util.List;

/** Escopo normalizado do pedido (tabelas ordenadas); serializado em export_batch.scope. */
public record ExportScope(List<String> tabelas, LocalDate dataInicial, LocalDate dataFinal) {
}
