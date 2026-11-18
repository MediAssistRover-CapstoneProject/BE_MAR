package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class FaceAiClient {
    private final ObjectMapper json;
    private final String base,key;
    private final boolean enabled;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public FaceAiClient(ObjectMapper json,@Value("${face.enabled:false}") boolean enabled,
            @Value("${face.base-url:http://127.0.0.1:8095}") String base,@Value("${face.service-key:}") String key) {
        this.json=json;this.base=base;this.key=key;this.enabled=enabled;
    }
    public boolean enabled(){return enabled && key.length()>=32;}
    public void requireEnabled(){if(!enabled())throw new BusinessRuleException("FACE_DISABLED");}
    public JsonNode call(String operation,JsonNode body){
        requireEnabled();
        try {
            var request=HttpRequest.newBuilder(URI.create(base+"/v1/face/"+operation)).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/json").header("X-Face-Service-Key",key)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200 || response.body().length()>16384)throw new BusinessRuleException("FACE_AI_UNAVAILABLE");
            return json.readTree(response.body());
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessRuleException("FACE_AI_UNAVAILABLE");}
          catch(Exception e){throw new BusinessRuleException("FACE_AI_UNAVAILABLE");}
    }
}
