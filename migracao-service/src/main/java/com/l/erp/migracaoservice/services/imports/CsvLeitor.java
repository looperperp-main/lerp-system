package com.l.erp.migracaoservice.services.imports;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV RFC 4180 em UTF-8 (com ou sem BOM). O separador é detectado no cabeçalho: o Excel em
 * português salva com ';', o export do SYAX usa ','. Aspas duplas protegem separador e quebra de linha.
 */
public class CsvLeitor implements LeitorTabular {

    private static final int LIMITE_CABECALHO = 65_536;

    @Override
    public void ler(Path arquivo, ConsumidorLinha consumidor) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            in.mark(1);
            if (in.read() != '﻿') in.reset();
            char separador = detectarSeparador(in);

            List<String> campos = new ArrayList<>();
            StringBuilder atual = new StringBuilder();
            boolean emAspas = false;
            int numero = 0;
            int lido;
            while ((lido = in.read()) != -1) {
                char c = (char) lido;
                if (emAspas) {
                    if (c != '"') {
                        atual.append(c);
                        continue;
                    }
                    in.mark(1);
                    int proximo = in.read();
                    if (proximo == '"') {
                        atual.append('"');          // "" dentro de aspas = aspas literal
                    } else {
                        emAspas = false;
                        if (proximo != -1) in.reset();
                    }
                } else if (c == '"' && atual.isEmpty()) {
                    emAspas = true;                 // aspas só abrem no início do campo
                } else if (c == separador) {
                    campos.add(atual.toString());
                    atual.setLength(0);
                } else if (c == '\n') {
                    campos.add(atual.toString());
                    atual.setLength(0);
                    consumidor.aceitar(++numero, new ArrayList<>(campos));
                    campos.clear();
                } else if (c != '\r') {
                    atual.append(c);
                }
            }
            if (!atual.isEmpty() || !campos.isEmpty()) {    // última linha sem quebra final
                campos.add(atual.toString());
                consumidor.aceitar(++numero, campos);
            }
        }
    }

    /** Olha só a primeira linha e volta o leitor ao início: o cabeçalho nunca tem aspas nem o separador no nome. */
    private static char detectarSeparador(BufferedReader in) throws IOException {
        in.mark(LIMITE_CABECALHO);
        int virgulas = 0;
        int pontoVirgulas = 0;
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c == ',') virgulas++;
            if (c == ';') pontoVirgulas++;
        }
        in.reset();
        return pontoVirgulas > virgulas ? ';' : ',';
    }
}
