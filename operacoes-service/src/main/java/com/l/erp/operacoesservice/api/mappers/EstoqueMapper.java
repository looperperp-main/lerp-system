package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.EstoqueSaldoResponseDTO;
import com.l.erp.operacoesservice.api.dto.MovimentoEstoqueResponseDTO;
import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Cópia de campos entidade -> DTO (spec/estoque.md §5). Sem toEntity: os 3 endpoints só leem ou passam pelo EstoqueService. */
@Mapper(componentModel = "spring")
public interface EstoqueMapper {

    @Mapping(target = "atualizadoEm", source = "updatedAt")
    // estoqueMinimo/abaixoMinimo vêm de ProdutoEstoqueConfig (cadastro-service) só na Fase E6.
    @Mapping(target = "estoqueMinimo", ignore = true)
    @Mapping(target = "abaixoMinimo", ignore = true)
    EstoqueSaldoResponseDTO toSaldoResponseDto(EstoqueSaldo entity);

    MovimentoEstoqueResponseDTO toMovimentoResponseDto(MovimentoEstoque entity);
}
