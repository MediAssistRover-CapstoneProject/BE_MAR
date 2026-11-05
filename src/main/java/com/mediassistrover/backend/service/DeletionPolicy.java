package com.mediassistrover.backend.service;

import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
public class DeletionPolicy {
    public void requireHardDeleteConfirmation(Boolean confirmed) {
        if(!Boolean.TRUE.equals(confirmed))
            throw new BusinessRuleException("X-Confirm-Hard-Delete must be true for permanent deletion");
    }
}
