package com.l.erp.emissaofiscalservice.services.nfse;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import com.l.erp.emissaofiscalservice.repository.ConfiguracaoNfseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProvedorNfseResolverTest {

    private final ConfiguracaoNfseRepository repositorio = mock(ConfiguracaoNfseRepository.class);

    @Test
    void devolveOAdaptadorRegistradoParaOCodigo() {
        ProvedorNfse adn = mock(ProvedorNfse.class);
        when(adn.codigo()).thenReturn(CodigoProvedorNfse.ADN);

        assertSame(adn, new ProvedorNfseResolver(repositorio, List.of(adn)).porCodigo(CodigoProvedorNfse.ADN));
    }

    @Test
    void semAdaptadorOuSemCodigoRecusaCom422() {
        var resolver = new ProvedorNfseResolver(repositorio, List.of());

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(BusinessException.class, () -> resolver.porCodigo(CodigoProvedorNfse.ADN)).getStatus());
        assertThrows(BusinessException.class, () -> resolver.porCodigo(null));
    }

    @Test
    void configuracaoDoEmitenteExisteOuRecusaCom422() {
        UUID configurado = UUID.randomUUID();
        UUID semConfiguracao = UUID.randomUUID();
        ConfiguracaoNfse configuracao = new ConfiguracaoNfse();
        when(repositorio.findByTenantIdAndEmitenteId(1L, configurado)).thenReturn(Optional.of(configuracao));
        when(repositorio.findByTenantIdAndEmitenteId(1L, semConfiguracao)).thenReturn(Optional.empty());
        var resolver = new ProvedorNfseResolver(repositorio, List.of());

        assertSame(configuracao, resolver.configuracaoDe(1L, configurado));
        assertThrows(BusinessException.class, () -> resolver.configuracaoDe(1L, semConfiguracao));
    }
}
