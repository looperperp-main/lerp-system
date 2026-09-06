package com.l.erp.cadastroservice.services;

import com.l.erp.cadastroservice.api.dto.PrecoResolvidoDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoPreco;
import com.l.erp.cadastroservice.domain.enumerators.OrigemPreco;
import com.l.erp.cadastroservice.repository.ClienteRepository;
import com.l.erp.cadastroservice.repository.ProdutoPrecoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoGrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PrecoResolverService {

    private final ProdutoService produtoService;
    private final ClienteRepository clienteRepository;
    private final TabelaPrecoGrupoClienteRepository tabelaPrecoGrupoClienteRepository;
    private final TabelaPrecoRepository tabelaPrecoRepository;
    private final ProdutoPrecoRepository produtoPrecoRepository;

    public PrecoResolverService(ProdutoService produtoService,
                                 ClienteRepository clienteRepository,
                                 TabelaPrecoGrupoClienteRepository tabelaPrecoGrupoClienteRepository,
                                 TabelaPrecoRepository tabelaPrecoRepository,
                                 ProdutoPrecoRepository produtoPrecoRepository) {
        this.produtoService = produtoService;
        this.clienteRepository = clienteRepository;
        this.tabelaPrecoGrupoClienteRepository = tabelaPrecoGrupoClienteRepository;
        this.tabelaPrecoRepository = tabelaPrecoRepository;
        this.produtoPrecoRepository = produtoPrecoRepository;
    }

    @Transactional(readOnly = true)
    public PrecoResolvidoDTO resolver(UUID produtoId, UUID clienteId, LocalDate data, Long tenantId) {
        Produto produto = produtoService.findById(produtoId, tenantId);
        LocalDate dataResolucao = data != null ? data : LocalDate.now();

        Cliente cliente = clienteId != null
                ? clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                        .orElseThrow(() -> new BusinessException(Constants.CLIENTE_NOT_FOUND, HttpStatus.BAD_REQUEST))
                : null;

        // Nível CLIENTE
        if (cliente != null && cliente.getTabelaPreco() != null) {
            Optional<ProdutoPreco> preco = buscarPrecoVigente(tenantId, produto.getId(), List.of(cliente.getTabelaPreco().getId()), dataResolucao);
            if (preco.isPresent()) {
                return toDto(produto.getId(), clienteId, preco.get(), OrigemPreco.CLIENTE, dataResolucao);
            }
        }

        // Nível GRUPO
        if (cliente != null && cliente.getGrupoCliente() != null) {
            List<UUID> tabelaIds = tabelaPrecoGrupoClienteRepository
                    .findAllByGrupoClienteIdAndTenantId(cliente.getGrupoCliente().getId(), tenantId)
                    .stream()
                    .map(vinculo -> vinculo.getTabelaPreco().getId())
                    .toList();
            if (!tabelaIds.isEmpty()) {
                Optional<ProdutoPreco> preco = buscarPrecoVigente(tenantId, produto.getId(), tabelaIds, dataResolucao);
                if (preco.isPresent()) {
                    return toDto(produto.getId(), clienteId, preco.get(), OrigemPreco.GRUPO, dataResolucao);
                }
            }
        }

        // Nível PADRAO
        Optional<UUID> tabelaPadraoId = tabelaPrecoRepository.findByPadraoIsTrueAndTenantId(tenantId).map(tabela -> tabela.getId());
        if (tabelaPadraoId.isPresent()) {
            Optional<ProdutoPreco> preco = buscarPrecoVigente(tenantId, produto.getId(), List.of(tabelaPadraoId.get()), dataResolucao);
            if (preco.isPresent()) {
                return toDto(produto.getId(), clienteId, preco.get(), OrigemPreco.PADRAO, dataResolucao);
            }
        }

        throw new BusinessException(Constants.PRECO_NAO_RESOLVIDO, HttpStatus.NOT_FOUND);
    }

    private Optional<ProdutoPreco> buscarPrecoVigente(Long tenantId, UUID produtoId, List<UUID> tabelaPrecoIds, LocalDate data) {
        List<ProdutoPreco> encontrados = produtoPrecoRepository.findVigentesEmTabelas(tenantId, produtoId, tabelaPrecoIds, data, PageRequest.of(0, 1));
        return encontrados.isEmpty() ? Optional.empty() : Optional.of(encontrados.get(0));
    }

    private PrecoResolvidoDTO toDto(UUID produtoId, UUID clienteId, ProdutoPreco produtoPreco, OrigemPreco origem, LocalDate data) {
        return new PrecoResolvidoDTO(produtoId, clienteId, produtoPreco.getTabelaPreco().getId(), origem, produtoPreco.getPreco(), data);
    }
}
