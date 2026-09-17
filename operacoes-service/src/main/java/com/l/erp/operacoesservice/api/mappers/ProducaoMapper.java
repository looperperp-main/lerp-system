package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.FichaTecnicaItemDTO;
import com.l.erp.operacoesservice.api.dto.FichaTecnicaResponseDTO;
import com.l.erp.operacoesservice.api.dto.OrdemProducaoResponseDTO;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import com.l.erp.operacoesservice.domain.estoque.FichaTecnicaItem;
import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Cópia de campos entidade -> DTO (spec/modulos/estoque/estoque.md §12, D11, Fase 2). */
@Mapper(componentModel = "spring")
public interface ProducaoMapper {

    @Mapping(target = "itens", ignore = true) // preenchido à parte pelo controller (ProducaoService.buscarItens)
    FichaTecnicaResponseDTO toFichaTecnicaResponseDto(FichaTecnica entity);

    FichaTecnicaItemDTO toItemDto(FichaTecnicaItem entity);

    @Mapping(target = "criadaEm", source = "createdAt")
    OrdemProducaoResponseDTO toOrdemResponseDto(OrdemProducao entity);
}
