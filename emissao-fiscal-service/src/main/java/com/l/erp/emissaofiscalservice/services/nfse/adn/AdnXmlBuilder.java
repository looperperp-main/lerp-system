package com.l.erp.emissaofiscalservice.services.nfse.adn;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.EnderecoDTO;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import org.springframework.stereotype.Service;
import tools.jackson.dataformat.xml.XmlMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monta o XML da DPS e do pedido de cancelamento no leiaute 1.00 do Sistema Nacional NFS-e. Só monta
 * e formata — a assinatura e o envio são do {@link ProvedorNfseAdn}. O {@code XmlMapper} é privado de
 * propósito (mesmo motivo do {@code NfeXmlBuilder}: bean compartilhado mudaria a serialização de outros).
 */
@Service
public class AdnXmlBuilder {

    private static final String NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    private static final XmlMapper XML = XmlMapper.builder().build();
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    /** {@code municipioEmissor} é o município onde o prestador está cadastrado para emitir (cLocEmi). */
    public String montarDps(NfseRequestDTO pedido, String municipioEmissor, long numero, OffsetDateTime emissao) {
        var prestador = pedido.prestador();
        DpsXml.InfDPS infDps = new DpsXml.InfDPS(
                idDps(municipioEmissor, prestador.cnpj(), pedido.serie(), numero),
                tpAmb(pedido.ambiente()),
                emissao.format(DATA_HORA),
                Constants.NFSE_VERSAO_APLICATIVO,
                "%05d".formatted(Long.parseLong(pedido.serie())),
                String.valueOf(numero),
                pedido.competencia().toString(),
                Constants.NFSE_EMITENTE_DPS_PRESTADOR,
                municipioEmissor,
                prestador(prestador),
                tomador(pedido.tomador()),
                new DpsXml.Serv(
                        new DpsXml.LocPrest(pedido.servico().municipioPrestacao()),
                        new DpsXml.CServ(pedido.servico().codigoTributacaoNacional(),
                                pedido.servico().codigoTributacaoMunicipal(), pedido.servico().descricao())),
                valores(pedido.valores()));
        return envolver("DPS", XML.writeValueAsString(infDps));
    }

    /** Pedido de registro do evento de cancelamento (e101101), ainda sem assinatura. */
    public String montarCancelamento(String ambiente, String cnpjAutor, String chaveNfse, String motivo,
                                     OffsetDateTime momento) {
        EventoXml.InfPedReg infPedReg = new EventoXml.InfPedReg(
                idPedidoCancelamento(chaveNfse),
                tpAmb(ambiente),
                Constants.NFSE_VERSAO_APLICATIVO,
                momento.format(DATA_HORA),
                cnpjAutor,
                chaveNfse,
                "1", // evento que só ocorre uma vez: nPedRegEvento = 1
                new EventoXml.E101101(Constants.NFSE_DESCRICAO_EVENTO_CANCELAMENTO,
                        Constants.NFSE_MOTIVO_CANCELAMENTO_OUTROS, motivo));
        return envolver("pedRegEvento", XML.writeValueAsString(infPedReg));
    }

    /** "DPS" + município (7) + tipo de inscrição (1) + CNPJ (14) + série (5) + número (15) = 45 posições. */
    public static String idDps(String municipioEmissor, String cnpj, String serie, long numero) {
        return "DPS" + municipioEmissor + Constants.NFSE_TIPO_INSCRICAO_CNPJ + cnpj
                + "%05d".formatted(Long.parseLong(serie)) + "%015d".formatted(numero);
    }

    /** "PRE" + chave da NFS-e (50) + código do evento (6) + nPedRegEvento (3) = 62 posições (59 dígitos). */
    public static String idPedidoCancelamento(String chaveNfse) {
        return "PRE" + chaveNfse + Constants.NFSE_CODIGO_EVENTO_CANCELAMENTO + "001";
    }

    /** 1 = produção, 2 = homologação. */
    public static String tpAmb(String ambiente) {
        return AmbienteEmissao.PRODUCAO.name().equalsIgnoreCase(ambiente) ? "1" : "2";
    }

    private static String envolver(String raiz, String conteudo) {
        return "<" + raiz + " xmlns=\"" + NAMESPACE + "\" versao=\"" + Constants.NFSE_VERSAO_LEIAUTE + "\">"
                + conteudo + "</" + raiz + ">";
    }

    private static DpsXml.Prest prestador(NfseRequestDTO.Prestador p) {
        String regimeApuracao = "3".equals(p.opcaoSimplesNacional()) ? p.regimeApuracaoSimples() : null;
        return new DpsXml.Prest(p.cnpj(), vazioParaNulo(p.inscricaoMunicipal()), p.razaoSocial(), endereco(p.endereco()),
                p.endereco().telefone(), p.email(),
                new DpsXml.RegTrib(p.opcaoSimplesNacional(), regimeApuracao, p.regimeEspecialTributacao()));
    }

    private static DpsXml.Toma tomador(NfseRequestDTO.Tomador t) {
        if (t == null) {
            return null;
        }
        boolean cnpj = t.documento().length() == 14;
        return new DpsXml.Toma(cnpj ? t.documento() : null, cnpj ? null : t.documento(), t.nome(),
                t.endereco() == null ? null : endereco(t.endereco()),
                t.endereco() == null ? null : t.endereco().telefone(), t.email());
    }

    private static DpsXml.End endereco(EnderecoDTO e) {
        return new DpsXml.End(new DpsXml.EndNac(e.codigoMunicipio(), e.cep()), e.logradouro(), e.numero(),
                vazioParaNulo(e.complemento()), e.bairro());
    }

    private static DpsXml.Valores valores(NfseRequestDTO.Valores v) {
        return new DpsXml.Valores(
                new DpsXml.VServPrest(dinheiro(v.valorServico())),
                new DpsXml.Trib(
                        new DpsXml.TribMun(v.tributacaoIssqn(),
                                v.aliquotaIssqn() == null ? null : dinheiro(v.aliquotaIssqn()), v.tipoRetencaoIssqn()),
                        new DpsXml.TotTrib(Constants.NFSE_IND_TOT_TRIB_NAO_INFORMA)));
    }

    /** Duas casas decimais, ponto, sem notação científica — o formato dos tipos TSDec15V2/TSDec1V2. */
    private static String dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
