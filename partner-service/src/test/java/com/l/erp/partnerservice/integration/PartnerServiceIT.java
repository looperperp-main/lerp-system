package com.l.erp.partnerservice.integration;

import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.repository.PartnerRepository;
import com.l.erp.partnerservice.services.CnpjService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerServiceIT extends AbstractIntegrationTest {

    @Autowired
    PartnerRepository partnerRepository;

    @MockitoBean
    CnpjService cnpjService;

    @BeforeEach
    void clean() {
        partnerRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrievePartner() {
        Partner partner = buildPartner("12345678000195", "Contabilidade IT Ltda", "REF-IT-001");
        Partner saved = partnerRepository.save(partner);

        Optional<Partner> found = partnerRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCnpj()).isEqualTo("12345678000195");
    }

    @Test
    void shouldFindPartnerByReferralCode() {
        Partner partner = buildPartner("12345678000195", "Contabilidade IT Ltda", "REF-UNIQUE-01");
        partnerRepository.save(partner);

        List<Partner> all = partnerRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getReferralCode()).isEqualTo("REF-UNIQUE-01");
    }

    private static Partner buildPartner(String cnpj, String name, String referralCode) {
        Partner p = new Partner();
        p.setName(name);
        p.setEmail("partner@it.com");
        p.setCnpj(cnpj);
        p.setReferralCode(referralCode);
        p.setCommissionRate(new BigDecimal("5.00"));
        p.setStatus("PENDENTE");
        p.setCreatedAt(OffsetDateTime.now());
        p.setCreatedBy("test");
        return p;
    }
}