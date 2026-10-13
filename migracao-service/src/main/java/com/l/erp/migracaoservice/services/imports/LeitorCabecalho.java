package com.l.erp.migracaoservice.services.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lê só o cabeçalho (1ª linha não vazia) e para: um arquivo de 50 MB não precisa ser percorrido inteiro para a
 * tela de mapeamento listar as colunas. A parada é por exceção sem stack, porque o leitor é um callback.
 */
final class LeitorCabecalho {

    private LeitorCabecalho() {}

    private static final class Achou extends RuntimeException {
        private final transient List<String> cabecalho;

        Achou(List<String> cabecalho) {
            super(null, null, false, false);
            this.cabecalho = cabecalho;
        }
    }

    /** Células do cabeçalho como vieram (podem ter vazias no meio); lista vazia se o arquivo não tem linha alguma. */
    static List<String> ler(LeitorTabular leitor, Path arquivo) throws IOException {
        try {
            leitor.ler(arquivo, (numero, celulas) -> {
                if (celulas.stream().anyMatch(c -> c != null && !c.isBlank())) throw new Achou(List.copyOf(celulas));
            });
        } catch (Achou achou) {
            return achou.cabecalho;
        }
        return List.of();
    }
}
