package com.l.erp.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.controllers.UserController;
import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.UserAccountDTO;
import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.api.mappers.UserMapper;
import com.l.erp.authservice.configuration.ObjectMapperConfig;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.services.UserService;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.Constants;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.authservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ UserService.class, ObjectMapperConfig.class})
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private UserMapper userMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private TenantRepository tenantRepository;

    @MockitoBean
    private PasswordValidatorUtil passwordValidatorUtil;

    @MockitoBean
    private OwnerMarkerRepository ownerMarkerRepository;

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldCreateUser() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UUID id = UUID.randomUUID();

        UserAccountDTO input = new UserAccountDTO(null,1L,"usuario@usuario.com",
                "asdasdsadasd","USER",true, null, Instant.now(),"seed",null,null);

        UserAccountDTO created = new UserAccountDTO(id,1L,"usuario@usuario.com",
                "asdasdsadasd","USER",true, null, Instant.now(),"seed",null,null);


        UserAccount entity = new UserAccount();
        UserAccount saved = new UserAccount();
        saved.setId(id);

        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setName("Empresa X");

        when(tenantRepository.findById(anyLong())).thenReturn(Optional.of(tenant));

        when(userMapper.toUserAccount(input)).thenReturn(entity);
        when(userAccountRepository.save(any())).thenReturn(saved);
        when(userMapper.toUserAccountDTO(any())).thenReturn(created);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("usuario@usuario.com"));
        }

    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldntCreateUserDuplicated() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UUID id = UUID.randomUUID();

        UserAccountDTO input = new UserAccountDTO(null,1L,"usuario@usuario.com",
                "asdasdsadsd","USER",true, null, Instant.now(),"seed",null,null);

        when(userAccountRepository.findByEmail(input.email())).thenReturn(Optional.of(new UserAccount()));

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("E-mail já está em uso"));
        }

    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldntCreateUserWithoutTenant() throws Exception {
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UUID id = UUID.randomUUID();

        UserAccountDTO input = new UserAccountDTO(null,null,"usuario@usuario.com",
                "asdasdsadasd","USER",true, null, Instant.now(),"seed",null,null);


        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));


            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input))
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("O ID do Tenant é obrigatório para criar um usuário"));
        }

    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldReturnUsers() throws Exception {

        UserAccountPageDTO user = new UserAccountPageDTO(
                null,null,null,null,
                true,null,null,null,null,null
        );


        Page<UserAccountPageDTO> page = new PageImpl<>(List.of(user));
        when(userAccountRepository.findAllProjectedBy(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/auth/users?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldReturnActiveUsers() throws Exception {

        UserAccountPageDTO user = new UserAccountPageDTO(
                null,null,null,null,
                true,null,null,null,null,null
        );


        Page<UserAccountPageDTO> page = new PageImpl<>(List.of(user));
        when(userAccountRepository.findAllProjectedBy(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/auth/users/active?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldupdateUserStatusById() throws Exception {
        UUID id = UUID.randomUUID();
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName("NAME");
        user.setActive(true);

        when(userAccountRepository.findById(id)).thenReturn(Optional.of(user));
        when(ownerMarkerRepository.existsByUser_IdAndEnabledTrue(any())).thenReturn(false);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));

            mockMvc.perform(patch("/auth/users/{userId}/status",id)
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldupdateUserStatusByIdErrorUserNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName("NAME");
        user.setActive(true);

        when(ownerMarkerRepository.existsByUser_IdAndEnabledTrue(any())).thenReturn(false);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));

            mockMvc.perform(patch("/auth/users/{userId}/status",id)
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Usuário não Encontrado"));

        }
    }

    @Test
    @WithMockUser(roles = "APP_OWNER")
    void shouldupdateUserStatusByIdErrorOwnerError() throws Exception {
        UUID id = UUID.randomUUID();
        var TOKEN_ATTR_NAME = "_csrf";
        var httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        var csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDisplayName("NAME");
        user.setActive(true);

        when(userAccountRepository.findById(id)).thenReturn(Optional.of(user));
        when(ownerMarkerRepository.existsByUser_IdAndEnabledTrue(any())).thenReturn(true);

        try (MockedStatic<SecurityUtils> securityUtils = Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(UUID.randomUUID(),"usuario@teste.com"));

            mockMvc.perform(patch("/auth/users/{userId}/status",id)
                            .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(Constants.USER_HAS_OWNER_MARKER));

        }
    }


}
