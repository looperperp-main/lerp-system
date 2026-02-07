package com.l.erp.authservice.api.mappers;

import com.l.erp.authservice.api.dto.LoginResponse;
import com.l.erp.authservice.dominio.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    @Mapping(target = "username", source = "user.displayName")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "refreshToken", source = "refreshToken")
    LoginResponse toLoginResponse(UserAccount user, String token, String refreshToken);
}
