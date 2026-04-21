package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ViaCepResponseDTO;
import com.l.erp.cadastroservice.services.CepService;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cep")
public class CepController {
    private final CepService cepService;

    public CepController(CepService cepService) {
        this.cepService = cepService;
    }

    @GetMapping("/{cep}")
    public ResponseEntity<ViaCepResponseDTO> buscarCep(@PathVariable String cep) {
        String validatedcep = HtmlSanitizerUtil.sanitize(cep);
        if (validatedcep.equals(cep)) {
            try {
                ViaCepResponseDTO response = cepService.buscarCep(cep);
                // ViaCEP retorna um JSON com "erro": true caso o CEP não exista
                if (response != null && response.getCep() != null) {
                    return ResponseEntity.ok(response);
                }
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } catch (Exception _) {
                return ResponseEntity.badRequest().build();
            }
        }else{
            throw new SecurityException("Blocked SSRF attempt");
        }
    }
}
