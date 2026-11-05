package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.service.FacilityDeletionResource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.ByteBuffer;import java.util.*;import java.util.UUID;

@Service @RequiredArgsConstructor
public class FacilityCatalogHardDeleteService {
 private record Spec(String table,String idColumn,Set<String> owned){}
 private static final Map<FacilityDeletionResource,Spec> SPECS=Map.of(
  FacilityDeletionResource.MAP,new Spec("Map","map_id",Set.of("MapBinaryAsset.map_id","MapArtifact.map_id")),
  FacilityDeletionResource.ZONE,new Spec("Zone","zone_id",Set.of()),
  FacilityDeletionResource.ROOM,new Spec("Room","room_id",Set.of()),
  FacilityDeletionResource.ENDPOINT,new Spec("Endpoint","endpoint_id",Set.of("EndpointGroupMember.endpoint_id")),
  FacilityDeletionResource.ENDPOINT_GROUP,new Spec("EndpointGroup","endpoint_group_id",Set.of("EndpointGroupMember.endpoint_group_id")));
 private final JdbcTemplate jdbc;
 @Transactional(readOnly=true) public DeletionImpactResponse impact(FacilityDeletionResource resource,UUID id){Spec s=SPECS.get(resource);byte[] k=bytes(id);exists(s,k,id);Map<String,Long> refs=references(s,k);List<String> blockers=refs.entrySet().stream().filter(e->!s.owned().contains(e.getKey())&&e.getValue()>0).map(e->e.getKey()+" has "+e.getValue()+" reference(s)").toList();return new DeletionImpactResponse(id,resource.name(),blockers.isEmpty(),blockers,Map.copyOf(refs));}
 @Transactional(propagation=Propagation.MANDATORY) public HardDeleteResponse delete(FacilityDeletionResource resource,UUID id){Spec s=SPECS.get(resource);byte[] k=bytes(id);if(jdbc.query("SELECT `"+s.idColumn()+"` FROM `"+s.table()+"` WHERE `"+s.idColumn()+"`=? FOR UPDATE",(r,n)->r.getBytes(1),k).isEmpty())throw new ResourceNotFoundException(resource+" not found: "+id);DeletionImpactResponse impact=impact(resource,id);if(!impact.canHardDelete())throw new ConflictException(String.join(", ",impact.blockers()));var deleted=new LinkedHashMap<String,Long>();if(resource==FacilityDeletionResource.MAP){deleted.put("mapBinaryAssets",(long)jdbc.update("DELETE FROM MapBinaryAsset WHERE map_id=?",k));deleted.put("mapArtifacts",(long)jdbc.update("DELETE FROM MapArtifact WHERE map_id=?",k));}if(resource==FacilityDeletionResource.ENDPOINT)deleted.put("memberships",(long)jdbc.update("DELETE FROM EndpointGroupMember WHERE endpoint_id=?",k));if(resource==FacilityDeletionResource.ENDPOINT_GROUP)deleted.put("memberships",(long)jdbc.update("DELETE FROM EndpointGroupMember WHERE endpoint_group_id=?",k));deleted.put(s.table(),(long)jdbc.update("DELETE FROM `"+s.table()+"` WHERE `"+s.idColumn()+"`=?",k));return new HardDeleteResponse(id,resource.name(),Map.copyOf(deleted));}
 private Map<String,Long> references(Spec s,byte[] k){var out=new LinkedHashMap<String,Long>();var keys=jdbc.query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME=? ORDER BY TABLE_NAME,COLUMN_NAME",(r,n)->List.of(r.getString(1),r.getString(2)),s.table());for(var x:keys){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM `"+x.get(0)+"` WHERE `"+x.get(1)+"`=?",Long.class,k);out.put(x.get(0)+"."+x.get(1),n==null?0:n);}return out;}
 private void exists(Spec s,byte[] k,UUID id){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM `"+s.table()+"` WHERE `"+s.idColumn()+"`=?",Long.class,k);if(n==null||n==0)throw new ResourceNotFoundException(s.table()+" not found: "+id);}private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
