package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.*;

@Service @RequiredArgsConstructor
public class StaffHardDeleteService {
    private static final Set<String> OWNED=Set.of("StaffProfile.account_id","RefreshToken.account_id","RevokedToken.account_id");
    private final JdbcTemplate jdbc;

    @Transactional(readOnly=true)
    public DeletionImpactResponse impact(UUID staffId){
        byte[] account=accountId(staffId,false);Map<String,Long> refs=references(account);
        List<String> blockers=refs.entrySet().stream().filter(e->!OWNED.contains(e.getKey())&&e.getValue()>0).map(e->e.getKey()+" has "+e.getValue()+" reference(s)").toList();
        return new DeletionImpactResponse(staffId,"Staff",blockers.isEmpty(),blockers,Map.copyOf(refs));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public HardDeleteResponse delete(UUID staffId){
        byte[] account=accountId(staffId,true);Map<String,Long> refs=references(account);
        List<String> blockers=refs.entrySet().stream().filter(e->!OWNED.contains(e.getKey())&&e.getValue()>0).map(e->e.getKey()).toList();
        if(!blockers.isEmpty())throw new ConflictException("Staff account is still referenced by: "+String.join(", ",blockers));
        jdbc.update("UPDATE RefreshToken SET replaced_by_token_id=NULL WHERE account_id=?",account);
        long refresh=jdbc.update("DELETE FROM RefreshToken WHERE account_id=?",account);
        long revoked=jdbc.update("DELETE FROM RevokedToken WHERE account_id=?",account);
        long profile=jdbc.update("DELETE FROM StaffProfile WHERE staff_profile_id=?",bytes(staffId));
        long accounts=jdbc.update("DELETE FROM Account WHERE account_id=?",account);
        return new HardDeleteResponse(staffId,"Staff",Map.of("accounts",accounts,"staffProfiles",profile,"refreshTokens",refresh,"revokedTokens",revoked));
    }

    private byte[] accountId(UUID staffId,boolean lock){
        List<byte[]> rows=jdbc.query("SELECT account_id FROM StaffProfile WHERE staff_profile_id=?"+(lock?" FOR UPDATE":""),(rs,n)->rs.getBytes(1),bytes(staffId));
        if(rows.isEmpty())throw new ResourceNotFoundException("Staff not found");return rows.getFirst();
    }
    private Map<String,Long> references(byte[] account){
        var result=new LinkedHashMap<String,Long>();
        var keys=jdbc.query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='Account' ORDER BY TABLE_NAME,COLUMN_NAME",(rs,n)->List.of(rs.getString(1),rs.getString(2)));
        for(var key:keys){String table=key.get(0),column=key.get(1);Long count=jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"` WHERE `"+column+"`=?",Long.class,account);result.put(table+"."+column,count==null?0:count);}
        return result;
    }
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
