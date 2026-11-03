package com.mediassistrover.backend.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.repository.FacilityMapRepository;
import com.mediassistrover.backend.validation.MappingArtifactContractValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalMapPreviewControllerTest {
    @Test
    void rendersBundledYamlAndPgmWithoutPersisting() throws Exception {
        MappingProperties properties = new MappingProperties();
        MappingArchiveReader reader = new MappingArchiveReader(
                new ObjectMapper(), new MappingArtifactContractValidator(), properties);
        FacilityMapRepository maps = mock(FacilityMapRepository.class);
        when(maps.findById(any())).thenReturn(Optional.empty());
        LocalMapPreviewController controller = new LocalMapPreviewController(reader, maps);

        byte[] yaml = resource("/static/map-assets/HOSPITAL_F1/v2/map.yaml");
        byte[] pgm = resource("/static/map-assets/HOSPITAL_F1/v2/map.pgm");
        var response = controller.preview(null,
                new MockMultipartFile("yaml", "map.yaml", "text/yaml", yaml),
                new MockMultipartFile("pgm", "map.pgm", "image/x-portable-graymap", pgm));

        assertThat(response.catalogFound()).isFalse();
        assertThat(response.assetHash()).hasSize(64);
        assertThat(response.metadata().path("width_px").asInt()).isPositive();
        assertThat(response.metadata().path("height_px").asInt()).isPositive();
        assertThat(response.imageDataUrl()).startsWith("data:image/png;base64,iVBOR");
    }

    private byte[] resource(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            assertThat(input).isNotNull();
            return input.readAllBytes();
        }
    }
}
