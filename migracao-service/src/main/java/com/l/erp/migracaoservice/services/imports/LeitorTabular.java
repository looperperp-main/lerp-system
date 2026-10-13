package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lê um arquivo tabular linha a linha (sem carregar o arquivo inteiro). A primeira linha não vazia
 * é o cabeçalho; quem decide isso é o consumidor. Linhas vazias também são entregues.
 */
public interface LeitorTabular {

    /** @param numeroLinha número da linha como o cliente vê na planilha (cabeçalho = 1) */
    @FunctionalInterface
    interface ConsumidorLinha {
        void aceitar(int numeroLinha, List<String> celulas);
    }

    void ler(Path arquivo, ConsumidorLinha consumidor) throws IOException;

    /** O leitor do formato, pela extensão já validada (xlsx ou csv). */
    static LeitorTabular para(String extensao) {
        return Constants.IMPORT_EXTENSAO_XLSX.equals(extensao) ? new XlsxLeitor() : new CsvLeitor();
    }
}
