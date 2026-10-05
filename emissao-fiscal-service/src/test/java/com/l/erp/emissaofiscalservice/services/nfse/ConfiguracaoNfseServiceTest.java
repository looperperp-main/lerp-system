package com.l.erp.emissaofiscalservice.services.nfse;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.ConfiguracaoNfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoNfseServiceTest {

    private static final Long TENANT = 9L;
    private static final UUID EMITENTE = UUID.randomUUID();

    @Mock
    private ConfiguracaoNfseRepository repository;

    private ConfiguracaoNfseService service;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(Constants.HEADER_TENANT_ID, String.valueOf(TENANT));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        service = new ConfiguracaoNfseService(repository);
        lenient().when(repository.save(any(ConfiguracaoNfse.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void primeiroCadastroCriaComAdnPorPadraoEDatas() {
        when(repository.findByTenantIdAndEmitenteId(TENANT, EMITENTE)).thenReturn(Optional.empty());

        ConfiguracaoNfse salva = service.salvar(EMITENTE, new ConfiguracaoNfseRequestDTO("3104007", null, "12345"));

        assertEquals(TENANT, salva.getTenantId());
        assertEquals(EMITENTE, salva.getEmitenteId());
        assertEquals("3104007", salva.getMunicipioIbge());
        assertEquals(CodigoProvedorNfse.ADN, salva.getProvedor());
        assertEquals("12345", salva.getInscricaoMunicipal());
        assertNotNull(salva.getCreatedAt());
    }

    @Test
    void cadastroExistenteEhAtualizadoMantendoADataDeCriacao() {
        ConfiguracaoNfse existente = new ConfiguracaoNfse();
        existente.setMunicipioIbge("3550308");
        OffsetDateTime criadoEm = OffsetDateTime.now().minusDays(3);
        existente.setCreatedAt(criadoEm);
        when(repository.findByTenantIdAndEmitenteId(TENANT, EMITENTE)).thenReturn(Optional.of(existente));

        ConfiguracaoNfse salva = service.salvar(EMITENTE, new ConfiguracaoNfseRequestDTO("3104007", null, null));

        assertSame(existente, salva);
        assertEquals("3104007", salva.getMunicipioIbge());
        assertEquals(criadoEm, salva.getCreatedAt());
    }

    @Test
    void buscarSemConfiguracaoEh404() {
        when(repository.findByTenantIdAndEmitenteId(TENANT, EMITENTE)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class, () -> service.buscar(EMITENTE));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }
}
