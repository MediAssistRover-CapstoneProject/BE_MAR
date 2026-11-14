package com.mediassistrover.backend.controller.facility;

import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import java.time.Clock;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MapCascadeHttpTest {
    final UUID id = UUID.randomUUID(), actorId = UUID.randomUUID();
    final FacilityService service = mock(FacilityService.class);
    final AccountPrincipal actor = AccountPrincipal.fromAccount(Account.builder().accountId(actorId).role(Role.ADMIN).build());
    final String path = "/api/v1/maps/" + id;
    final String body = "{\"reason\":\"Xóa map và phòng không sử dụng\"}";
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FacilityController(service, new DeletionPolicy()))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter p) { return p.getParameterType() == AccountPrincipal.class; }
                    @Override public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r,
                            org.springframework.web.bind.support.WebDataBinderFactory f) { return actor; }
                }).build();
        when(service.forceDeleteMap(eq(id), anyString(), eq(actorId), any()))
                .thenReturn(new HardDeleteResponse(id, "MAP", Map.of("Map", 1L, "Endpoint", 3L, "Room", 2L)));
    }

    @Test void forceDeleteReturnsActualMapEndpointAndRoomDeletionCounts() throws Exception {
        mvc.perform(delete(path + "/force").header("X-Confirm-Hard-Delete", true).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").value(id.toString()))
                .andExpect(jsonPath("$.deleted.Map").value(1)).andExpect(jsonPath("$.deleted.Endpoint").value(3))
                .andExpect(jsonPath("$.deleted.Room").value(2)).andExpect(jsonPath("$.status").doesNotExist());
        verify(service, never()).archiveMap(any(), any(), any(), any());
    }

    @Test void impactReportsTheSameCascadeAndSharedRooms() throws Exception {
        when(service.mapDeletionImpact(id)).thenReturn(new DeletionImpactResponse(id, "MAP", true, List.of(),
                Map.of("Map", 1L, "Endpoint", 3L, "Room", 2L, "sharedRooms", 1L)));
        mvc.perform(get(path + "/deletion-impact")).andExpect(status().isOk())
                .andExpect(jsonPath("$.canHardDelete").value(true)).andExpect(jsonPath("$.dependencies.Room").value(2))
                .andExpect(jsonPath("$.dependencies.sharedRooms").value(1));
        verify(service, never()).forceDeleteMap(any(), any(), any(), any());
    }

    @Test void missingOrFalseConfirmationDoesNotCallDeleteService() throws Exception {
        mvc.perform(delete(path + "/force").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(delete(path + "/force").header("X-Confirm-Hard-Delete", false).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test void invalidReasonDoesNotCallDeleteService() throws Exception {
        mvc.perform(delete(path + "/force").header("X-Confirm-Hard-Delete", true).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void runtimeReferenceConflictIsSurfacedWithoutArchiving() throws Exception {
        when(service.forceDeleteMap(eq(id), anyString(), eq(actorId), any())).thenThrow(new ConflictException("Shift.room_id còn 1 bản ghi tham chiếu"));
        mvc.perform(delete(path + "/force").header("X-Confirm-Hard-Delete", true).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Shift.room_id còn 1 bản ghi tham chiếu"));
        verify(service, never()).archiveMap(any(), any(), any(), any());
    }
}
