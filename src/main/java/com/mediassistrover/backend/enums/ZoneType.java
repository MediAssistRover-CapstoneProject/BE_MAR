package com.mediassistrover.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true, allowableValues = {"KEEP_OUT", "SPEED", "NO_STOP"})
public enum ZoneType {
    KEEP_OUT,
    SPEED,
    NO_STOP
}
