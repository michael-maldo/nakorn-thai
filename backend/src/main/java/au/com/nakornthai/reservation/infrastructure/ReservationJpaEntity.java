package au.com.nakornthai.reservation.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="reservation") @Getter @Setter @NoArgsConstructor
public class ReservationJpaEntity {
 @Id private UUID id;
 @Column(nullable=false) private String customerName;
 @Column(nullable=false) private String phone;
 private int partySize;
 private LocalDateTime requestedAt;
 private String notes;
 private String status="REQUESTED";
 private String staffNote="";
 private String updatedBy;
 private Instant createdAt=Instant.now();
 private Instant updatedAt=Instant.now();
 @Version private Long version;
}
