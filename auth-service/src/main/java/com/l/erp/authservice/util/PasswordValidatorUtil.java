package com.l.erp.authservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import org.jspecify.annotations.NonNull;
import org.passay.CharacterCharacteristicsRule;
import org.passay.CharacterRule;
import org.passay.DictionarySubstringRule;
import org.passay.EnglishCharacterData;
import org.passay.EnglishSequenceData;
import org.passay.IllegalSequenceRule;
import org.passay.LengthRule;
import org.passay.MessageResolver;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.PropertiesMessageResolver;
import org.passay.Rule;
import org.passay.RuleResult;
import org.passay.dictionary.ArrayWordList;
import org.passay.dictionary.WordListDictionary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@Component
public class PasswordValidatorUtil {
    public void validatePassword(String password, String tenantName) {
        List<Rule> rules = new ArrayList<>();

        // 1. Comprimento Mínimo de 14 caracteres
        rules.add(new LengthRule(14, 128));

        // 2. Complexidade: 4 de 4 grupos (Maiúsculas, Minúsculas, Números, Símbolos)
        CharacterCharacteristicsRule complexRule = new CharacterCharacteristicsRule(4,
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                new CharacterRule(EnglishCharacterData.Digit, 1),
                new CharacterRule(EnglishCharacterData.Special, 1)
        );
        rules.add(complexRule);

        // 3. Blacklist de palavras proibidas comuns e contextuais
        List<String> blacklist = new ArrayList<>(Arrays.asList(
                "123456", "senha123", "loop123", "erp2025"
        ));

        // Adiciona o nome da empresa como regra proibida (se possuir valor)
        if (tenantName != null && !tenantName.isBlank()) {
            blacklist.add(tenantName.replaceAll("\\s+", "") + "123");
        }

        Collections.sort(blacklist);

        // DictionaryRule do Passay com a nossa blacklist customizada
        WordListDictionary wordListDictionary = new WordListDictionary(new ArrayWordList(blacklist.toArray(new String[0])));
        DictionarySubstringRule dictRule = new DictionarySubstringRule(wordListDictionary);
        dictRule.setMatchBackwards(true); // Também bloqueia se o usuário digitar invertido
        rules.add(dictRule);

        // 4. Bloqueia sequências obvias (ex: abcdef, qwerty, 12345)
        rules.add(new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 5, false));
        rules.add(new IllegalSequenceRule(EnglishSequenceData.Numerical, 5, false));

        PasswordValidator validator = getPasswordValidator(rules);
        PasswordData passwordData = new PasswordData(password);
        RuleResult result = validator.validate(passwordData);

        if (!result.isValid()) {
            // Lança erro caso a senha não cumpra a política
            List<String> messages = validator.getMessages(result);
            throw new BusinessException("Senha fraca: " + String.join(", ", messages), HttpStatus.BAD_REQUEST);
        }
    }

    private static @NonNull PasswordValidator getPasswordValidator(List<Rule> rules) {
        Properties props = new Properties();

        // Mensagens da regra de tamanho
        props.put("TOO_SHORT", "A senha deve ter pelo menos %1$s caracteres.");
        props.put("TOO_LONG", "A senha não pode ter mais que %2$s caracteres.");

        // Mensagens das regras de caracteres
        // No Passay, o código de erro para um CharacterRule obedece ao nome do enum de dados (INSUFFICIENT_UPPERCASE, etc)
        props.put("INSUFFICIENT_UPPERCASE", "A senha deve conter pelo menos %1$s letra(s) maiúscula(s).");
        props.put("INSUFFICIENT_LOWERCASE", "A senha deve conter pelo menos %1$s letra(s) minúscula(s).");
        props.put("INSUFFICIENT_DIGIT", "A senha deve conter pelo menos %1$s número(s).");
        props.put("INSUFFICIENT_SPECIAL", "A senha deve conter pelo menos %1$s caractere(s) especial(is).");

        // Mensagem geral da regra de características
        props.put("INSUFFICIENT_CHARACTERISTICS", "A senha atende a %1$s das %2$s regras exigidas (necessita de maiúsculas, minúsculas, números e símbolos).");

        // Mensagem da blacklist (dicionário)
        props.put("ILLEGAL_WORD", "A senha contém uma palavra proibida ou muito comum ('%1$s').");
        props.put("ILLEGAL_WORD_REVERSED", "A senha contém uma palavra proibida de trás para frente ('%1$s').");

        // Mensagem da regra de sequência (12345, abcde)
        props.put("ILLEGAL_ALPHABETICAL_SEQUENCE", "A senha contém uma sequência alfabética proibida ('%1$s').");
        props.put("ILLEGAL_NUMERICAL_SEQUENCE", "A senha contém uma sequência numérica proibida ('%1$s').");

        MessageResolver resolver = new PropertiesMessageResolver(props);

        return new PasswordValidator(resolver, rules);
    }
}
