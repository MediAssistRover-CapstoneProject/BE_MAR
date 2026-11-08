package com.mediassistrover.backend.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.exception.BusinessRuleException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Private VPC/loopback HTTP control; external signalling remains WSS and media DTLS-SRTP. */
@Component
public class LiveKitGateway {
    private final MediaProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public LiveKitGateway(MediaProperties properties,ObjectMapper json,Clock clock) {
        this.properties=properties;this.json=json;this.clock=clock;
    }
    public void ready() {
        if (!properties.isEnabled()) throw new BusinessRuleException("MEDIA_DISABLED");
        URI external=URI.create(properties.getUrl()), internal=URI.create(properties.getInternalUrl());
        if (!"wss".equals(external.getScheme()) || external.getHost()==null || external.getUserInfo()!=null
                || external.getQuery()!=null || external.getFragment()!=null
                || !Set.of("", "/").contains(Optional.ofNullable(external.getPath()).orElse("")))
            throw new BusinessRuleException("MEDIA_EXTERNAL_WSS_ORIGIN_REQUIRED");
        if (!"http".equals(internal.getScheme()) || !privateHost(internal.getHost())
                || internal.getUserInfo()!=null || internal.getQuery()!=null || internal.getFragment()!=null
                || !Set.of("", "/").contains(Optional.ofNullable(internal.getPath()).orElse("")))
            throw new BusinessRuleException("MEDIA_INTERNAL_PRIVATE_HTTP_REQUIRED");
        if (properties.getApiKey().isBlank() || properties.getApiSecret().getBytes(StandardCharsets.UTF_8).length<32
                || properties.getTokenTtlSec()<30 || properties.getTokenTtlSec()>120
                || properties.getSessionMaxSec()<60 || properties.getSessionMaxSec()>7200
                || properties.getRingTimeoutSec()<15 || properties.getRingTimeoutSec()>180
                || properties.getDeviceTimeoutSec()<20 || properties.getDeviceTimeoutSec()>120)
            throw new BusinessRuleException("MEDIA_CONFIGURATION_INVALID");
    }
    private boolean privateHost(String host) {
        if(host==null)return false;
        if(Set.of("127.0.0.1","localhost","[::1]").contains(host))return true;
        if(!host.matches("[0-9.]+"))return false;
        try {return java.net.InetAddress.getByName(host).isSiteLocalAddress();}
        catch(java.net.UnknownHostException e){return false;}
    }
    String sign(String identity,Map<String,Object> grant,Instant expiry) {
        return Jwts.builder().issuer(properties.getApiKey()).subject(identity)
                .issuedAt(Date.from(Instant.now(clock))).notBefore(Date.from(Instant.now(clock).minusSeconds(5)))
                .expiration(Date.from(expiry)).claim("video",grant)
                .signWith(new SecretKeySpec(properties.getApiSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"),Jwts.SIG.HS256)
                .compact();
    }
    public String join(MediaSession session,String identity,boolean device) {
        ready();
        Map<String,Object> grant=new LinkedHashMap<>();
        grant.put("room",session.room());grant.put("roomJoin",true);grant.put("canSubscribe",true);
        grant.put("canPublishData",false);grant.put("canPublish",device || session.getMode()==MediaSession.Mode.ASSIST);
        grant.put("canPublishSources",device
                ? session.getMode()==MediaSession.Mode.MONITOR ? List.of("camera") : List.of("camera","microphone")
                : session.getMode()==MediaSession.Mode.ASSIST ? List.of("microphone") : List.of());
        Instant limit=Instant.now(clock).plusSeconds(properties.getTokenTtlSec());
        return sign(identity,grant,limit.isBefore(session.getExpiresAt())?limit:session.getExpiresAt());
    }
    public void createRoom(MediaSession session) {
        rpc("CreateRoom",Map.of("name",session.room(),"empty_timeout",60,"departure_timeout",15,"max_participants",2),Map.of("roomCreate",true));
    }
    public void deleteRoom(MediaSession session) {
        rpc("DeleteRoom",Map.of("room",session.room()),Map.of("roomCreate",true));
    }
    private void rpc(String method,Map<String,Object> body,Map<String,Object> grant) {
        ready();
        try {
            String origin=properties.getInternalUrl().replaceAll("/+$","");
            var request=HttpRequest.newBuilder(URI.create(origin+"/twirp/livekit.RoomService/"+method))
                    .timeout(Duration.ofSeconds(3)).header("Content-Type","application/json")
                    .header("Authorization","Bearer "+sign("mar-backend",grant,Instant.now(clock).plusSeconds(30)))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.discarding());
            if (response.statusCode()==404 && method.equals("DeleteRoom")) return;
            if (response.statusCode()/100!=2) throw new BusinessRuleException("MEDIA_SERVER_HTTP_"+response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();throw new BusinessRuleException("MEDIA_SERVER_INTERRUPTED");
        } catch (java.io.IOException e) { throw new BusinessRuleException("MEDIA_SERVER_UNAVAILABLE"); }
    }
}
