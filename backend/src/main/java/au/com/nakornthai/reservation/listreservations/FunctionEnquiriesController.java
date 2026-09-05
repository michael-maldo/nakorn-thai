package au.com.nakornthai.reservation.listreservations;

import au.com.nakornthai.reservation.infrastructure.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/staff/functions") @RequiredArgsConstructor
public class FunctionEnquiriesController {
    private static final Set<String> STATUSES=Set.of("NEW","CONTACTED","CONFIRMED","DECLINED","CANCELLED","COMPLETED");
    private final SpringDataFunctionEnquiryRepository enquiries;
    private final EntityManager em;
    public record Queue(List<FunctionEnquiryJpaEntity> items,int page,boolean hasNext) {}

    @GetMapping
    ResponseEntity<Queue> list(@RequestParam(defaultValue="NEW") String status,@RequestParam(defaultValue="0") int page) {
        if(page<0 || page>10000 || (!status.equals("ALL") && !STATUSES.contains(status)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid enquiry status and page");
        var paging=PageRequest.of(page,25,Sort.by("createdAt","id"));
        var result=status.equals("ALL")?enquiries.findAll(paging):enquiries.findByStatus(status,paging);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Queue(result.getContent(),page,result.hasNext()));
    }
    public record Update(@NotNull @Min(0) Long version,@NotNull String status,
        LocalDate arrangedDate,@NotNull @Size(max=2000) String staffNote) {}

    @PatchMapping("/{id}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable UUID id,@Valid @RequestBody Update request,Authentication auth) {
        var e=em.find(FunctionEnquiryJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(e==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Enquiry not found");
        if(!e.getVersion().equals(request.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Enquiry changed; refresh before saving");
        var allowed=switch(e.getStatus()) {
            case "NEW" -> Set.of("NEW","CONTACTED","CONFIRMED","DECLINED","CANCELLED");
            case "CONTACTED" -> Set.of("CONTACTED","CONFIRMED","DECLINED","CANCELLED");
            case "CONFIRMED" -> Set.of("CONFIRMED","COMPLETED","CANCELLED");
            default -> Set.of(e.getStatus());
        };
        if(!allowed.contains(request.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid enquiry status change");
        if(Set.of("CONFIRMED","COMPLETED").contains(request.status()) && request.arrangedDate()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the agreed event date before confirming");
        if(request.status().equals("CONFIRMED") && !e.getStatus().equals("CONFIRMED") && request.arrangedDate().isBefore(LocalDate.now(ZoneId.of("Australia/Melbourne"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The agreed event date cannot be in the past");
        e.setStatus(request.status());e.setArrangedDate(request.arrangedDate());e.setStaffNote(request.staffNote().trim());
        e.setUpdatedBy(auth.getName());e.setUpdatedAt(Instant.now());
    }
}
