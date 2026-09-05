package au.com.nakornthai.identity.infrastructure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="staff_user") @Getter @Setter
public class UserJpaEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=50,unique=true) private String username;
    @Column(nullable=false,length=100) private String passwordHash;
    @Column(nullable=false,length=20) private String role;
    @Column(nullable=false) private boolean enabled=true;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    @Version private Long version;
}
