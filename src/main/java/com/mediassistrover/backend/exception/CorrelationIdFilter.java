package com.mediassistrover.backend.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = supplied == null || supplied.isBlank() || supplied.length() > MAX_CORRELATION_ID_LENGTH
                ? UUID.randomUUID().toString()
                : supplied;

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        long started=System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String stage=switch(request.getRequestURI()) {
                case "/api/v1/pi/voice/navigation-destinations" -> "catalog";
                case "/api/v1/pi/voice/navigation-requests" -> "navigation_request";
                case "/api/v1/pi/speech/profile" -> "speech_profile";
                case "/api/v1/pi/speech/transcribe" -> "transcribe";
                case "/api/v1/pi/speech/synthesize" -> "synthesize";
                default -> null;
            };
            if(stage!=null)log.info("VOICE_HTTP stage={} status={} elapsed_ms={} correlation_id={}",
                    stage,response.getStatus(),(System.nanoTime()-started)/1_000_000,correlationId);
            MDC.remove("correlationId");
        }
    }
}
