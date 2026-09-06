package au.com.nakornthai.ordering.infrastructure;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity @Table(name="restaurant_order_item") @Getter @Setter
public class OrderItemJpaEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id") private OrderJpaEntity order;
    @Column(nullable=false) private UUID variationId;
    @Column(nullable=false, length=150) private String dishName;
    @Column(nullable=false, length=100) private String variationName;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false) private long unitPriceMinor;
    @Column(nullable=false) private short snapshotVersion = 1;
    private UUID collectionId;
    @Column(length=150) private String collectionName;
    @Column(length=180) private String collectionSlug;
    private Long variationBasePriceMinor;
    private Long collectionPriceOverrideMinor;
    @OneToMany(mappedBy="orderItem", cascade=CascadeType.PERSIST)
    @org.hibernate.annotations.BatchSize(size=64)
    @OrderBy("id")
    private java.util.List<OrderItemOptionJpaEntity> selectedOptions = new java.util.ArrayList<>();
}
