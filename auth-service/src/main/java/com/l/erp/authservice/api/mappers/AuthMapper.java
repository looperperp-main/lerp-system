package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.dto.TenantLoginResponse;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface AuthMapper {
    @Mapping(target = "username", source = "user.displayName")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "refreshToken", source = "refreshToken")
    LoginResponse toLoginResponse(UserAccount user, String token, String refreshToken);

    @Mapping(target = "username", source = "user.displayName")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.name")
    @Mapping(target = "tenantCnpj", source = "tenant.cnpj")
    TenantLoginResponse toTenantLoginResponse(UserAccount user, String token, String refreshToken, Tenant tenant);

    TenantDTO toTenantDTO(Tenant tenant);

    @Mapping(target = "name", expression = "java(HtmlSanitizerUtil.sanitize(tenantDTO.name()))")
    @Mapping(target = "cnpj", expression = "java(HtmlSanitizerUtil.sanitize(tenantDTO.cnpj()))")
    Tenant toTenant(TenantDTO tenantDTO);

    List<TenantDTO> toTenantDTOs(List<Tenant> tenants);
}
