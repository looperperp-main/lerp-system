package com.l.erp.emissaofiscalservice.services.certificado;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.services.crypto.EnvelopeEncryptionService;
import com.l.erp.emissaofiscalservice.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upload de certificado A1 (.pfx) por emitente — spec/modulos/emissao-fiscal/emissao-fiscal.md
 * §3 item 1. Duas validações síncronas antes de aceitar o certificado, na mesma linha do que a spec
 * pede antes de assinar/transmitir (aqui, antes mesmo de guardar):
 * <ul>
 *   <li>o arquivo abre como PKCS12 com a senha informada (senão a senha está errada);</li>
 *   <li>o CNPJ do subject do X.509 bate com o CNPJ do emitente informado por quem chama — este
 *   serviço nunca busca o emitente em cadastro-service (spec §2), quem chama já resolveu o CNPJ e
 *   manda no payload (identificador neutro, sem acoplar a "Estabelecimento" do erp-vsd — §2).</li>
 * </ul>
 */
@Service
public class CertificadoDigitalService {

    private static final Pattern CNPJ_NO_SUBJECT = Pattern.compile("(\\d{14})");

    private final CertificadoDigitalRepository repository;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    public CertificadoDigitalService(CertificadoDigitalRepository repository,
                                      EnvelopeEncryptionService envelopeEncryptionService) {
        this.repository = repository;
        this.envelopeEncryptionService = envelopeEncryptionService;
    }

    @Transactional
    public CertificadoDigital upload(UUID emitenteId, String cnpjEmitente, MultipartFile arquivoPfx, String senha) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));

        X509Certificate certificado = abrirEValidarPfx(arquivoPfx, senha);
        String cnpjDoCertificado = extrairCnpjDoSubject(certificado);
        String cnpjInformadoNormalizado = somenteDigitos(cnpjEmitente);

        if (!cnpjDoCertificado.equals(cnpjInformadoNormalizado)) {
            throw new BusinessException(
                    "CNPJ do certificado (" + cnpjDoCertificado + ") não confere com o CNPJ do emitente informado.",
                    HttpStatus.BAD_REQUEST);
        }

        CertificadoDigital entidade = repository.findByTenantIdAndEmitenteId(tenantId, emitenteId)
                .orElseGet(CertificadoDigital::new);
        entidade.setTenantId(tenantId);
        entidade.setEmitenteId(emitenteId);
        entidade.setCnpjSubject(cnpjDoCertificado);
        entidade.setKekVersion(EnvelopeEncryptionService.KEK_VERSION_ATUAL);
        entidade.setCertificadoCifrado(envelopeEncryptionService.cifrar(lerBytes(arquivoPfx)));
        entidade.setSenhaCifrada(envelopeEncryptionService.cifrar(senha.getBytes()));
        entidade.setCertificadoValidoAte(certificado.getNotAfter().toInstant().atOffset(ZoneOffset.UTC));
        entidade.setAlertaEnviado(false);
        entidade.setAtivo(true);
        OffsetDateTime agora = OffsetDateTime.now();
        entidade.setUpdatedAt(agora);
        if (entidade.getCreatedAt() == null) {
            entidade.setCreatedAt(agora);
        }

        return repository.save(entidade);
    }

    /** Decifra o .pfx e devolve o {@link KeyStore} pronto pra assinatura (usado pela Etapa 2+). */
    public KeyStore abrirKeyStore(CertificadoDigital certificadoDigital, char[] senha) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            byte[] pfxDecifrado = envelopeEncryptionService.decifrar(certificadoDigital.getCertificadoCifrado());
            keyStore.load(new ByteArrayInputStream(pfxDecifrado), senha);
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Falha ao abrir o certificado digital decifrado.", e);
        }
    }

    private X509Certificate abrirEValidarPfx(MultipartFile arquivoPfx, String senha) {
        try (InputStream is = arquivoPfx.getInputStream()) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(is, senha.toCharArray());

            Enumeration<String> aliases = keyStore.aliases();
            if (!aliases.hasMoreElements()) {
                throw new BusinessException("Certificado A1 sem nenhum alias — arquivo inválido.", HttpStatus.BAD_REQUEST);
            }
            String alias = aliases.nextElement();
            X509Certificate certificado = (X509Certificate) keyStore.getCertificate(alias);
            if (certificado == null) {
                throw new BusinessException("Não foi possível ler o certificado X.509 do arquivo enviado.", HttpStatus.BAD_REQUEST);
            }
            return certificado;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    "Não foi possível abrir o certificado A1 — verifique o arquivo .pfx e a senha.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String extrairCnpjDoSubject(X509Certificate certificado) {
        X500Principal subject = certificado.getSubjectX500Principal();
        Matcher matcher = CNPJ_NO_SUBJECT.matcher(subject.getName());
        if (!matcher.find()) {
            throw new BusinessException(
                    "Não foi possível extrair o CNPJ do subject do certificado (padrão e-CNPJ ICP-Brasil).",
                    HttpStatus.BAD_REQUEST);
        }
        return matcher.group(1);
    }

    private String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Falha ao ler o arquivo do certificado.", HttpStatus.BAD_REQUEST);
        }
    }
}
