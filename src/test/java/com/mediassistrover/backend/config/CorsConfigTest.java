package com.mediassistrover.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    private final CorsFilter filter = new CorsFilter(new CorsConfig(new CorsProperties(List.of("http://localhost:5173")))
            .corsConfigurationSource());

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/maps/map-id/force", "/api/v1/rooms/room-id/force"})
    void browserCanPreflightConfirmedDeletion(String path) throws Exception {
        var request = preflight(path, "http://localhost:5173");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
        var headers = Arrays.stream(response.getHeader("Access-Control-Allow-Headers").split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
        assertTrue(headers.containsAll(List.of("authorization", "content-type", "x-confirm-hard-delete")));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("DELETE"));
        assertNull(chain.getRequest(), "Preflight must never invoke a deletion handler");
    }

    @Test
    void unapprovedOriginRemainsBlocked() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(preflight("/api/v1/maps/map-id/force", "https://unapproved.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(chain.getRequest());
    }

    private MockHttpServletRequest preflight(String path, String origin) {
        var request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "DELETE");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type,x-confirm-hard-delete");
        return request;
    }
}
