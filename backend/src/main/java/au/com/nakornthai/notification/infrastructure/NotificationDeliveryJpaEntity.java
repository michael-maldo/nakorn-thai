package au.com.nakornthai.notification.infrastructure;
import au.com.nakornthai.notification.domain.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="notification_delivery") @Getter @Setter
public class NotificationDeliveryJpaEntity {
 @Id private UUID id;
 private UUID reservationId;
 @Enumerated(EnumType.STRING) private NotificationType type;
 @Enumerated(EnumType.STRING) private NotificationChannel channel;
 private String recipient;
 private String subject;
 private String body;
 @Enumerated(EnumType.STRING) private DeliveryStatus status=DeliveryStatus.PENDING;
 private int attempts;
 private Instant createdAt;
 private Instant nextAttemptAt;
 private Instant sentAt;
 private String lastError;
}
