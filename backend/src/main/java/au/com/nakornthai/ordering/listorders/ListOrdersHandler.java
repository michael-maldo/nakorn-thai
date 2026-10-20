package au.com.nakornthai.ordering.listorders;
import au.com.nakornthai.ordering.infrastructure.*;
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
    public StaffOrderResponse detail(UUID id,boolean kitchen){
        var order=orders.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if(kitchen&&!List.of("ACCEPTED","PREPARING","READY").contains(order.getStatus()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return mapper.mapStaff(order,!kitchen);
    }
    @Transactional(readOnly=true)
    public List<StaffOrderResponse> handle(boolean kitchen, boolean history) {
        var page = PageRequest.of(0,200);
        var result = history && !kitchen ? orders.findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                List.of("COMPLETED","CANCELLED"),Instant.now().minusSeconds(86400),page) :
                orders.findByStatusInOrderByCreatedAtAsc(kitchen ? List.of("ACCEPTED","PREPARING","READY") :
                        List.of("NEW","ACCEPTED","PREPARING","READY"),page);
        return result.stream().map(o -> mapper.mapStaff(o,!kitchen)).toList();
    }
}
