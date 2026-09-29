package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.VoiceInteractionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * VoiceInteraction mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(voice_interaction_id)`.
 * - ended_at >= started_at khi có giá trị.
 * 
 * Nguồn: S0, S2, S3
 */
@Entity
@Table(name = "VoiceInteraction")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "ended_at IS NULL OR ended_at >= started_at")
public class VoiceInteraction {

    /** ID phiên giọng nói. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "voice_interaction_id", nullable = false)
    private UUID voice_interaction_id;

    /** Người dùng đã định danh; có thể là khách. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = true)
    private Account account_id;

    /** Robot phục vụ phiên. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = false)
    private Robot robot_id;

    /** Ngôn ngữ phiên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "language_code", nullable = false, length = 16)
    private String language_code;

    /** ACTIVE, COMPLETED, CANCELLED, FAILED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VoiceInteractionStatus status;

    /** Bắt đầu phiên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "started_at", nullable = false)
    private Instant started_at;

    /** Kết thúc phiên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ended_at", nullable = true)
    private Instant ended_at;

    /** Ý định ở mức phiên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "detected_intent", nullable = true, length = 80)
    private String detected_intent;

    /** Tóm tắt hội thoại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "transcript_summary", nullable = true, columnDefinition = "text")
    private String transcript_summary;

    /** Tham chiếu audio nếu được lưu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "audio_uri", nullable = true, columnDefinition = "text")
    private String audio_uri;
}
