package au.com.nakornthai.identity.infrastructure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="staff_session") @Getter @Setter
public class StaffSessionJpaEntity {
    @Id private UUID id=UUID.randomUUID();
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id") private UserJpaEntity user;
    @Column(nullable=false,length=64) private String refreshHash;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant expiresAt;
    private Instant revokedAt;
}
