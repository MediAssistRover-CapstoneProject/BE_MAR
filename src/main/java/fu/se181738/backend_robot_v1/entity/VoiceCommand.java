package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.VoiceCommandStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * VoiceCommand mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(voice_command_id)`.
 * - UQ(voice_interaction_id, sequence_no).
 * 
 * Nguồn: S0, S2
 */
@Entity
@Table(name = "VoiceCommand", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"voice_interaction_id", "sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "sequence_no >= 1")
@Check(constraints = "confidence_score BETWEEN 0 AND 1")
public class VoiceCommand {

    /** ID lệnh giọng nói. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "voice_command_id", nullable = false)
    private UUID voice_command_id;

    /** Phiên chứa lệnh. Tham chiếu VoiceInteraction.voice_interaction_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_interaction_id", referencedColumnName = "voice_interaction_id", nullable = false)
    private VoiceInteraction voice_interaction_id;

    /** Yêu cầu tạo ra từ lệnh, nếu có. Tham chiếu ServiceRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "service_request_id", referencedColumnName = "service_request_id", nullable = true)
    private ServiceRequest service_request_id;

    /** Thứ tự lệnh >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Văn bản nhận dạng giọng nói. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String raw_text;

    /** Văn bản chuẩn hóa. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "normalized_text", nullable = true, columnDefinition = "text")
    private String normalized_text;

    /** Ý định được nhận diện. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "intent_code", nullable = true, length = 80)
    private String intent_code;

    /** Độ tin cậy trong [0,1]; NULL nếu không có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "confidence_score", nullable = true, precision = 5, scale = 4)
    private BigDecimal confidence_score;

    /** Loại lệnh nghiệp vụ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "command_type", nullable = false, length = 64)
    private String command_type;

    /** Tham số trích xuất; chưa phải FK trong JSON. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", nullable = false)
    private String parameters_json;

    /** RECEIVED, RESOLVED, REJECTED, EXECUTED, FAILED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VoiceCommandStatus status;

    /** Thời điểm nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "received_at", nullable = false)
    private Instant received_at;

    /** Thời điểm xử lý xong. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "processed_at", nullable = true)
    private Instant processed_at;
}
