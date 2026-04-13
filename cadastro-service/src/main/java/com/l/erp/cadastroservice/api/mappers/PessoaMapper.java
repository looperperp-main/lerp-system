package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.dto.PessoaResponseDTO;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface PessoaMapper {
    PessoaResponseDTO toDto(Pessoa entity);

    Pessoa toEntity(PessoaResponseDTO entity);

    List<PessoaResponseDTO> toDtos(List<Pessoa> entity);

    List<Pessoa> toEntities(List<PessoaResponseDTO> entity);

    PessoaRequestDTO toDtoRequest(Pessoa entity);

    Pessoa toEntityRequest(PessoaRequestDTO entity);

}
