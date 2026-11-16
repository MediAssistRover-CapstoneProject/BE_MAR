package com.mediassistrover.backend.controller.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP binding only. Job ownership authorization is exercised by PatrolJobControlTest. */
class PatrolJobHttpTest {
    final UUID id=UUID.randomUUID();
    final PatrolJobControlService control=mock(PatrolJobControlService.class);
    final LocalValidatorFactoryBean validator=new LocalValidatorFactoryBean();
    MockMvc mvc;
    @BeforeEach void setup(){
        var controller=mock(JobController.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller,"patrolControl",control);
        validator.afterPropertiesSet();
        mvc=MockMvcBuilders.standaloneSetup(controller).setValidator(validator)
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
        var patrol=new PatrolJobState(2,3,false,true,Instant.parse("2026-11-16T00:00:00Z"),null,1000,id,null,"MOVE",
            "ACTIVE_SUSPENSION_HOLDS",List.of(new SuspensionHoldResponse("INCIDENT",id,Instant.parse("2026-11-16T00:00:00Z"))));
        var detail=new JobDetailResponse(null,List.of(),List.of(),patrol);
        when(control.suspend(eq(id),eq("pause"),any(),any())).thenReturn(detail);
        when(control.resume(eq(id),eq("pause"),any(),any())).thenReturn(detail);
    }
    @AfterEach void close(){validator.close();}
    @Test void suspendReturnsProgressAndIndependentHoldWithoutInventingCompletedStop() throws Exception {
        mvc.perform(post("/api/v1/jobs/"+id+"/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"pause\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.patrol.round").value(2))
            .andExpect(jsonPath("$.patrol.holds[0].source").value("INCIDENT"))
            .andExpect(jsonPath("$.patrol.resumeBlockedReason").value("ACTIVE_SUSPENSION_HOLDS"));
        verify(control).suspend(eq(id),eq("pause"),any(),any());
    }
    @Test void resumeReturnsActualBlockedState() throws Exception {
        mvc.perform(post("/api/v1/jobs/"+id+"/resume").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"pause\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.patrol.repeatCount").value(3));
        verify(control).resume(eq(id),eq("pause"),any(),any());
    }
    @Test void invalidReasonIsRejectedBeforeMutation() throws Exception {
        for(String action:List.of("suspend","resume"))
            mvc.perform(post("/api/v1/jobs/"+id+"/"+action).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(control);
    }
}
