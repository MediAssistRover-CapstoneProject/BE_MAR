package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.ByteBuffer;
import java.util.*;

@Service @RequiredArgsConstructor
public class HospitalHardDeleteService {
    private final JdbcTemplate jdbc;
    @Transactional(readOnly=true) public DeletionImpactResponse impact(UUID id){byte[] key=bytes(id);exists(key);long floors=count("SELECT COUNT(*) FROM Floor WHERE hospital_information_id=?",key),articles=count("SELECT COUNT(*) FROM HospitalInformationArticle WHERE hospital_information_id=?",key);List<String> blockers=floors>0?List.of("Hospital still has "+floors+" Floor record(s)"):List.of();return new DeletionImpactResponse(id,"HospitalInformation",blockers.isEmpty(),blockers,Map.of("floors",floors,"articles",articles));}
    @Transactional(propagation=Propagation.MANDATORY) public HardDeleteResponse delete(UUID id){byte[] key=bytes(id);List<byte[]> locked=jdbc.query("SELECT hospital_information_id FROM HospitalInformation WHERE hospital_information_id=? FOR UPDATE",(r,n)->r.getBytes(1),key);if(locked.isEmpty())throw new ResourceNotFoundException("HospitalInformation not found: "+id);long floors=count("SELECT COUNT(*) FROM Floor WHERE hospital_information_id=?",key);if(floors>0)throw new ConflictException("HospitalInformation still has Floor references");long articles=jdbc.update("DELETE FROM HospitalInformationArticle WHERE hospital_information_id=?",key);long hospitals=jdbc.update("DELETE FROM HospitalInformation WHERE hospital_information_id=?",key);return new HardDeleteResponse(id,"HospitalInformation",Map.of("hospitals",hospitals,"articles",articles));}
    private void exists(byte[] key){if(count("SELECT COUNT(*) FROM HospitalInformation WHERE hospital_information_id=?",key)==0)throw new ResourceNotFoundException("HospitalInformation not found");}
    private long count(String sql,byte[] key){Long n=jdbc.queryForObject(sql,Long.class,key);return n==null?0:n;}
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
