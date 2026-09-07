package com.l.erp.authservice.infra;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.api.dto.AtivarContaRequest;
import com.l.erp.authservice.api.dto.CriarContaGratisRequest;
import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.TenantLoginResponse;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.infra.client.CadastroServiceClient;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.repositorios.OwnerMarkerRepository;
import com.l.erp.authservice.repositorios.RolePermissionRepository;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.UserAccountRepository;
import com.l.erp.authservice.repositorios.UserRoleRepository;
import com.l.erp.authservice.services.TenantOwnerBootstrapService;
import com.l.erp.authservice.services.TrialEngagementService;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.PasswordValidatorUtil;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a resolução da claim `roles` do JWT (AuthService.resolveRoles) — antes hardcoded
 * (isOwner ? [APP_OWNER, TENANT_OWNER] : ["ROLE_USER"]), agora busca a role real via user_role
 * (spec/auditoria.md §4.11).
 */
class AuthServiceTest {

    private UserAccountRepository userRepo;
    private OwnerMarkerRepository ownerRepo;
    private UserRoleRepository userRoleRepository;
    private TenantRepository tenantRepository;
    private TokenService tokenService;
    private RefreshTokenService refreshTokenService;
    private AuthMapper authMapper;
    private CadastroServiceClient cadastroServiceClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserAccountRepository.class);
        ownerRepo = mock(OwnerMarkerRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        authMapper = mock(AuthMapper.class);
        userRoleRepository = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermissionRepository = mock(RolePermissionRepository.class);
        AuditService auditService = mock(AuditService.class);
        PasswordValidatorUtil passwordValidatorUtil = mock(PasswordValidatorUtil.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        TrialEngagementService trialEngagementService = mock(TrialEngagementService.class);
        TenantOwnerBootstrapService tenantOwnerBootstrapService = mock(TenantOwnerBootstrapService.class);
        cadastroServiceClient = mock(CadastroServiceClient.class);

        authService = new AuthService(userRepo, ownerRepo, tenantRepository, tokenService, refreshTokenService,
                passwordEncoder, authMapper, userRoleRepository, rolePermissionRepository, auditService,
                passwordValidatorUtil, kafkaTemplate, objectMapper, trialEngagementService, tenantOwnerBootstrapService,
                cadastroServiceClient);

        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(refreshTokenService.issue(any(), any())).thenReturn(new RefreshTokenService.TokenPair("refresh-raw", null));
        when(authMapper.toLoginResponse(any(), any(), any())).thenReturn(mock(LoginResponse.class));
    }

    private UserAccount user() {
        UserAccount u = new UserAccount();
        u.setId(UUID.randomUUID());
        u.setEmail("user@empresa.com");
        u.setDisplayName("User");
        u.setPasswordHash("hash");
        u.setUserType(Constants.TENANT_USER);
        u.setActive(true);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        u.setTenant(tenant);
        return u;
    }

    private UserRole userRoleWithName(String roleName) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleName);
        UserRole ur = new UserRole();
        ur.setRole(role);
        return ur;
    }

    @SuppressWarnings("unchecked")
    private List<String> loginAndCaptureRoles() {
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        authService.login("user@empresa.com", "senha-correta");
        verify(tokenService).generateToken(any(), rolesCaptor.capture(), anyBoolean(), anyList());
        return rolesCaptor.getValue();
    }

    @Test
    void naoOwner_semRoleAtribuida_naoRecebeRoleUserFixo() {
        UserAccount user = user();
        when(userRepo.findByEmail("user@empresa.com")).thenReturn(Optional.of(user));
        when(ownerRepo.existsByUser_IdAndEnabledTrue(user.getId())).thenReturn(false);
        when(userRoleRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<String> roles = loginAndCaptureRoles();

        assertThat(roles).doesNotContain("ROLE_USER");
        assertThat(roles).isEmpty();
    }

    @Test
    void naoOwner_comRoleAtribuida_recebeARoleRealDoUserRole() {
        UserAccount user = user();
        when(userRepo.findByEmail("user@empresa.com")).thenReturn(Optional.of(user));
        when(ownerRepo.existsByUser_IdAndEnabledTrue(user.getId())).thenReturn(false);
        when(userRoleRepository.findAllByUserId(user.getId())).thenReturn(List.of(userRoleWithName(Constants.OWNER_ROLE_NAME)));

        List<String> roles = loginAndCaptureRoles();

        assertThat(roles).containsExactly(Constants.OWNER_ROLE_NAME);
        assertThat(roles).doesNotContain("ROLE_USER");
    }

    @Test
    void owner_semRoleAtribuida_recebeApenasFlagAppOwner() {
        UserAccount user = user();
        when(userRepo.findByEmail("user@empresa.com")).thenReturn(Optional.of(user));
        when(ownerRepo.existsByUser_IdAndEnabledTrue(user.getId())).thenReturn(true);
        when(userRoleRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        List<String> roles = loginAndCaptureRoles();

        assertThat(roles).containsExactly(Roles.APP_OWNER);
    }

    @Test
    void owner_comRolePropriaAtribuida_recebeRoleRealMaisFlagAppOwner() {
        UserAccount user = user();
        when(userRepo.findByEmail("user@empresa.com")).thenReturn(Optional.of(user));
        when(ownerRepo.existsByUser_IdAndEnabledTrue(user.getId())).thenReturn(true);
        when(userRoleRepository.findAllByUserId(user.getId())).thenReturn(List.of(userRoleWithName(Constants.OWNER_ROLE_NAME)));

        List<String> roles = loginAndCaptureRoles();

        assertThat(roles).containsExactlyInAnyOrder(Constants.OWNER_ROLE_NAME, Roles.APP_OWNER);
    }

    // ── CNPJ: dígito verificador (spec/auditoria.md §7.7) ──

    @Test
    void cnpj_numericoComDvValido_exemploOficialSerpro_passa() {
        // 11.222.333/0001-81 — CNPJ de exemplo publicado pela Receita/Serpro em documentação
        // oficial; vetor conhecido, não inventado, então confirma o algoritmo mod-11 batendo
        // com um valor real, não só consigo mesmo.
        assertThat(AuthService.cnpjTemDvValido("11222333000181")).isTrue();
    }

    @Test
    void cnpj_numericoComDvInvalido_falha() {
        assertThat(AuthService.cnpjTemDvValido("11222333000180")).isFalse();
    }

    @Test
    void cnpj_alfanumericoComDvValido_passa() {
        // Não é um vetor oficial publicado (não achei um caso de teste oficial da NT 2026.004
        // pra citar) — é auto-consistente: DV calculado à mão com o mesmo algoritmo
        // (valor de cada caractere = ASCII - 48) que o código usa. Confirma que letra na base
        // não quebra o cálculo, mas não é prova independente de que bate com a Receita.
        assertThat(AuthService.cnpjTemDvValido("1234567A000104")).isTrue();
    }

    @Test
    void cnpj_tamanhoErrado_falha() {
        assertThat(AuthService.cnpjTemDvValido("1122233300018")).isFalse(); // 13 chars
        assertThat(AuthService.cnpjTemDvValido("112223330001811")).isFalse(); // 15 chars
    }

    @Test
    void cnpj_null_falha() {
        assertThat(AuthService.cnpjTemDvValido(null)).isFalse();
    }

    @Test
    void cnpj_digitosVerificadoresNaoNumericos_falha() {
        assertThat(AuthService.cnpjTemDvValido("112223330001AB")).isFalse();
    }

    @Test
    void normalizeCnpj_removePontuacaoEUppercase() {
        // Cadastro e login precisam gravar/buscar o mesmo formato — sem pontuação, uppercase.
        assertThat(AuthService.normalizeCnpj("12.abc.678/0001-95")).isEqualTo("12ABC678000195");
        assertThat(AuthService.normalizeCnpj(null)).isNull();
    }

    // ── Fase 4 (spec/estabelecimentos-filiais.md §6): os dois pontos que criam um tenant
    // "de verdade" fora de TenantService.createTenant também precisam provisionar a
    // pessoa/matriz própria no cadastro-service. ──

    @Test
    void ativarConta_provisionaPessoaPropriaAposAtivar() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setStatus(EnumTenantStatus.CONVIDADO);
        tenant.setEmail("convidado@empresa.com");
        tenant.setName("Empresa Convidada");

        DecodedJWT decoded = mock(DecodedJWT.class);
        when(decoded.getSubject()).thenReturn("1");
        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("convidado@empresa.com");
        when(decoded.getClaim(Constants.EMAIL)).thenReturn(emailClaim);
        when(decoded.getClaim("referralId")).thenReturn(mock(Claim.class));
        when(tokenService.validateInvitationToken("token-valido")).thenReturn(decoded);

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepo.findByEmailAndTenantId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        doAnswer(inv -> {
            UserAccount saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        }).when(userRepo).save(any(UserAccount.class));

        authService.ativarConta(new AtivarContaRequest("token-valido", "senhaForteDemais123", "senhaForteDemais123"));

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(cadastroServiceClient).provisionarPessoaPropria(eq(tenant), userIdCaptor.capture());
        assertThat(userIdCaptor.getValue()).isNotNull();
    }

    @Test
    void criarContaGratis_provisionaPessoaPropriaAposCriar() {
        when(tenantRepository.findByCnpj(anyString())).thenReturn(Optional.empty());
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        doAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(2L);
            return t;
        }).when(tenantRepository).save(any(Tenant.class));
        doAnswer(inv -> {
            UserAccount saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        }).when(userRepo).save(any(UserAccount.class));
        when(authMapper.toTenantLoginResponse(any(), any(), any(), any())).thenReturn(mock(TenantLoginResponse.class));

        authService.criarContaGratis(new CriarContaGratisRequest("11222333000181", "Empresa Nova Ltda",
                "Empresa Nova", "nova@empresa.com", "senhaForteDemais123", "11999999999"));

        verify(cadastroServiceClient).provisionarPessoaPropria(any(Tenant.class), any(UUID.class));
    }
}