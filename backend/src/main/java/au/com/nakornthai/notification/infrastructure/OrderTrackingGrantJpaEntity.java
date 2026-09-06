package au.com.nakornthai.notification.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="order_tracking_grant") @Getter @Setter
public class OrderTrackingGrantJpaEntity {
 @Id private String tokenHash;
 private UUID orderId;
 private Instant expiresAt;
}
