package com.l.erp.authservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Desativado até configurarmos um banco de dados em memória para os testes no CI")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
