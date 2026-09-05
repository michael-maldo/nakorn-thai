package au.com.nakornthai.reservation.createreservation;

import au.com.nakornthai.reservation.infrastructure.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CreateFunctionEnquiryHandler {
    private final SpringDataFunctionEnquiryRepository enquiries;
    private final EntityManager em;

    @Transactional
    public Map<String,Object> handle(CreateFunctionEnquiryRequest request) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class)
            .setParameter("key",request.requestId().getMostSignificantBits()).getSingleResult();
        var existing=enquiries.findById(request.requestId());
        if(existing.isPresent()) {
            var e=existing.get();
            if(!e.getCustomerName().equals(request.customerName().trim()) || !e.getEmail().equals(request.email().trim())
                || !e.getPhone().equals(request.phone().trim()) || !e.getEventType().equals(request.eventType().trim())
                || e.getGuestCount()!=request.guestCount() || !Objects.equals(e.getPreferredDate(),request.preferredDate())
                || !e.getPreferredTime().equals(request.preferredTime().trim()) || !e.getMessage().equals(request.message().trim()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Enquiry reference already used; start a new enquiry");
            return receipt(e);
        }
        var today=LocalDate.now(ZoneId.of("Australia/Melbourne"));
        if(request.preferredDate()!=null && (request.preferredDate().isBefore(today) || request.preferredDate().isAfter(today.plusYears(2))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a date within the next two years or leave it open");
        var e=new FunctionEnquiryJpaEntity();
        e.setId(request.requestId());e.setCustomerName(request.customerName().trim());e.setEmail(request.email().trim());
        e.setPhone(request.phone().trim());e.setEventType(request.eventType().trim());e.setGuestCount(request.guestCount());
        e.setPreferredDate(request.preferredDate());e.setPreferredTime(request.preferredTime().trim());e.setMessage(request.message().trim());
        enquiries.saveAndFlush(e);
        return receipt(e);
    }

    private Map<String,Object> receipt(FunctionEnquiryJpaEntity enquiry) {
        return Map.of("reference",enquiry.getId(),"message","Your venue enquiry has been received. Our team will contact you to discuss availability and arrangements. This is not a confirmed reservation.");
    }
}
