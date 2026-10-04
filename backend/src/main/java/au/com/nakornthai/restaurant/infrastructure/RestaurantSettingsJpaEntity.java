package au.com.nakornthai.restaurant.infrastructure;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "restaurant_settings") @Getter @Setter @NoArgsConstructor
public class RestaurantSettingsJpaEntity extends RestaurantAuditJpaEntity {
    @Id private Short id;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.Map<String,String> operationalConfiguration=new java.util.HashMap<>();
    @Column(nullable = false) private boolean orderingPaused;
    @Column(length = 300) private String orderingPauseMessage;
    @Column(nullable = false, length = 64) private String timezone;
}
