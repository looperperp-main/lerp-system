package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.Constants;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BussinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
        tenant.setStatus(EnumTenantStatus.P.getDescription());
        tenant.setCreationDate(Instant.now());
        tenant.setCreatedBy(currentUser.email());
        Tenant tenantSaved = tenantRepository.save(tenant);
        auditService.logAuditEvent(Constants.TENANT_CREATION, currentUser.id(), Constants.TENANT, null, "SUCCESS", null,null);
        return authMapper.toTenantDTO(tenantSaved);

    }

    /**
     * Retorna todos os tenants cadastrados
     * TODO: Checkar a Paginação
     * @return lista de tenants
     */
    public Page<TenantDTO> getAllTenants(Pageable pageable){
        logger.debug("REST request to get all Tenants");
        Page<Tenant> tenants = tenantRepository.findAll(pageable);
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
        Tenant oldTenant = tenantRepository.findById(tenantDTO.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        if(!Objects.equals(oldTenant.getStatus(), EnumTenantStatus.C.getDescription())){
            Long duplicates = tenantRepository.countAllByNameAndCnpj(tenantDTO.name(),tenantDTO.cnpj());
            if (duplicates != 0) {

                Tenant tenant = authMapper.toTenant(tenantDTO);
                tenant.setUpdateDate(Instant.now());
                tenant.setLastUpdatedBy(currentUser.email());
                Tenant saved = tenantRepository.save(tenant);
                auditService.logAuditEvent(Constants.TENANT_UPDATE, currentUser.id(), Constants.TENANT, null, "SUCCESS", null,null);
                return authMapper.toTenantDTO(saved);
            }else{
                throw new BussinessException(ENTITY_NAME + " : Registro em duplicidade",BAD_REQUEST);
            }
        }else{
            throw new BussinessException(ENTITY_NAME + " : Não é possível atualizar um Tenant Cancelado",BAD_REQUEST);
        }
    }

    public Optional<Void> updateTenantStatusById(Long tenantId, String status) {
        logger.debug("REST request to update the status of the given tenant : {}", tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : " + Constants.TENANT_NOT_FOUND));

        CurrentUser currentUser = SecurityUtils.getCurrentUserInfo();

        if(Objects.equals(tenant.getStatus(), EnumTenantStatus.C.getDescription())){
            throw new BussinessException(ENTITY_NAME + " : Não é possível atualizar um Tenant Cancelado",BAD_REQUEST);
        }else{
            tenant.setStatus(status);
            tenant.setUpdateDate(Instant.now());
            tenant.setLastUpdatedBy(currentUser.email());
            tenantRepository.save(tenant);
            auditService.logAuditEvent(Constants.TENANT_UPDATE, currentUser.id(), Constants.TENANT, null, "SUCCESS", null,null);
            return Optional.empty();
        }
    }
}
