package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaDTO;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface ProdutoCategoriaMapper {
    ProdutoCategoriaDTO toDto(ProdutoCategoria entity);

    ProdutoCategoria toEntity(ProdutoCategoriaDTO dto);
}
