package com.mediassistrover.backend.service.impl;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.ByteBuffer;import java.util.*;import java.util.UUID;
@Service @RequiredArgsConstructor public class FloorHardDeleteService {
 private final JdbcTemplate jdbc;
 @Transactional(readOnly=true) public DeletionImpactResponse impact(UUID id){byte[] k=bytes(id);exists(k);long maps=count("SELECT COUNT(*) FROM Map WHERE floor_id=?",k),rooms=count("SELECT COUNT(*) FROM Room WHERE floor_id=?",k),shifts=count("SELECT COUNT(*) FROM Shift WHERE room_id IN (SELECT room_id FROM Room WHERE floor_id=?)",k);var d=Map.of("maps",maps,"rooms",rooms,"shiftsThroughRooms",shifts);var b=new ArrayList<String>();if(maps>0)b.add("Floor still has Map records");if(rooms>0)b.add("Floor still has Room records");return new DeletionImpactResponse(id,"Floor",b.isEmpty(),List.copyOf(b),d);}
 @Transactional(propagation=Propagation.MANDATORY) public HardDeleteResponse delete(UUID id){byte[] k=bytes(id);if(jdbc.query("SELECT floor_id FROM Floor WHERE floor_id=? FOR UPDATE",(r,n)->r.getBytes(1),k).isEmpty())throw new ResourceNotFoundException("Floor not found: "+id);var i=impact(id);if(!i.canHardDelete())throw new ConflictException(String.join(", ",i.blockers()));long n=jdbc.update("DELETE FROM Floor WHERE floor_id=?",k);return new HardDeleteResponse(id,"Floor",Map.of("floors",n));}
 private void exists(byte[] k){if(count("SELECT COUNT(*) FROM Floor WHERE floor_id=?",k)==0)throw new ResourceNotFoundException("Floor not found");}private long count(String q,byte[] k){Long n=jdbc.queryForObject(q,Long.class,k);return n==null?0:n;}private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
