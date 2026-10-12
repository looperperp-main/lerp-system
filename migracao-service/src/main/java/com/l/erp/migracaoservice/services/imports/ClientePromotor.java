package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ErroCampo;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ClienteNovo;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.MatrizNova;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaExistente;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaNova;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import com.l.erp.migracaoservice.util.DocumentoValidator;
import com.l.erp.migracaoservice.util.ValorImport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CUSTOMER → cadastros.pessoa (+ estabelecimento matriz se PJ) + cadastros.cliente, com as mesmas regras do
 * PessoaService: uma pessoa por documento (PF) ou por raiz de CNPJ (PJ, onde matriz e filial são a mesma pessoa).
 * Pessoa que já existe (ex.: cadastrada como fornecedor) só ganha o vínculo de cliente; cliente que já existe é
 * IGNORADO, e o cadastro existente nunca é sobrescrito (o cliente pode já ter movimento fiscal com ele).
 */
@Component
@RequiredArgsConstructor
public class ClientePromotor implements Promotor {

    private final CadastroDestinoRepository destino;
    private final ImportRowRepository rows;
    private final ObjectMapper mapper;

    @Override
    public String entidade() {
        return Constants.MIGRACAO_ENTIDADE_CLIENTE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void promover(Long tenantId, UUID userId, UUID batchId) {
        // ponytail: o cadastro de pessoas do tenant inteiro em memória (id por documento e por raiz); cabe em centenas
        // de milhares de pessoas. Se um tenant passar disso, trocar por consulta por lote de 1.000 documentos.
        Map<String, UUID> porDocumento = new HashMap<>();
        Map<String, UUID> porRaiz = new HashMap<>();
        for (PessoaExistente p : destino.pessoas(tenantId)) {
            porDocumento.put(DocumentoValidator.normalizar(p.documento()), p.id());
            if (p.cnpjRaiz() != null) porRaiz.put(p.cnpjRaiz(), p.id());
        }
        Set<UUID> comCliente = destino.pessoasComCliente(tenantId);

        long depoisDoId = 0;
        List<LinhaStaging> lote;
        while (!(lote = rows.proximasValidas(batchId, tenantId, depoisDoId, Constants.IMPORT_LOTE)).isEmpty()) {
            List<PessoaNova> pessoas = new ArrayList<>();
            List<MatrizNova> matrizes = new ArrayList<>();
            List<ClienteNovo> clientes = new ArrayList<>();
            List<Resultado> resultados = new ArrayList<>(lote.size());
            for (LinhaStaging staging : lote) {
                Map<String, String> linha = mapper.readValue(staging.rawJson(), Map.class);
                String doc = DocumentoValidator.normalizar(linha.get("documento"));
                boolean pj = doc.length() == 14;                       // a validação já garantiu CPF/CNPJ coerente com o tipo
                String raiz = pj ? doc.substring(0, 8) : null;
                boolean ativo = ativo(linha.get("ativo"));

                UUID pessoaId = pj ? porRaiz.getOrDefault(raiz, porDocumento.get(doc)) : porDocumento.get(doc);
                if (pessoaId == null) {
                    pessoaId = UUID.randomUUID();
                    String formatado = DocumentoValidator.formatar(doc);
                    pessoas.add(new PessoaNova(pessoaId, pj ? "PJ" : "PF", linha.get("nome_razao").strip(),
                            ValorImport.texto(linha.get("apelido_fantasia")), formatado, raiz, ValorImport.texto(linha.get("rg")),
                            data(linha.get("data_nascimento")), indIeDest(linha.get("ind_ie_dest")), ativo));
                    if (pj) {
                        matrizes.add(new MatrizNova(pessoaId, formatado, ativo));
                        porRaiz.put(raiz, pessoaId);
                    }
                    porDocumento.put(doc, pessoaId);
                }
                if (comCliente.add(pessoaId)) {
                    clientes.add(new ClienteNovo(UUID.randomUUID(), pessoaId, ativo));
                    resultados.add(new Resultado(staging.id(), Constants.IMPORT_LINHA_PROMOVIDA, null));
                } else {
                    resultados.add(new Resultado(staging.id(), Constants.IMPORT_LINHA_IGNORADA,
                            mapper.writeValueAsString(List.of(new ErroCampo("documento", Constants.IMPORT_IGNORADA_CLIENTE)))));
                }
            }
            // Ordem das FKs: pessoa → estabelecimento/cliente.
            destino.inserirPessoas(tenantId, userId, pessoas);
            destino.inserirMatrizes(tenantId, userId, matrizes);
            destino.inserirClientes(tenantId, userId, clientes);
            rows.gravarPromocao(tenantId, resultados);
            depoisDoId = lote.getLast().id();
        }
    }

    /** Coluna em branco = cadastro ativo (mesmo default da coluna). */
    private static boolean ativo(String valor) {
        String texto = ValorImport.texto(valor);
        return texto == null || ValorImport.booleano(texto).orElse(true);
    }

    private static LocalDate data(String valor) {
        String texto = ValorImport.texto(valor);
        return texto == null ? null : ValorImport.data(texto).orElse(null);
    }

    private static String indIeDest(String valor) {
        String texto = ValorImport.texto(valor);
        return texto == null ? null : texto.toUpperCase(Locale.ROOT);
    }
}
