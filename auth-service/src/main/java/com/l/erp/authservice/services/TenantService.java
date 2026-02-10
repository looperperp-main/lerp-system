package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BussinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class TenantService {

    private final Logger logger = LoggerFactory.getLogger(TenantService.class);
    private static final String ENTITY_NAME = "Tenant";

    private final TenantRepository tenantRepository;

    private final AuthMapper authMapper;

    public TenantService(TenantRepository tenantRepository,
                         AuthMapper authMapper) {
        this.tenantRepository = tenantRepository;
        this.authMapper = authMapper;
    }

    /**
     * Cria um novo tenant
     * @param tenantDTO tenant a ser criado
     * @return dado criado
     */
    public TenantDTO createTenant(TenantDTO tenantDTO){
        logger.debug("Creating Tenant : {}", tenantDTO);
        String loggedUserEmail = SecurityUtils.getCurrentUserEmail()
                .orElseThrow(() -> new RuntimeException("Usuário não autenticado"));
        if (!Objects.equals(tenantDTO.status(), EnumTenantStatus.A.getDescription())
                || !Objects.equals(tenantDTO.status(), EnumTenantStatus.S.getDescription())
                || !Objects.equals(tenantDTO.status(), EnumTenantStatus.C.getDescription())
        ){
            Tenant tenant = authMapper.toTenant(tenantDTO);
            tenant.setStatus(EnumTenantStatus.A.getDescription());
            tenant.setCreationDate(Instant.now());
            tenant.setCreatedBy(loggedUserEmail);
            tenant = tenantRepository.save(tenant);
            return authMapper.toTenantDTO(tenant);
        }else{
            throw new ResponseStatusException(BAD_REQUEST,ENTITY_NAME + " : Registro com o Status inválido");
        }
    }

    /**
     * Retorna todos os tenants cadastrados
     * TODO: Checkar a Paginação
     * @return lista de tenants
     */
    public List<TenantDTO> getAllTenants(){
        logger.debug("REST request to get all Tenants");
        List<Tenant> tenants = tenantRepository.findAll();
        return authMapper.toTenantDTOs(tenants);
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
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : Tenant não encontrado"))
        );
    }

    public TenantDTO updateTenant(TenantDTO tenantDTO) {
        logger.debug("REST request to update Tenant : {}", tenantDTO);
        Tenant oldTenant = tenantRepository.findById(tenantDTO.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : Tenant não encontrado"));

        String loggedUserEmail = SecurityUtils.getCurrentUserEmail()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuário não autenticado"));

        if(Objects.equals(oldTenant.getStatus(), EnumTenantStatus.C.getDescription())){
            Long duplicates = tenantRepository.countAllByNameAndCnpj(tenantDTO.name(),tenantDTO.cnpj());
            if (duplicates == 0) {

                Tenant tenant = authMapper.toTenant(tenantDTO);
                tenant.setUpdateDate(Instant.now());
                tenant.setLastUpdatedBy(loggedUserEmail);
                tenantRepository.save(tenant);
                return authMapper.toTenantDTO(tenant);
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
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ENTITY_NAME + " : Tenant não encontrado"));

        String loggedUserEmail = SecurityUtils.getCurrentUserEmail()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Usuário não autenticado"));

        if(Objects.equals(tenant.getStatus(), EnumTenantStatus.C.getDescription())){
            throw new BussinessException(ENTITY_NAME + " : Não é possível atualizar um Tenant Cancelado",BAD_REQUEST);
        }else{
            tenant.setStatus(status);
            tenant.setUpdateDate(Instant.now());
            tenant.setLastUpdatedBy(loggedUserEmail);
            tenantRepository.save(tenant);
            return Optional.empty();
        }
    }
}
