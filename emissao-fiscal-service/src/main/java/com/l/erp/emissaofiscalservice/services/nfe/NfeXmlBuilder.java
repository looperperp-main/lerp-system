package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DestinatarioDTO;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.EmitenteDTO;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.ItemDocumentoDTO;
import com.l.erp.emissaofiscalservice.api.dto.SnapshotFiscalItemDTO;
import org.springframework.stereotype.Service;
import tools.jackson.dataformat.xml.XmlMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o XML da NF-e 4.00 (modelo 55) a partir do payload já resolvido — nunca recalcula nada
 * (spec §3 item 11, opção b): cada valor sai do snapshot fiscal do item. Devolve o {@code <NFe>} sem
 * assinatura; a assinatura é anexada depois pelo {@code XmlSignatureService} (Fatia 3).
 *
 * <p>O {@code XmlMapper} é privado de propósito e não vira bean: {@code XmlMapper} é um
 * {@code ObjectMapper}, e registrá-lo no contexto quebraria a injeção do {@code ObjectMapper} JSON
 * usado no resto do serviço.</p>
 */
@Service
public class NfeXmlBuilder {

    private static final String NAMESPACE_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final XmlMapper XML = XmlMapper.builder().build();
    private static final String CST_ICMS_00 = "00";
    private static final String CST_ICMS_20 = "20";

    /** {@code emissao} carrega o fuso do emitente (dhEmi exige o offset, ex. {@code -03:00}). */
    public String montar(DocumentoFiscalRequestDTO request, long numero, ChaveAcesso.Resultado chave,
                         OffsetDateTime emissao) {
        boolean homologacao = "HOMOLOGACAO".equalsIgnoreCase(request.ambiente());
        NfeXml.InfNFe infNFe = new NfeXml.InfNFe(
                Constants.NFE_VERSAO_LEIAUTE,
                "NFe" + chave.chave(),
                ide(request, numero, chave, emissao, homologacao),
                emit(request.emitente()),
                dest(request.destinatario(), homologacao),
                itens(request.itens(), homologacao),
                total(request.itens()),
                new NfeXml.Transp(Constants.NFE_FRETE_SEM_OCORRENCIA),
                new NfeXml.Pag(new NfeXml.DetPag(Constants.NFE_PAGAMENTO_SEM_PAGAMENTO, dinheiro(BigDecimal.ZERO))));
        return "<NFe xmlns=\"" + NAMESPACE_NFE + "\">" + XML.writeValueAsString(infNFe) + "</NFe>";
    }

    private NfeXml.Ide ide(DocumentoFiscalRequestDTO request, long numero, ChaveAcesso.Resultado chave,
                           OffsetDateTime emissao, boolean homologacao) {
        boolean interestadual = !request.emitente().endereco().uf().equals(request.destinatario().endereco().uf());
        return new NfeXml.Ide(
                chave.codigoUf(),
                chave.codigoNumerico(),
                request.naturezaOperacao(),
                Constants.NFE_MODELO_55,
                request.serie(),
                String.valueOf(numero),
                emissao.format(DATA_HORA),
                String.valueOf(request.tipoOperacao()),
                interestadual ? "2" : "1",
                request.emitente().endereco().codigoMunicipio(),
                "1",
                "1",
                chave.digitoVerificador(),
                homologacao ? "2" : "1",
                "1",
                String.valueOf(request.indicadorFinal()),
                String.valueOf(request.indicadorPresenca()),
                "0",
                Constants.NFE_VER_PROC);
    }

    private NfeXml.Emit emit(EmitenteDTO emitente) {
        return new NfeXml.Emit(emitente.cnpj(), emitente.razaoSocial(), emitente.nomeFantasia(),
                endereco(emitente.endereco()), emitente.inscricaoEstadual(), emitente.crt());
    }

    private NfeXml.Dest dest(DestinatarioDTO destinatario, boolean homologacao) {
        boolean cpf = destinatario.documento().length() == 11;
        return new NfeXml.Dest(
                cpf ? null : destinatario.documento(),
                cpf ? destinatario.documento() : null,
                homologacao ? Constants.NFE_TEXTO_HOMOLOGACAO_DESTINATARIO : destinatario.nome(),
                endereco(destinatario.endereco()),
                destinatario.indicadorIe(),
                destinatario.inscricaoEstadual(),
                destinatario.email());
    }

