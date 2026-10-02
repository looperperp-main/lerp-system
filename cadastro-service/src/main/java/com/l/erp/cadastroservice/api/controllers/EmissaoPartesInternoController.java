package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.EmissaoPartesRefDTO;
import com.l.erp.cadastroservice.domain.Endereco;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.IndicadorIeDestinatario;
import com.l.erp.cadastroservice.domain.enumerators.TipoEndereco;
import com.l.erp.cadastroservice.repository.EnderecoRepository;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
import com.l.erp.cadastroservice.services.PessoaService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentTenantId;

/**
 * Endpoint interno pro operacoes-service montar o payload de {@code POST /emissao/documentos} no
 * faturamento: emitente (estabelecimento próprio) e destinatário (pessoa do cliente) já no formato do
 * contrato da emissão. Leitura pura, protegida pelo {@code InternalRequestFilter} como o resto do serviço.
 */
@RestController
@RequestMapping("/api/v1/interno/emissao")
public class EmissaoPartesInternoController {

    private static final String SEM_NUMERO = "S/N";

    private final EstabelecimentoService estabelecimentoService;
    private final PessoaService pessoaService;
    private final EnderecoRepository enderecoRepository;

    public EmissaoPartesInternoController(EstabelecimentoService estabelecimentoService, PessoaService pessoaService,
                                          EnderecoRepository enderecoRepository) {
        this.estabelecimentoService = estabelecimentoService;
        this.pessoaService = pessoaService;
        this.enderecoRepository = enderecoRepository;
    }

    @GetMapping("/emitente")
    public EmissaoPartesRefDTO.Emitente emitente() {
        Long tenantId = tenantId();
        Estabelecimento estabelecimento = estabelecimentoService.buscarProprio(tenantId);
        Pessoa pessoa = estabelecimento.getPessoa();
        return new EmissaoPartesRefDTO.Emitente(
                estabelecimento.getId(),
                estabelecimento.getCnpjCompleto(),
                pessoa.getNomeRazao(),
                pessoa.getApelidoFantasia(),
                estabelecimento.getIe(),
                estabelecimento.getCrt() != null ? String.valueOf(estabelecimento.getCrt().getCodigo()) : null,
                endereco(pessoa.getId(), tenantId));
    }

    @GetMapping("/destinatario/{pessoaId}")
    public EmissaoPartesRefDTO.Destinatario destinatario(@PathVariable UUID pessoaId) {
        Long tenantId = tenantId();
        Pessoa pessoa = pessoaService.findByIdAndTenant(pessoaId, tenantId);
        IndicadorIeDestinatario indicador = pessoa.getIndIeDest() != null
                ? pessoa.getIndIeDest() : IndicadorIeDestinatario.NAO_CONTRIBUINTE;
        return new EmissaoPartesRefDTO.Destinatario(
                pessoa.getDocumento(),
                pessoa.getNomeRazao(),
                String.valueOf(indicador.getCodigo()),
                pessoa.getIe(),
                endereco(pessoa.getId(), tenantId));
    }

    private EmissaoPartesRefDTO.Endereco endereco(UUID pessoaId, Long tenantId) {
        // Mesma prioridade do CadastroServiceClient.buscarEnderecoFiscal: FISCAL, depois principal, depois o primeiro.
        Endereco e = enderecoRepository.findAllByPessoaIdAndTenantId(pessoaId, tenantId).stream()
                .min(Comparator.comparingInt((Endereco x) -> x.getTipo() == TipoEndereco.FISCAL ? 0 : 1)
                        .thenComparingInt(x -> Boolean.TRUE.equals(x.getPrincipal()) ? 0 : 1))
                .orElseThrow(() -> new BusinessException(Constants.EMISSAO_PESSOA_SEM_ENDERECO, HttpStatus.UNPROCESSABLE_ENTITY));
        return new EmissaoPartesRefDTO.Endereco(e.getLogradouro(),
                e.getNumero() != null && !e.getNumero().isBlank() ? e.getNumero() : SEM_NUMERO,
                e.getComplemento(), e.getBairro(), e.getIbgeCodigo(), e.getCidade(), e.getUf(), e.getCep());
    }

    private static Long tenantId() {
        return getCurrentTenantId()
                .orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
    }
}
