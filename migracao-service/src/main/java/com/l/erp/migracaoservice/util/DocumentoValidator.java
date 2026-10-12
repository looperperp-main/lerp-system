package com.l.erp.migracaoservice.util;

/**
 * Dígito verificador de CPF e CNPJ. O CNPJ aceita o formato alfanumérico (NT 2026.004): cada
 * caractere vale {@code código ASCII - 48} (0-9 → 0-9, A-Z → 17-42) e os dois DVs seguem numéricos.
 */
public final class DocumentoValidator {

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentoValidator() {}

    /** Tira máscara e espaços e põe em maiúsculas: "12.345.678/0001-95" → "12345678000195". */
    public static String normalizar(String documento) {
        return documento == null ? "" : documento.replaceAll("[.\\-/\\s]", "").toUpperCase();
    }

    /**
     * Aplica a máscara que o cadastro grava em pessoa.documento (CPF 999.999.999-99, CNPJ 99.999.999/9999-99,
     * alfanumérico inclusive). Sem ela a dedup do cadastro, que compara o texto, não reconheceria o documento.
     */
    public static String formatar(String normalizado) {
        if (normalizado.length() == 11) {
            return normalizado.substring(0, 3) + "." + normalizado.substring(3, 6) + "." + normalizado.substring(6, 9) + "-" + normalizado.substring(9);
        }
        if (normalizado.length() == 14) {
            return normalizado.substring(0, 2) + "." + normalizado.substring(2, 5) + "." + normalizado.substring(5, 8)
                    + "/" + normalizado.substring(8, 12) + "-" + normalizado.substring(12);
        }
        return normalizado;
    }

    public static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || todosIguais(cpf)) return false;
        int dv1 = digitoCpf(cpf, 9);
        int dv2 = digitoCpf(cpf, 10);
        return dv1 == cpf.charAt(9) - '0' && dv2 == cpf.charAt(10) - '0';
    }

    public static boolean cnpjValido(String cnpj) {
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}\\d{2}") || todosIguais(cnpj)) return false;
        int dv1 = digitoCnpj(cnpj, PESOS_CNPJ_1);
        int dv2 = digitoCnpj(cnpj, PESOS_CNPJ_2);
        return dv1 == cnpj.charAt(12) - '0' && dv2 == cnpj.charAt(13) - '0';
    }

    private static int digitoCpf(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int digitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (cnpj.charAt(i) - '0') * pesos[i];
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String s) {
        return s.chars().distinct().count() == 1;
    }
}
