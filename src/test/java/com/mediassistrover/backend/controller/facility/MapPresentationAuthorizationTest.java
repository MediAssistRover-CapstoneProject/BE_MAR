package com.mediassistrover.backend.controller.facility;

import com.mediassistrover.backend.dto.facility.MapPresentationContracts.Change;
import com.mediassistrover.backend.service.MapPresentationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MapPresentationAuthorizationTest.Config.class)
class MapPresentationAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean MapPresentationService service() { return mock(MapPresentationService.class); }
        @Bean MapPresentationController controller(MapPresentationService service) { return new MapPresentationController(service); }
    }
    @Autowired MapPresentationController controller;
    @Autowired MapPresentationService service;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); reset(service); }
    void as(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user","",List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
    @Test void staffCanReadPublishedButCannotReadDraftOrMutate() {
        as("STAFF"); UUID id=UUID.randomUUID();
        controller.published(id); verify(service).published(id);
        assertThrows(AccessDeniedException.class,()->controller.editor(id));
        assertThrows(AccessDeniedException.class,()->controller.publish(id,new Change(1)));
        assertThrows(AccessDeniedException.class,()->controller.unpublish(id,new Change(1)));
        assertThrows(AccessDeniedException.class,()->controller.save(id,null));
    }
    @Test void adminCanEdit() { as("ADMIN"); UUID id=UUID.randomUUID(); controller.editor(id); verify(service).editor(id); }
}
