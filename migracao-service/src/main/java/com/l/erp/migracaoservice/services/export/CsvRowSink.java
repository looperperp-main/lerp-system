package com.l.erp.migracaoservice.services.export;

import com.l.erp.migracaoservice.util.ValorExport;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** CSV RFC 4180 em UTF-8 + metadata no arquivo irmão {nome}.meta.json (nunca dentro do CSV — spec export §8). */
public class CsvRowSink implements RowSink {

    private final Path arquivo;
    private final Path meta;
    private final ObjectMapper mapper;
    private final BufferedWriter out;

    public CsvRowSink(Path arquivo, Path meta, ObjectMapper mapper) throws IOException {
        this.arquivo = arquivo;
        this.meta = meta;
        this.mapper = mapper;
        this.out = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8);
    }

    @Override
    public void cabecalho(List<String> colunas) throws IOException {
        escreverLinha(colunas.stream().map(c -> (Object) c).toList());
    }

    @Override
    public void linha(List<Object> valores) throws IOException {
        escreverLinha(valores);
    }

    @Override
    public void concluir(Map<String, Object> metadata) throws IOException {
        out.flush();
        Files.writeString(meta, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private void escreverLinha(List<Object> valores) throws IOException {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) out.write(',');
            out.write(escapar(ValorExport.paraTexto(valores.get(i))));
        }
        out.write("\r\n");
    }

    static String escapar(String v) {
        boolean precisa = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return precisa ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    Path arquivo() {
        return arquivo;
    }
}
