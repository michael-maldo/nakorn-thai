package au.com.nakornthai.restaurant.infrastructure;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "restaurant_settings") @Getter @Setter @NoArgsConstructor
public class RestaurantSettingsJpaEntity extends RestaurantAuditJpaEntity {
    @Id private Short id;
    @Column(nullable = false) private boolean orderingPaused;
    @Column(length = 300) private String orderingPauseMessage;
    @Column(nullable = false, length = 64) private String timezone;
}
