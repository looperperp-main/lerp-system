package com.l.erp.migracaoservice.services.export;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Destino de uma tabela exportada (um arquivo por tabela). Duas implementações: CSV e XLSX. */
public interface RowSink extends Closeable {

    void cabecalho(List<String> colunas) throws IOException;

    /** Valores já normalizados por {@code ValorExport.normalizar}. */
    void linha(List<Object> valores) throws IOException;

    /** Grava o metadata do arquivo (spec export §8) e finaliza. Chamado uma vez, depois da última linha. */
    void concluir(Map<String, Object> metadata) throws IOException;
}
