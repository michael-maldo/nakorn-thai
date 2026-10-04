package au.com.nakornthai.restaurant.infrastructure;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="integration_configuration") @Getter @Setter @NoArgsConstructor
public class IntegrationConfigurationJpaEntity {
 @Id private String category;
 @JdbcTypeCode(SqlTypes.JSON) private Map<String,String> fields=new HashMap<>();
 @JdbcTypeCode(SqlTypes.JSON) private Map<String,String> secrets=new HashMap<>();
 @Version private Long version;
 private String validationStatus="NOT_TESTED";
 private Instant testedAt;
 private Instant updatedAt;
 private String updatedBy;
}
