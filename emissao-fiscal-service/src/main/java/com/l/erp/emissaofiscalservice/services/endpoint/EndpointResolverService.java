package com.l.erp.emissaofiscalservice.services.endpoint;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.ServicoWebservice;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.UfAutorizador;
import com.l.erp.emissaofiscalservice.domain.WebserviceEndpoint;
import com.l.erp.emissaofiscalservice.repository.UfAutorizadorRepository;
import com.l.erp.emissaofiscalservice.repository.WebserviceEndpointRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Resolve a URL final de um serviço em runtime — spec §3 item 12:
 * {@code (estabelecimento, documento) → UF do emitente → uf_autorizador → contingencia_status
 * decide normal/contingência → webservice_endpoint → URL}. Este serviço cobre até
 * {@code uf_autorizador}; a leitura de {@code contingencia_status} (força manual pelo tenant) fica
 * pra quando a Etapa 2 ligar a máquina de estados de verdade — por ora quem chama informa
 * {@code contingencia} diretamente.
 *
 * <p>ponytail: sem {@code @Cacheable} ainda — spec pede (dado muda raro), mas ninguém chama isso
 * em caminho quente até a Etapa 2 existir; adicionar cache antes disso é otimização sem uso real.</p>
 */
@Service
public class EndpointResolverService {

    private final UfAutorizadorRepository ufAutorizadorRepository;
    private final WebserviceEndpointRepository webserviceEndpointRepository;

    public EndpointResolverService(UfAutorizadorRepository ufAutorizadorRepository,
                                    WebserviceEndpointRepository webserviceEndpointRepository) {
        this.ufAutorizadorRepository = ufAutorizadorRepository;
        this.webserviceEndpointRepository = webserviceEndpointRepository;
    }

    public String resolverUrl(String uf, TipoDocumentoFiscal documento, ServicoWebservice servico,
                               AmbienteEmissao ambiente, boolean contingencia) {
        LocalDate hoje = LocalDate.now();

        UfAutorizador ufAutorizador = ufAutorizadorRepository.buscarVigentes(uf, documento, hoje).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Nenhum autorizador configurado para UF " + uf + " (" + documento + ").",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        String autorizador = contingencia
                ? ufAutorizador.getAutorizadorContingencia()
                : ufAutorizador.getAutorizadorNormal();

        return webserviceEndpointRepository.buscarVigentes(documento, autorizador, servico, ambiente, hoje).stream()
                .findFirst()
                .map(WebserviceEndpoint::getUrl)
                .orElseThrow(() -> new BusinessException(
                        "Nenhum endpoint configurado para autorizador " + autorizador + ", serviço " + servico
                                + ", ambiente " + ambiente + ".",
                        HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
