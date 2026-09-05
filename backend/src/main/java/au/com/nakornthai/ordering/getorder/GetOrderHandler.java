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
    @Transactional(readOnly=true)
    public CreateOrderResponse handle(UUID id, String token) {
        var order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (token == null || token.length()!=64 || !java.security.MessageDigest.isEqual(
                order.getTrackingHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                CreateOrderHandler.hash(token).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return mapper.map(order, false);
    }
}
