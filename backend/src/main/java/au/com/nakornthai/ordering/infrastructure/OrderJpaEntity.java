package au.com.nakornthai.ordering.infrastructure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="restaurant_order") @Getter @Setter
public class OrderJpaEntity {
    @Id private UUID id;
    @Version private Long version;
    @Column(nullable=false, length=64) private String trackingHash;
    @Column(nullable=false, length=64) private String requestHash;
    @Column(nullable=false, length=100) private String customerName;
    @Column(nullable=false, length=30) private String phone;
    @Column(nullable=false, length=1000) private String notes;
    @Column(nullable=false, length=20) private String status = "NEW";
    @Column(nullable=false) private long totalMinor;
    @Column(nullable=false, length=3) private String currency = "AUD";
    @Column(nullable=false, length=20) private String fulfilment = "PICKUP";
    @Column(nullable=false, length=30) private String paymentMethod = "PAY_AT_RESTAURANT";
    private Instant paidAt;
    private Instant estimatedReadyAt;
    @Column(length=500) private String cancellationReason;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="order", cascade=CascadeType.PERSIST)
    private List<OrderItemJpaEntity> items = new ArrayList<>();
}
