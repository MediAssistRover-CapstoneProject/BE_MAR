package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.controller.media.MediaController;
import com.mediassistrover.backend.security.DeviceAuthenticationFilter;
import com.mediassistrover.backend.security.JwtAuthenticationFilter;
import com.mediassistrover.backend.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers=MediaController.class,excludeFilters=@ComponentScan.Filter(
        type=FilterType.ASSIGNABLE_TYPE,classes={DeviceAuthenticationFilter.class,JwtAuthenticationFilter.class}))
@Import(MediaControllerSecurityTest.MethodSecurity.class)
class MediaControllerSecurityTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity { }

    @MockitoBean MediaService service;
    @MockitoBean java.time.Clock clock;
    private final MockMvc mvc;

    @Autowired MediaControllerSecurityTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test void anonymousRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/media/url").param("objectKey",
                        "images/00000000-0000-0000-0000-000000000001.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test void staffCanResolveButCannotDelete() throws Exception {
        String key="images/00000000-0000-0000-0000-000000000001.png";
        mvc.perform(get("/api/v1/media/url").param("objectKey",key)
                        .with(user("staff").roles("STAFF")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/media").param("objectKey",key)
                        .with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanDeleteAndDeviceCannotUseMediaApi() throws Exception {
        String key="videos/00000000-0000-0000-0000-000000000001.mp4";
        mvc.perform(delete("/api/v1/media").param("objectKey",key)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/media/url").param("objectKey",key)
                        .with(user("robot").roles("DEVICE")))
                .andExpect(status().isForbidden());
    }
}
