package com.mediassistrover.backend.controller.facility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.facility.MapPresentationContracts.*;
import com.mediassistrover.backend.service.MapPresentationService;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP JSON binding/serialization only; authorization is covered separately. */
class MapPresentationHttpContractTest {
    final UUID id=UUID.fromString("11111111-1111-4111-8111-111111111111");
    final String hash="a".repeat(64);
    final String path="/api/v1/maps/"+id+"/presentation";
    final Source source=new Source(id,hash,10,6,.05,-2,3,.4,"map",200,120);
    final Instant publishedAt=Instant.parse("2026-11-07T00:00:00Z");
    MapPresentationService service;
    LocalValidatorFactoryBean validator;
    MockMvc mvc;

    @BeforeEach void setup() {
        service=mock(MapPresentationService.class);
        validator=new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc=MockMvcBuilders.standaloneSetup(new MapPresentationController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .setValidator(validator).build();
        when(service.save(eq(id),any(Save.class))).thenAnswer(call->{
            Save request=call.getArgument(1);
            return new Editor(source,request.expectedRevision()+1,request.document(),null,null,null,false);
        });
    }
    @AfterEach void closeValidator() { validator.close(); }

    String request(String rotationProperty) {
        return """
            {"expectedRevision":4,"sourceAssetHash":"%s","document":{
                "schemaVersion":1,"backgroundColor":"#ffffff","features":[]%s}}
            """.formatted(hash,rotationProperty);
    }

    @Test void putBindsAndReturnsRotationIncludingZeroAndBoundary() throws Exception {
        for(double angle:new double[]{-23.5,0.,180.,-180.}) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                    .content(request(",\"rotationDegrees\":"+angle)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.revision").value(5))
                .andExpect(jsonPath("$.draft.rotationDegrees").value(angle))
                .andExpect(jsonPath("$.source.originYawRad").value(.4));
            var captor=org.mockito.ArgumentCaptor.forClass(Save.class);
            verify(service).save(eq(id),captor.capture());
            assertEquals(angle,captor.getValue().document().rotationDegrees());
            assertEquals(hash,captor.getValue().sourceAssetHash());
            clearInvocations(service);
        }
    }

    @Test void legacyMissingAndNullRotationBecomeExplicitZeroInHttpResponse() throws Exception {
        for(String property:new String[]{"",",\"rotationDegrees\":null"}) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request(property)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft.rotationDegrees").value(0.));
        }
    }

    @Test void invalidRotationIsRejectedAtHttpBoundaryBeforeServiceCall() throws Exception {
        for(String value:new String[]{"180.01","-180.01","\"invalid\""}) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                    .content(request(",\"rotationDegrees\":"+value)))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void editorGetExposesDraftAndPublishedRotationIndependently() throws Exception {
        var draft=new Document(1,"#ffffff",List.of(),45.);
        var published=new Document(1,"#ffffff",List.of(),-23.5);
        when(service.editor(id)).thenReturn(new Editor(source,6,draft,published,5L,publishedAt,false));
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(jsonPath("$.draft.rotationDegrees").value(45.))
            .andExpect(jsonPath("$.published.rotationDegrees").value(-23.5))
            .andExpect(header().string("Cache-Control","no-store"));
    }

    @Test void publishAndPublishedGetExposeSavedRotation() throws Exception {
        var document=new Document(1,"#ffffff",List.of(),-23.5);
        when(service.publish(eq(id),any(Change.class)))
            .thenReturn(new Editor(source,6,document,document,6L,publishedAt,false));
        when(service.published(id)).thenReturn(new Published(source,6,document,publishedAt));
        mvc.perform(post(path+"/publish").contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":5}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.published.rotationDegrees").value(-23.5));
        verify(service).publish(id,new Change(5));
        mvc.perform(get(path+"/published")).andExpect(status().isOk())
            .andExpect(jsonPath("$.document.rotationDegrees").value(-23.5))
            .andExpect(jsonPath("$.source.originYawRad").value(.4));
    }
}
