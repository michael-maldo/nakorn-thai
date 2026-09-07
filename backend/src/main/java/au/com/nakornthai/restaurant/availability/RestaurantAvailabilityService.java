package au.com.nakornthai.restaurant.availability;

import au.com.nakornthai.restaurant.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class RestaurantAvailabilityService {
    private final RestaurantRepository restaurant;
    @Transactional
    public boolean isOpen(Instant instant) { return schedule().isOpen(instant); }
    @Transactional
    public RestaurantSchedule schedule() { return restaurant.schedule(); }
}
