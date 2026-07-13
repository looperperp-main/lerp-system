package com.l.erp.partnerservice.services;

import com.l.erp.partnerservice.api.dto.FeatureStatDTO;
import com.l.erp.partnerservice.domain.TrialEngagement;
import com.l.erp.partnerservice.repository.TrialEngagementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialEngagementServiceTest {

    @Mock
    private TrialEngagementRepository repository;

    @InjectMocks
    private TrialEngagementService engagementService;

    private static final Long TENANT_ID = 42L;

    private TrialEngagement buildEngagement(String featureKey, int accessCount) {
        TrialEngagement e = new TrialEngagement();
        e.setTenantId(TENANT_ID);
        e.setFeatureKey(featureKey);
        e.setAccessCount(accessCount);
        e.setLastAccessedAt(OffsetDateTime.now());
        return e;
    }

    @Test
    void shouldRegistrarFeatureAccess() {
        engagementService.registrar(TENANT_ID, "nfe");

        verify(repository).upsertFeatureAccess(eq(TENANT_ID), eq("nfe"), any(OffsetDateTime.class));
    }

    @Test
    void shouldSwallowExceptionWhenRegistrarFails() {
        doThrow(new RuntimeException("db down")).when(repository)
                .upsertFeatureAccess(anyLong(), org.mockito.ArgumentMatchers.anyString(), any());

        // não deve propagar a exceção — apenas loga
        assertThatCode(() -> engagementService.registrar(TENANT_ID, "nfe"))
                .doesNotThrowAnyException();

        verify(repository).upsertFeatureAccess(eq(TENANT_ID), eq("nfe"), any(OffsetDateTime.class));
    }

    @Test
    void shouldGetEngagementSortedByAccessCountDescendingAndExcludeLogin() {
        when(repository.findByTenantId(TENANT_ID)).thenReturn(List.of(
                buildEngagement(TrialEngagementService.FEATURE_LOGIN, 99),
                buildEngagement("nfe", 3),
                buildEngagement("relatorios", 10)
        ));

        List<FeatureStatDTO> result = engagementService.getEngagement(TENANT_ID);

        assertThat(result).hasSize(TrialEngagementService.FEATURE_CATALOG.size());
        assertThat(result.getFirst().featureKey()).isEqualTo("relatorios");
        assertThat(result.getFirst().accessCount()).isEqualTo(10);
        assertThat(result.stream().map(FeatureStatDTO::featureKey)).doesNotContain(TrialEngagementService.FEATURE_LOGIN);
    }

    @Test
    void shouldGetEngagementWithZeroAccessCountWhenNoRecords() {
        when(repository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        List<FeatureStatDTO> result = engagementService.getEngagement(TENANT_ID);
        assertThat(result)
                .hasSize(TrialEngagementService.FEATURE_CATALOG.size())
                .allMatch(f -> f.accessCount() == 0 && f.lastAccessedAt() == null);
    }

    @Test
    void shouldGetAdoptionGapsExcludingAccessedFeatures() {
        when(repository.findByTenantId(TENANT_ID)).thenReturn(List.of(
                buildEngagement("nfe", 5),
                buildEngagement("relatorios", 0) // acessado zero vezes ainda conta como gap
        ));

        List<String> gaps = engagementService.getAdoptionGaps(TENANT_ID);

        assertThat(gaps)
                .doesNotContain(TrialEngagementService.FEATURE_CATALOG.get("nfe"))
                .contains(TrialEngagementService.FEATURE_CATALOG.get("relatorios"))
                .hasSize(TrialEngagementService.FEATURE_CATALOG.size() - 1);
    }

    @Test
    void shouldReturnAllFeaturesAsGapsWhenNoEngagement() {
        when(repository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        List<String> gaps = engagementService.getAdoptionGaps(TENANT_ID);

        assertThat(gaps).hasSize(TrialEngagementService.FEATURE_CATALOG.size());
    }

    @Test
    void shouldGetLoginStatsWhenPresent() {
        TrialEngagement login = buildEngagement(TrialEngagementService.FEATURE_LOGIN, 7);
        when(repository.findByTenantIdAndFeatureKey(TENANT_ID, TrialEngagementService.FEATURE_LOGIN))
                .thenReturn(Optional.of(login));

        Optional<TrialEngagement> result = engagementService.getLoginStats(TENANT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getAccessCount()).isEqualTo(7);
    }

    @Test
    void shouldGetLoginStatsWhenAbsent() {
        when(repository.findByTenantIdAndFeatureKey(TENANT_ID, TrialEngagementService.FEATURE_LOGIN))
                .thenReturn(Optional.empty());

        Optional<TrialEngagement> result = engagementService.getLoginStats(TENANT_ID);

        assertThat(result).isEmpty();
    }
}
