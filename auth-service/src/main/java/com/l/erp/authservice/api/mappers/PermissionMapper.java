package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.PermissionDTO;
import com.l.erp.authservice.dominio.Permission;
import com.l.erp.common.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface PermissionMapper {

    PermissionDTO toPermissionDTO(Permission permission);

    Permission toPermission(PermissionDTO permissionDTO);

    List<PermissionDTO> toPermissionDTOs(List<Permission> permissions);
}
