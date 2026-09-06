package au.com.nakornthai.payment.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="order_payment") @Getter @Setter
public class OrderPaymentJpaEntity {
 @Id private UUID orderId;
 private String method;
 private String providerOrderId;
 private String approvalUrl;
 private String status="PENDING";
 private String confirmationReference;
 private String confirmedBy;
 private Instant updatedAt=Instant.now();
}
