package au.com.nakornthai.ordering.getorder;
import au.com.nakornthai.ordering.createorder.*;
import au.com.nakornthai.ordering.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class GetOrderHandler {
    private final SpringDataOrderRepository orders;
    private final OrderMapper mapper;
    private final OrderAccessService access;
    @Transactional(readOnly=true)
    public CreateOrderResponse handle(UUID id, String token) {
        var order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        access.require(order,token);
        return mapper.map(order, false);
    }
}
