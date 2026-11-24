package com.mediassistrover.backend.knowledge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(KnowledgeSearchAuthorizationTest.Config.class)
class KnowledgeSearchAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean KnowledgeSearchService service() { return mock(KnowledgeSearchService.class); }
        @Bean KnowledgeSearchController controller(KnowledgeSearchService service) {
            return new KnowledgeSearchController(service);
        }
    }
    @Autowired KnowledgeSearchController controller;

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void onlyAdminMaySearch() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.documents("giờ khám",0,20));
        for (String role:List.of("STAFF","DEVICE")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            assertThrows(AccessDeniedException.class,()->controller.documents("giờ khám",0,20));
            assertThrows(AccessDeniedException.class,()->controller.chunks("giờ khám",null,0,20));
        }
    }
}
