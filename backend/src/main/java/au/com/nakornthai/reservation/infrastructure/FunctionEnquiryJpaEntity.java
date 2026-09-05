package au.com.nakornthai.reservation.infrastructure;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="function_enquiry") @Getter @Setter @NoArgsConstructor
public class FunctionEnquiryJpaEntity {
    @Id private UUID id;
    private String customerName;
    private String email;
    private String phone;
    private String eventType;
    private int guestCount;
    private LocalDate preferredDate;
    private String preferredTime;
    private String message;
    private String status="NEW";
    private LocalDate arrangedDate;
    private String staffNote="";
    private String updatedBy;
    private Instant createdAt=Instant.now();
    private Instant updatedAt=Instant.now();
    @Version private Long version;
}