    private NfeXml.Endereco endereco(EnderecoDTO e) {
        return new NfeXml.Endereco(e.logradouro(), e.numero(), e.complemento(), e.bairro(), e.codigoMunicipio(),
                e.municipio(), e.uf(), e.cep(), e.telefone());
    }

    private List<NfeXml.Det> itens(List<ItemDocumentoDTO> itens, boolean homologacao) {
        List<NfeXml.Det> dets = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemDocumentoDTO item = itens.get(i);
            SnapshotFiscalItemDTO f = item.fiscal();
            String descricao = homologacao && i == 0 ? Constants.NFE_TEXTO_HOMOLOGACAO_PRODUTO : item.descricao();
            NfeXml.Prod prod = new NfeXml.Prod(item.codigo(), Constants.NFE_SEM_GTIN, descricao, item.ncm(), f.cfop(),
                    item.unidadeComercial(), quantidade(item.quantidade()), valorUnitario(item.valorUnitario()),
                    dinheiro(item.valorTotal()), Constants.NFE_SEM_GTIN, item.unidadeComercial(),
                    quantidade(item.quantidade()), valorUnitario(item.valorUnitario()), "1");
            NfeXml.Imposto imposto = new NfeXml.Imposto(icms(item), pis(f), cofins(f), ibsCbs(f));
            dets.add(new NfeXml.Det(String.valueOf(i + 1), prod, imposto));
        }
        return dets;
    }

    private NfeXml.Icms icms(ItemDocumentoDTO item) {
        SnapshotFiscalItemDTO f = item.fiscal();
        String modBc = f.modalidadeBaseCalculoIcms() == null ? Constants.NFE_MOD_BC_VALOR_OPERACAO : f.modalidadeBaseCalculoIcms();
        return switch (f.cstIcms()) {
            case CST_ICMS_00 -> new NfeXml.Icms(new NfeXml.Icms00(item.origem(), f.cstIcms(), modBc,
                    dinheiro(f.baseCalculoIcms()), percentual(f.percentualIcms()), dinheiro(f.valorIcms())), null, null);
            case CST_ICMS_20 -> new NfeXml.Icms(null, new NfeXml.Icms20(item.origem(), f.cstIcms(), modBc,
                    percentual(f.percentualReducaoBaseIcms()), dinheiro(f.baseCalculoIcms()),
                    percentual(f.percentualIcms()), dinheiro(f.valorIcms())), null);
            default -> new NfeXml.Icms(null, null, new NfeXml.Icms40(item.origem(), f.cstIcms()));
        };
    }

    /** CST 01/02 → PISAliq; 04 a 09 → PISNT; demais → PISOutr (mesmo critério de COFINS). */
    private NfeXml.Pis pis(SnapshotFiscalItemDTO f) {
        String cst = f.cstPis();
        if (semIncidencia(cst)) {
            return new NfeXml.Pis(null, new NfeXml.PisNt(cst), null);
        }
        if (aliquotaNormal(cst)) {
            return new NfeXml.Pis(new NfeXml.PisAliq(cst, dinheiro(f.baseCalculoPis()), percentual(f.percentualPis()),
                    dinheiro(f.valorPis())), null, null);
        }
        return new NfeXml.Pis(null, null, new NfeXml.PisOutr(cst, dinheiro(f.baseCalculoPis()),
                percentual(f.percentualPis()), dinheiro(f.valorPis())));
    }

    private NfeXml.Cofins cofins(SnapshotFiscalItemDTO f) {
        String cst = f.cstCofins();
        if (semIncidencia(cst)) {
            return new NfeXml.Cofins(null, new NfeXml.CofinsNt(cst), null);
        }
        if (aliquotaNormal(cst)) {
            return new NfeXml.Cofins(new NfeXml.CofinsAliq(cst, dinheiro(f.baseCalculoCofins()),
                    percentual(f.percentualCofins()), dinheiro(f.valorCofins())), null, null);
        }
        return new NfeXml.Cofins(null, null, new NfeXml.CofinsOutr(cst, dinheiro(f.baseCalculoCofins()),
                percentual(f.percentualCofins()), dinheiro(f.valorCofins())));
    }

    /** IBS/CBS do item (grupo UB12), obrigatório em homologação para CRT 3 desde 01/07/2026 (NT 2025.002 v1.51). */
    private NfeXml.IbsCbs ibsCbs(SnapshotFiscalItemDTO f) {
        BigDecimal vIbs = soma(f.valorIbsEstadual(), f.valorIbsMunicipal());
        return new NfeXml.IbsCbs(f.cstIbsCbs(), f.cClassTrib(), new NfeXml.GIbsCbs(
                dinheiro(f.baseCalculoIbsCbs()),
                new NfeXml.GIbsUf(percentual(f.percentualIbsUf()), dinheiro(f.valorIbsEstadual())),
                new NfeXml.GIbsMun(percentual(f.percentualIbsMunicipal()), dinheiro(f.valorIbsMunicipal())),
                dinheiro(vIbs),
                new NfeXml.GCbs(percentual(f.percentualCbs()), dinheiro(f.valorCbs()))));
    }

    private NfeXml.Total total(List<ItemDocumentoDTO> itens) {
        BigDecimal vBcIcms = BigDecimal.ZERO;
        BigDecimal vIcms = BigDecimal.ZERO;
        BigDecimal vProd = BigDecimal.ZERO;
        BigDecimal vPis = BigDecimal.ZERO;
        BigDecimal vCofins = BigDecimal.ZERO;
        BigDecimal vBcIbsCbs = BigDecimal.ZERO;
        BigDecimal vIbsUf = BigDecimal.ZERO;
        BigDecimal vIbsMun = BigDecimal.ZERO;
        BigDecimal vCbs = BigDecimal.ZERO;
        for (ItemDocumentoDTO item : itens) {
            SnapshotFiscalItemDTO f = item.fiscal();
            vProd = vProd.add(item.valorTotal());
            vBcIcms = soma(vBcIcms, f.baseCalculoIcms());
            vIcms = soma(vIcms, f.valorIcms());
            vPis = soma(vPis, f.valorPis());
            vCofins = soma(vCofins, f.valorCofins());
            vBcIbsCbs = soma(vBcIbsCbs, f.baseCalculoIbsCbs());
            vIbsUf = soma(vIbsUf, f.valorIbsEstadual());
            vIbsMun = soma(vIbsMun, f.valorIbsMunicipal());
            vCbs = soma(vCbs, f.valorCbs());
        }
        String zero = dinheiro(BigDecimal.ZERO);
        NfeXml.IcmsTot icmsTot = new NfeXml.IcmsTot(dinheiro(vBcIcms), dinheiro(vIcms), zero, zero, zero, zero, zero,
                zero, dinheiro(vProd), zero, zero, zero, zero, zero, zero, dinheiro(vPis), dinheiro(vCofins), zero,
                dinheiro(vProd));
        NfeXml.IbsCbsTot ibsCbsTot = new NfeXml.IbsCbsTot(dinheiro(vBcIbsCbs),
                new NfeXml.TotIbs(new NfeXml.TotIbsUf(zero, zero, dinheiro(vIbsUf)),
                        new NfeXml.TotIbsMun(zero, zero, dinheiro(vIbsMun)),
                        dinheiro(vIbsUf.add(vIbsMun)), zero, zero),
                new NfeXml.TotCbs(zero, zero, dinheiro(vCbs), zero, zero));
        return new NfeXml.Total(icmsTot, ibsCbsTot);
    }

    private static boolean semIncidencia(String cst) {
        return cst.compareTo("04") >= 0 && cst.compareTo("09") <= 0;
    }

    private static boolean aliquotaNormal(String cst) {
        return "01".equals(cst) || "02".equals(cst);
    }

    private static BigDecimal soma(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    /** 13v2 — valores monetários, sempre 2 casas. */
    static String dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 3v2-4 — percentuais, de 2 a 4 casas. */
    static String percentual(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        int casas = Math.min(4, Math.max(2, v.stripTrailingZeros().scale()));
        return v.setScale(casas, RoundingMode.HALF_UP).toPlainString();
    }

    /** 11v0-4 — quantidades, de 0 a 4 casas, sem zeros à direita. */
    static String quantidade(BigDecimal valor) {
        return semZerosAdireita(valor.setScale(4, RoundingMode.HALF_UP));
    }

    /** 11v0-10 — valor unitário, de 0 a 10 casas, sem zeros à direita. */
    static String valorUnitario(BigDecimal valor) {
        return semZerosAdireita(valor.setScale(10, RoundingMode.HALF_UP));
    }

    private static String semZerosAdireita(BigDecimal valor) {
        BigDecimal semZeros = valor.stripTrailingZeros();
        return (semZeros.scale() < 0 ? semZeros.setScale(0) : semZeros).toPlainString();
    }
}
