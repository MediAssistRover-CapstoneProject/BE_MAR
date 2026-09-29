package fu.se181738.backend_robot_v1.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Exactly PK(endpoint_group_id, endpoint_id); no surrogate key. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EndpointGroupMemberId implements Serializable {
    private UUID endpoint_group_id;
    private UUID endpoint_id;
}
