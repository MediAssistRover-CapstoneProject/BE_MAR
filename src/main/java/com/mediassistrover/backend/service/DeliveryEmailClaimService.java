package com.mediassistrover.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Claims one committed email row without holding a database lock during SMTP I/O. */
@Service
@RequiredArgsConstructor
public class DeliveryEmailClaimService {
    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String, Object> claim() {
        var rows = jdbc.queryForList("select * from delivery_email_outbox where state in ('PENDING','SENDING') "
                + "and available_at<=CURRENT_TIMESTAMP(6) order by available_at limit 1 for update skip locked");
        if (rows.isEmpty()) return null;
        var row = new HashMap<>(rows.getFirst());
        String lease = UUID.randomUUID().toString();
        jdbc.update("update delivery_email_outbox set state='SENDING',lease_token=?,available_at=? where id=?",
                lease, Timestamp.from(Instant.now().plusSeconds(30)), row.get("id"));
        row.put("lease_token", lease);
        return row;
    }
}
