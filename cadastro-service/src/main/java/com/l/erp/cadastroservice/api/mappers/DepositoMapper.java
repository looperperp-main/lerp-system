package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.DepositoDTO;
import com.l.erp.cadastroservice.domain.Deposito;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface DepositoMapper {

    Deposito toEntity(DepositoDTO dto);

    DepositoDTO toDto(Deposito entity);
}
