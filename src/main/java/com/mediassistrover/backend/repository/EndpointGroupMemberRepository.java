package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.EndpointGroupMember;
import com.mediassistrover.backend.entity.EndpointGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EndpointGroupMemberRepository extends JpaRepository<EndpointGroupMember, EndpointGroupMemberId> {
    boolean existsByEndpointGroupEndpointGroupIdAndSequenceNo(UUID groupId, int sequenceNo);
    List<EndpointGroupMember> findByEndpointGroupEndpointGroupIdOrderBySequenceNo(UUID groupId);
}
