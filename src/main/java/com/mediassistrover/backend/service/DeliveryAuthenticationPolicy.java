package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.DeliveryRequest;
import com.mediassistrover.backend.enums.DeliveryAuthenticationMethod;
import com.mediassistrover.backend.enums.AuthVerificationPurpose;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.DeliveryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A persisted item flag determines the handover method; free text never authorizes a lock. */
@Service @RequiredArgsConstructor
public class DeliveryAuthenticationPolicy {
    private final DeliveryItemRepository items;

    public static DeliveryAuthenticationMethod required(boolean sensitive) {
        return sensitive ? DeliveryAuthenticationMethod.FACE_VERIFICATION : DeliveryAuthenticationMethod.QR_CODE;
    }

    public DeliveryAuthenticationMethod require(DeliveryRequest delivery) {
        boolean sensitive = items.findByDeliveryRequestServiceRequestIdOrderByItemCodeAsc(delivery.getServiceRequestId())
                .stream().anyMatch(item -> item.isSensitive());
        DeliveryAuthenticationMethod required = required(sensitive);
        if (delivery.getAuthenticationMethod() != required)
            throw new BusinessRuleException("DELIVERY_AUTHENTICATION_POLICY_MISMATCH: important goods require face; standard goods require QR");
        return required;
    }

    /** Sender always presents QR; only the receiver of sensitive goods needs face. */
    public DeliveryAuthenticationMethod methodFor(DeliveryRequest delivery, AuthVerificationPurpose purpose) {
        DeliveryAuthenticationMethod receiverMethod = require(delivery);
        if (purpose == AuthVerificationPurpose.DELIVERY_SENDER) return DeliveryAuthenticationMethod.QR_CODE;
        if (purpose == AuthVerificationPurpose.DELIVERY_RECEIVER) return receiverMethod;
        throw new BusinessRuleException("Unsupported delivery authentication purpose");
    }
}
