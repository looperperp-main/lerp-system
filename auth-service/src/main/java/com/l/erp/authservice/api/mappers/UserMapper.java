package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.UserAccountDTO;
import com.l.erp.authservice.dominio.UserAccount;
import com.l.erp.authservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface UserMapper {

    @Mapping(target = "tenantId", source = "tenant.id")
    UserAccountDTO toUserAccountDTO(UserAccount user);

    @Mapping(target = "tenant.id", source = "tenantId")
    UserAccount toUserAccount(UserAccountDTO userDTO);

    List<UserAccountDTO> toUserAccountDTOs(List<UserAccount> users);
}
