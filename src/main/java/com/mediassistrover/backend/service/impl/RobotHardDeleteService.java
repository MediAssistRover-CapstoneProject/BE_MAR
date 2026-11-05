package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.ByteBuffer;import java.util.*;import java.util.UUID;

@Service @RequiredArgsConstructor
public class RobotHardDeleteService {
 private final JdbcTemplate jdbc;
 @Transactional(readOnly=true) public DeletionImpactResponse impact(UUID id){byte[] key=key(id);exists(key,id);Map<String,Long> refs=references(key);List<String> blockers=refs.entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey()+" has "+e.getValue()+" reference(s)").toList();return new DeletionImpactResponse(id,"Robot",blockers.isEmpty(),blockers,Map.copyOf(refs));}
 @Transactional(propagation=Propagation.MANDATORY) public HardDeleteResponse delete(UUID id){byte[] key=key(id);if(jdbc.query("SELECT robot_id FROM Robot WHERE robot_id=? FOR UPDATE",(r,n)->r.getBytes(1),key).isEmpty())throw new ResourceNotFoundException("Robot not found: "+id);DeletionImpactResponse impact=impact(id);if(!impact.canHardDelete())throw new ConflictException(String.join(", ",impact.blockers()));return new HardDeleteResponse(id,"Robot",Map.of("Robot",(long)jdbc.update("DELETE FROM Robot WHERE robot_id=?",key)));}
 private Map<String,Long> references(byte[] key){var out=new LinkedHashMap<String,Long>();var refs=jdbc.query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='Robot' ORDER BY TABLE_NAME,COLUMN_NAME",(r,n)->List.of(r.getString(1),r.getString(2)));for(var ref:refs){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM `"+ref.get(0)+"` WHERE `"+ref.get(1)+"`=?",Long.class,key);out.put(ref.get(0)+"."+ref.get(1),count==null?0:count);}return out;}
 private void exists(byte[] key,UUID id){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM Robot WHERE robot_id=?",Long.class,key);if(count==null||count==0)throw new ResourceNotFoundException("Robot not found: "+id);}private static byte[] key(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
