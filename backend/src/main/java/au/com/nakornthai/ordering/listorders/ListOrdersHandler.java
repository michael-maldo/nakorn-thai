package au.com.nakornthai.ordering.listorders;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.ordering.createorder.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class ListOrdersHandler {
    private final SpringDataOrderRepository orders;
    private final OrderMapper mapper;
    @Transactional(readOnly=true)
    public List<CreateOrderResponse> handle(boolean kitchen, boolean history) {
        var page = PageRequest.of(0,200);
        var result = history && !kitchen ? orders.findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                List.of("COMPLETED","CANCELLED"),Instant.now().minusSeconds(86400),page) :
                orders.findByStatusInOrderByCreatedAtAsc(kitchen ? List.of("ACCEPTED","PREPARING","READY") :
                        List.of("NEW","ACCEPTED","PREPARING","READY"),page);
        return result.stream().map(o -> mapper.map(o,!kitchen)).toList();
    }
}
