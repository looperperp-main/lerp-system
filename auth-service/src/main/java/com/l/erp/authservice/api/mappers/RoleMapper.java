package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.RoleDTO;
import com.l.erp.authservice.dominio.Role;
import com.l.erp.common.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface RoleMapper {

    @Mapping(target = "tenantId", source = "tenant.id")
    RoleDTO toRoleDTO(Role role);

    Role toRole(RoleDTO roleDTO);

    List<RoleDTO> toRoleDTOs(List<Role> roles);
}
