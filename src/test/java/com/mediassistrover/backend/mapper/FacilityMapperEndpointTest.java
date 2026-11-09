package com.mediassistrover.backend.mapper;

import com.mediassistrover.backend.dto.facility.request.FacilityRequests.EndpointRequest;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.enums.EndpointType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityMapperEndpointTest {
    private final FacilityMapper mapper = Mappers.getMapper(FacilityMapper.class);
    private final UUID mapId = UUID.randomUUID();

    private EndpointRequest request(String x, String y) {
        return new EndpointRequest(mapId, null, null, "PARK_A", "Parking A", EndpointType.PARK,
                new BigDecimal(x), new BigDecimal(y), new BigDecimal("1.570796"),
                true, true, false, "Moved parking point");
    }

    @Test
    void updatingSavedEndpointReturnsNewCoordinatesAndPreservesIdentity() {
        UUID endpointId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");
        Endpoint endpoint = Endpoint.builder().endpointId(endpointId)
                .map(FacilityMap.builder().mapId(mapId).build())
                .xM(new BigDecimal("2.0000")).yM(new BigDecimal("3.0000"))
                .yawRad(BigDecimal.ZERO).createdAt(createdAt).build();
        EndpointRequest request = request("-1.2500", "0.0000");

        mapper.update(request, endpoint);

        assertEquals(request.xM(), endpoint.getXM(), "Updating must replace the old X coordinate");
        assertEquals(request.yM(), endpoint.getYM(), "Updating must replace the old Y, including zero");
        assertEquals(request.yawRad(), endpoint.getYawRad());
        assertEquals(endpointId, endpoint.getEndpointId());
        assertEquals(createdAt, endpoint.getCreatedAt());
        var response = mapper.toResponse(endpoint);
        assertEquals(mapId, response.mapId());
        assertEquals(endpointId, response.endpointId());
        assertEquals(request.xM(), response.xM());
        assertEquals(request.yM(), response.yM());
    }

    @Test
    void creatingEndpointStillMapsCoordinates() {
        EndpointRequest request = request("0.0000", "-2.5000");

        Endpoint endpoint = mapper.toEntity(request);

        assertEquals(request.xM(), endpoint.getXM());
        assertEquals(request.yM(), endpoint.getYM());
        assertEquals(request.yawRad(), endpoint.getYawRad());
    }
}
