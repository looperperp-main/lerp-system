package com.l.erp.authservice;

import com.l.erp.authservice.api.controllers.AuditController;
import com.l.erp.authservice.api.dto.audit.AuditLogDTO;
import com.l.erp.authservice.api.mappers.AuditMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.audit.AuditLog;
import com.l.erp.authservice.repositorios.audit.AuditRepository;
import com.l.erp.authservice.services.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuditService.class, ObjectMapperConfig.class})
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditRepository auditRepository;

    @MockitoBean
    private AuditMapper auditMapper;

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetAuditLogs() throws Exception {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID());
        auditLog.setAction("LOGIN_SUCCESS");
        auditLog.setActorUserId(UUID.randomUUID());
        auditLog.setTargetType("USER");
        auditLog.setResult("SUCCESS");
        auditLog.setDetailsJson("{}");
        auditLog.setEventDate(Instant.now());

        AuditLogDTO dto = new AuditLogDTO(
                auditLog.getId(),
                auditLog.getActorUserId(),
                "LOGIN_SUCCESS",
                "USER",
                auditLog.getActorUserId(),
                "SUCCESS",
                "{}",
                null,
                Instant.now()
        );

        Page<AuditLog> page = new PageImpl<>(List.of(auditLog));
        when(auditRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(auditMapper.toAuditLogDTO(any(AuditLog.class))).thenReturn(dto);

        mockMvc.perform(get("/auth/audits?page=0&size=25"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldGetAuditLogsEmpty() throws Exception {
        Page<AuditLog> emptyPage = new PageImpl<>(List.of());
        when(auditRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/auth/audits"))
                .andDo(print())
                .andExpect(status().isOk());
    }
}
