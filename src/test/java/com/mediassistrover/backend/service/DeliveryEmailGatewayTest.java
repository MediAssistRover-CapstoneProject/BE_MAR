package com.mediassistrover.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryEmailGatewayTest {
    @Test void missingMailSenderDoesNotPreventGatewayConstruction() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> senders = mock(ObjectProvider.class);
        when(senders.getIfAvailable()).thenReturn(null);
        var gateway = new DeliveryEmailGateway(senders);

        assertFalse(gateway.available());
        assertThrows(MailSendException.class,
                () -> gateway.send("from@example.test", "to@example.test", "subject", "body"));
    }
}
