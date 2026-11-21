package com.mediassistrover.backend.knowledge;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeResolveTest {
    @Test void serviceCredentialIsRequiredEvenForEmptyOrKnownFileIds() {
        var service=mock(KnowledgeDocumentService.class);
        var controller=new KnowledgeResolveController(service,"x".repeat(32));
        var request=new KnowledgeResolveController.Resolve("vs_test",List.of("file-test"));
        for(String token:new String[]{"", "wrong", "x".repeat(31)})
            assertEquals(401,assertThrows(ResponseStatusException.class,()->controller.resolve(token,request)).getStatusCode().value());
        verifyNoInteractions(service);
        controller.resolve("x".repeat(32),request);
        verify(service).resolve("vs_test",List.of("file-test"));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->new KnowledgeResolveController(service,"").resolve("",request)).getStatusCode().value());
    }
}
