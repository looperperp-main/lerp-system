package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.common.util.Constants;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.util.HtmlSanitizerUtil;
import com.l.erp.common.exception.custom.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.l.erp.authservice.util.SecurityUtils.getCorrelationIdFromRequest;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TenantService {

    private final Logger logger = LoggerFactory.getLogger(TenantService.class);
    private static final String ENTITY_NAME = "Tenant";

    private final TenantRepository tenantRepository;

    private final AuditService auditService;

    private final AuthMapper authMapper;

    public TenantService(TenantRepository tenantRepository,
                         AuditService auditService,
                         AuthMapper authMapper) {
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.authMapper = authMapper;
    }

    /**
     * Cria um novo tenant
     * @param tenantDTO tenant a ser criado
     * @return dado criado
     */
    public TenantDTO createTenant(TenantDTO tenantDTO){
        logger.debug("Creating Tenant : {}", tenantDTO);
        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        Tenant tenant = authMapper.toTenant(tenantDTO);
        tenant.setStatus(EnumTenantStatus.PENDENTE);
        tenant.setCreationDate(Instant.now());
        tenant.setCreatedBy(currentUser.email());
        Tenant tenantSaved = tenantRepository.save(tenant);
        UUID correlationId = getCorrelationIdFromRequest(logger);
        auditService.logAuditEvent(Constants.TENANT_CREATION, Constants.TENANT, null, Constants.SUCCESS, null,correlationId);
        return sanitizeDto(authMapper.toTenantDTO(tenantSaved));

    }

    /**
     * Retorna todos os tenants cadastrados
     *
     * @return lista de tenants
     */
    public Page<TenantDTO> getAllTenants(Pageable pageable){
        logger.debug("REST request to get all Tenants");
        Page<Tenant> tenants = tenantRepository.findAll(pageable);
        return tenants.map(authMapper::toTenantDTO);
    }

    /**
     * Retorna todos os tenants cadastrados
     *
     * @return lista de tenants
     */
    public Page<TenantDTO> getAllActiveTenants(Pageable pageable){
        logger.debug("REST request to get all Tenants using a Pageable");
        Page<Tenant> tenants = tenantRepository.findAllByStatusIs(EnumTenantStatus.ATIVO,pageable);
        return tenants.map(authMapper::toTenantDTO);
    }

    /**
     * Retorna um tenant pelo id
     * @param tenantId id do tenant
     * @return tenant
     */
    public TenantDTO getTenantById(Long tenantId) {
        logger.debug("REST request to get Tenant : {}", tenantId);
        return authMapper.toTenantDTO(
                tenantRepository.findById(tenantId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND))
        );
    }

    public TenantDTO updateTenant(TenantDTO tenantDTO) {
        logger.debug("REST request to update Tenant : {}", tenantDTO);
        UUID correlationId = getCorrelationIdFromRequest(logger);
        Tenant oldTenant = tenantRepository.findById(tenantDTO.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        if(!Objects.equals(oldTenant.getStatus(), EnumTenantStatus.CANCELADO)){
            try{
                Tenant tenant = authMapper.toTenant(tenantDTO);
                tenant.setUpdateDate(Instant.now());
                tenant.setLastUpdatedBy(currentUser.email());
                Tenant saved = tenantRepository.save(tenant);

                auditService.logAuditEvent(Constants.TENANT_UPDATE, Constants.TENANT, null, Constants.SUCCESS, null,correlationId);
                return authMapper.toTenantDTO(saved);
            }catch (DataIntegrityViolationException error){
                logger.error("error:{}", String.valueOf(error));
                auditService.logAuditEvent(Constants.TENANT_UPDATE, Constants.TENANT, null, Constants.ERROR, null,correlationId);
                throw new BusinessException(ENTITY_NAME + " : Registro em duplicidade",BAD_REQUEST);
            }
        }else{
            auditService.logAuditEvent(Constants.TENANT_UPDATE, Constants.TENANT, null, Constants.ERROR, null,correlationId);
            throw new BusinessException(ENTITY_NAME + " : Não é possível atualizar um Tenant Cancelado",BAD_REQUEST);
        }
    }

    private TenantDTO sanitizeDto(TenantDTO dto) {
        return new TenantDTO(
            dto.id(),
            HtmlSanitizerUtil.sanitizePlainText(dto.name()),
            dto.cnpj(),
            dto.status(),
            HtmlSanitizerUtil.sanitizePlainText(dto.slug()),
            HtmlSanitizerUtil.sanitizePlainText(dto.nomeFantasia()),
            HtmlSanitizerUtil.sanitizePlainText(dto.inscricaoEstadual()),
            HtmlSanitizerUtil.sanitizePlainText(dto.email()),
            HtmlSanitizerUtil.sanitizePlainText(dto.telefone()),
            HtmlSanitizerUtil.sanitizePlainText(dto.logradouro()),
            HtmlSanitizerUtil.sanitizePlainText(dto.numero()),
            HtmlSanitizerUtil.sanitizePlainText(dto.complemento()),
            HtmlSanitizerUtil.sanitizePlainText(dto.bairro()),
            HtmlSanitizerUtil.sanitizePlainText(dto.cidade()),
            HtmlSanitizerUtil.sanitizePlainText(dto.uf()),
            HtmlSanitizerUtil.sanitizePlainText(dto.cep()),
            HtmlSanitizerUtil.sanitizePlainText(dto.ibgeCodigo()),
            dto.creationDate(),
            HtmlSanitizerUtil.sanitizePlainText(dto.createdBy()),
            dto.updateDate(),
            HtmlSanitizerUtil.sanitizePlainText(dto.lastUpdatedBy())
        );
    }

    /**
     * Ativa a assinatura de um tenant a partir do evento {@code billing.subscription.activated}.
     * Executado fora de contexto HTTP/segurança (consumer Kafka) — não usa SecurityUtils.
     * Idempotente: republicações do evento (renovações) não geram efeito nem auditoria duplicada
     * quando o tenant já está ATIVO com o mesmo plano.
     *
     * @param tenantId tenant a ativar
     * @param planType tipo de plano contratado (pode ser nulo)
     * @param asaasSubscriptionId id da assinatura no Asaas (pode ser nulo)
     */
    @org.springframework.transaction.annotation.Transactional
    public void activateSubscription(Long tenantId, String planType, String asaasSubscriptionId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND));

        boolean jaAtivoNoMesmoPlano = EnumTenantStatus.ATIVO.equals(tenant.getStatus())
                && Objects.equals(tenant.getPlanType(), planType)
                && Objects.equals(tenant.getAsaasSubscriptionId(), asaasSubscriptionId);
        if (jaAtivoNoMesmoPlano) {
            logger.info("Tenant {} já ATIVO no plano {} — evento de ativação ignorado (renovação)", tenantId, planType);
            return;
        }

        tenant.setStatus(EnumTenantStatus.ATIVO);
        tenant.setPlanType(planType);
        tenant.setAsaasSubscriptionId(asaasSubscriptionId);
        tenant.setUpdateDate(Instant.now());
        tenant.setLastUpdatedBy(Constants.SYSTEM);
        tenantRepository.save(tenant);

        auditService.logAuditEventWithActor(
                Constants.TENANT_SUBSCRIPTION_ACTIVATED, Constants.SYSTEM_ACTOR_ID, Constants.TENANT, null,
                Constants.SUCCESS,
                "{\"tenantId\":" + tenantId + ",\"planType\":\"" + (planType != null ? planType : "") + "\"}",
                UUID.randomUUID());

        logger.info("Tenant {} ativado via assinatura — planType={} asaasSubscriptionId={}",
                tenantId, planType, asaasSubscriptionId);
    }

    public void updateTenantStatusById(Long tenantId, String status) {
        logger.debug("REST request to update the status of the given tenant : {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();
        UUID correlationId = getCorrelationIdFromRequest(logger);

        if(Objects.equals(tenant.getStatus(), EnumTenantStatus.CANCELADO)){
            auditService.logAuditEvent(Constants.TENANT_CANCEL, Constants.TENANT, null, Constants.ERROR, null,correlationId);
            throw new BusinessException(ENTITY_NAME + " : Não é possível atualizar um Tenant Cancelado",BAD_REQUEST);
        }else{
            tenant.setStatus(EnumTenantStatus.valueOf(status));
            tenant.setUpdateDate(Instant.now());
            tenant.setLastUpdatedBy(currentUser.email());
            tenantRepository.save(tenant);

            auditService.logAuditEvent(Constants.TENANT_CANCEL, Constants.TENANT, null, Constants.SUCCESS, null,correlationId);
        }
    }
}
