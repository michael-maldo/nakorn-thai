package au.com.nakornthai.ordering.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="restaurant_order_item_option") @Getter @Setter
public class OrderItemOptionJpaEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_item_id") private OrderItemJpaEntity orderItem;
    @Column(nullable=false) private UUID optionId;
    @Column(nullable=false, length=100) private String optionGroupName;
    @Column(nullable=false, length=100) private String optionName;
    @Column(nullable=false) private long priceDeltaMinor;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false) private Instant createdAt;
}
