package au.com.nakornthai.restaurant.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.time.Instant;

@Getter @MappedSuperclass
public abstract class RestaurantAuditJpaEntity {
    @Version @Column(nullable = false) private Long version;
    @Generated(event = EventType.INSERT)
    @Column(nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(nullable = false, insertable = false, updatable = false) private Instant updatedAt;
}
