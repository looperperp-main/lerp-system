package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.domain.ErroCampo;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ProdutoNovo;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import com.l.erp.migracaoservice.util.ValorImport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PRODUCT → cadastros.produto. A chave é o SKU (único por tenant, sensível a caixa como no banco): SKU que já
 * existe é IGNORADO e o produto existente é mantido, nunca sobrescrito (preço, estoque e pedidos apontam para ele).
 */
@Component
@RequiredArgsConstructor
public class ProdutoPromotor implements Promotor {

    private final CadastroDestinoRepository destino;
    private final ImportRowRepository rows;
    private final ObjectMapper mapper;

    @Override
    public String entidade() {
        return Constants.MIGRACAO_ENTIDADE_PRODUTO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void promover(Long tenantId, UUID userId, UUID batchId) {
        Set<String> skus = destino.skus(tenantId);      // ponytail: todos os SKUs do tenant em memória; cabe em centenas de milhares

        long depoisDoId = 0;
        List<LinhaStaging> lote;
        while (!(lote = rows.proximasValidas(batchId, tenantId, depoisDoId, Constants.IMPORT_LOTE)).isEmpty()) {
            List<ProdutoNovo> produtos = new ArrayList<>();
            List<Resultado> resultados = new ArrayList<>(lote.size());
            for (LinhaStaging staging : lote) {
                Map<String, String> linha = mapper.readValue(staging.rawJson(), Map.class);
                String sku = linha.get("sku").strip();
                if (skus.add(sku)) {
                    produtos.add(new ProdutoNovo(UUID.randomUUID(), sku, ValorImport.texto(linha.get("codigo_externo")),
                            linha.get("nome").strip(), ValorImport.texto(linha.get("descricao")), tipo(linha.get("tipo")),
                            linha.get("unidade").strip().toUpperCase(Locale.ROOT), ncm(linha.get("ncm")),
                            ValorImport.texto(linha.get("ean")), ValorImport.texto(linha.get("cest")),
                            ValorImport.texto(linha.get("origem")), ativo(linha.get("ativo"))));
                    resultados.add(new Resultado(staging.id(), Constants.IMPORT_LINHA_PROMOVIDA, null));
                } else {
                    resultados.add(new Resultado(staging.id(), Constants.IMPORT_LINHA_IGNORADA,
                            mapper.writeValueAsString(List.of(new ErroCampo("sku", Constants.IMPORT_IGNORADA_PRODUTO)))));
                }
            }
            destino.inserirProdutos(tenantId, userId, produtos);
            rows.gravarPromocao(tenantId, resultados);
            depoisDoId = lote.getLast().id();
        }
    }

    /** O validador aceita "Serviço" com cedilha; o banco guarda o nome do enum. */
    private static String tipo(String valor) {
        return valor.strip().toUpperCase(Locale.ROOT).replace("Ç", "C");
    }

    /** O validador aceita o NCM com pontos (1234.56.78); o cadastro guarda só os dígitos. */
    private static String ncm(String valor) {
        String texto = ValorImport.texto(valor);
        return texto == null ? null : texto.replace(".", "");
    }

    private static boolean ativo(String valor) {
        String texto = ValorImport.texto(valor);
        return texto == null || ValorImport.booleano(texto).orElse(true);
    }
}
