package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.security.AccountPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(KnowledgeDocumentAuthorizationTest.Config.class)
class KnowledgeDocumentAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean KnowledgeDocumentService service() { return mock(KnowledgeDocumentService.class); }
        @Bean KnowledgeDocumentController controller(KnowledgeDocumentService service) { return new KnowledgeDocumentController(service); }
    }
    @Autowired KnowledgeDocumentController controller;
    @Autowired KnowledgeDocumentService service;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); reset(service); }
    void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "user", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Test void onlyAdminCanUploadAndRead() {
        var file = new MockMultipartFile("file", "knowledge.txt", "text/plain", "Knowledge".getBytes());
        var actor = mock(AccountPrincipal.class);
        when(actor.accountId()).thenReturn(UUID.randomUUID());
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.list(0, 20));
        for (String role : List.of("STAFF", "DEVICE")) {
            as(role);
            assertThrows(AccessDeniedException.class, () -> controller.upload(file, actor));
            assertThrows(AccessDeniedException.class, () -> controller.get(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.list(0, 20));
            assertThrows(AccessDeniedException.class, () -> controller.chunks(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.extractedText(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.delete(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.deleteChunk(UUID.randomUUID(),UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.retry(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> controller.replace(UUID.randomUUID(),file,actor));
            assertThrows(AccessDeniedException.class, () -> controller.update(UUID.randomUUID(),file,actor));
            assertThrows(AccessDeniedException.class, () -> controller.updateChunk(UUID.randomUUID(),UUID.randomUUID(),new KnowledgeDocumentController.ChunkUpdate("new"),actor));
        }
        as("ADMIN");
        controller.upload(file, actor);
        controller.list(0, 20);
        verify(service).upload(file, actor.accountId());
        verify(service).list(0, 20);
    }
}
