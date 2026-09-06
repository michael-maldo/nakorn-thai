package au.com.nakornthai.notification.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="order_verification") @Getter @Setter
public class OrderVerificationJpaEntity {
 @Id private UUID id;
 private UUID orderId;
 private String destinationHash;
 private String channel;
 private String providerSid;
 private Instant createdAt;
 private Instant expiresAt;
 private int attempts;
 private boolean consumed;
}
