package au.com.nakornthai.restaurant.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RestaurantClosedException extends ResponseStatusException {
    public RestaurantClosedException() {
        super(HttpStatus.CONFLICT, "The restaurant is closed at the requested time");
    }
}
