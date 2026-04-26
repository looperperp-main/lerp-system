package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.TipoContato;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Relation(collectionRelation = "contatos", itemRelation = "contato")
public class ContatoResponseDTO extends RepresentationModel<ContatoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private String nome;
    private TipoContato tipo;
    private String cargo;
    private String email;
    private String telefone;
    private Boolean principal;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ContatoResponseDTO that = (ContatoResponseDTO) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getTenantId(), that.getTenantId())
                && Objects.equals(getNome(), that.getNome()) && Objects.equals(getTipo(), that.getTipo())
                && Objects.equals(getCargo(), that.getCargo()) && Objects.equals(getEmail(), that.getEmail())
                && Objects.equals(getTelefone(), that.getTelefone()) && Objects.equals(getPrincipal(), that.getPrincipal())
                && Objects.equals(getAtivo(), that.getAtivo()) && Objects.equals(getCreatedAt(), that.getCreatedAt())
                && Objects.equals(getUpdatedAt(), that.getUpdatedAt()) && Objects.equals(getCreatedBy(), that.getCreatedBy())
                && Objects.equals(getLastUpdatedBy(), that.getLastUpdatedBy());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getId(), getTenantId(), getNome(), getTipo(), getCargo(),
                getEmail(), getTelefone(), getPrincipal(), getAtivo(), getCreatedAt(), getUpdatedAt(),
                getCreatedBy(), getLastUpdatedBy());
    }
}
