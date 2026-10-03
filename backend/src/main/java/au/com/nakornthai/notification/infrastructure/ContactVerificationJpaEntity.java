package au.com.nakornthai.notification.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="contact_verification") @Getter @Setter
public class ContactVerificationJpaEntity {
 @Id private UUID id;
 private String channel;
 private String destinationHash;
 private String providerReference;
 private Instant createdAt;
 private Instant expiresAt;
 private Instant verifiedAt;
 private UUID consumedBy;
 private UUID consumedOrderId;
 private int attempts;
}
