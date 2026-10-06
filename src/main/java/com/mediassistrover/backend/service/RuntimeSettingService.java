package com.mediassistrover.backend.service;

public interface RuntimeSettingService {
    long getLong(String key, long fallback, long minimum, long maximum);
}
