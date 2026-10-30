package com.mediassistrover.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum EndpointType {
    WAYPOINT,
    /** Arrival point for manual charging; MOVE does not confirm electrical charging. */
    CHARGER,
    /** Regular parking position; may be used as an operator-confirmed AMCL seed. */
    PARK,
    STAGING
}
