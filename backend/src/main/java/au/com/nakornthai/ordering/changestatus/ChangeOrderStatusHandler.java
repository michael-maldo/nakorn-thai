package au.com.nakornthai.ordering.changestatus;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.ordering.createorder.CreateOrderResponse;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class ChangeOrderStatusHandler {
    private final EntityManager em;
    private final OrderMapper mapper;
    @Transactional
    public CreateOrderResponse handle(UUID id, ChangeOrderStatusCommand command, Authentication actor) {
        var roles = actor.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        boolean front = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_FOH");
        boolean kitchen = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_BOH");
        boolean kitchenAction = Set.of("PREPARING","READY").contains(command.status());
        if (kitchenAction ? !kitchen : !front) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var order = em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if (order == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (order.getVersion() != command.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Order changed; reload the queue");
        var next = switch(order.getStatus()) {
            case "NEW" -> Set.of("ACCEPTED","CANCELLED");
            case "ACCEPTED" -> Set.of("PREPARING","CANCELLED");
            case "PREPARING" -> Set.of("READY","CANCELLED");
            case "READY" -> Set.of("COMPLETED","CANCELLED");
            default -> Set.<String>of();
        };
        if (!next.contains(command.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid order transition");
        if ("ACCEPTED".equals(command.status())) {
            if (command.pickupMinutes()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter estimated pickup minutes");
            order.setEstimatedReadyAt(Instant.now().plusSeconds(command.pickupMinutes()*60L));
        }
        if ("CANCELLED".equals(command.status())) {
            if (command.reason()==null || command.reason().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a cancellation reason");
            order.setCancellationReason(command.reason().trim());
        }
        if ("COMPLETED".equals(command.status())) {
            if (!command.paymentCollected()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirm payment was collected");
            order.setPaidAt(Instant.now());
        }
        order.setStatus(command.status()); order.setUpdatedAt(Instant.now());
        var event = new OrderEventJpaEntity(); event.setOrderId(id); event.setStatus(command.status());
        event.setActor(actor.getName()); event.setCreatedAt(order.getUpdatedAt()); em.persist(event); em.flush();
        return mapper.map(order, front);
    }
}
