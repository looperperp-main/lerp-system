package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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
        if (!Objects.equals(tenantDTO.status(), EnumTenantStatus.A.getDescription())
                || !Objects.equals(tenantDTO.status(), EnumTenantStatus.S.getDescription())
                || !Objects.equals(tenantDTO.status(), EnumTenantStatus.C.getDescription())
        ){
            Tenant tenant = authMapper.toTenant(tenantDTO);
            tenant.setCreationDate(Instant.now());
            tenant = tenantRepository.save(tenant);
            return authMapper.toTenantDTO(tenant);
        }else{
            throw new ResponseStatusException(BAD_REQUEST,ENTITY_NAME + " : Registro com o Status inválido");
        }
    }

    /**
     * Retorna todos os tenants cadastrados
     * @return lista de tenants
     */
    public List<TenantDTO> getAllTenants(){
        logger.debug("REST request to get all Tenants");
        List<Tenant> tenants = tenantRepository.findAll();
        return authMapper.toTenantDTOs(tenants);
    }
}
