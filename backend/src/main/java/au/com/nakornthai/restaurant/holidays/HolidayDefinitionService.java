package au.com.nakornthai.restaurant.holidays;

import au.com.nakornthai.restaurant.infrastructure.JpaRestaurantRepository;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HolidayDefinitionService {
    private final HolidayDefinitionRepository holidays;
    private final JpaRestaurantRepository restaurant;
    private final au.com.nakornthai.restaurant.configuration.ConfigurationHandler audit;

    private void audit(String action, Set<String> fields) {
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        audit.recordChange("SETTINGS", action, fields, actor == null ? "SYSTEM" : actor.getName());
    }

    @Transactional(readOnly = true)
    public List<HolidayDefinitionResponse> list() {
        return holidays.findAllByOrderByStartDateAscNameAscIdAsc().stream().map(HolidayDefinitionResponse::from).toList();
    }

    @Transactional
    public HolidayDefinitionResponse save(UUID id, HolidayDefinitionRequest request) {
        if (!request.recurrence().supports(request.startDate(), request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose an ordered date range between years 1 and 9999. Annual ranges must be shorter than one calendar cycle.");
        }
        restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        var row = id == null ? new HolidayDefinition() : findVersion(id, request.version());
        boolean active = request.active() == null ? id == null || row.isActive() : request.active();
        String notes = request.notes() == null || request.notes().isEmpty() ? null : request.notes();
        row.update(request.name(), request.startDate(), request.endDate(), request.recurrence(), notes, active);
        try {
            var response = HolidayDefinitionResponse.from(holidays.saveAndFlush(row));
            audit(id == null ? "HOLIDAY_DEFINITION_CREATED" : "HOLIDAY_DEFINITION_UPDATED",
                    Set.of("name", "startDate", "endDate", "recurrence", "notes", "active"));
            return response;
        } catch (DataIntegrityViolationException failure) {
            if (failure.getCause() instanceof ConstraintViolationException constraint
                    && ("holiday_one_time_identity".equals(constraint.getConstraintName())
                    || "holiday_annual_identity".equals(constraint.getConstraintName()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A holiday with this name and date range already exists.");
            }
            throw failure;
        }
    }

    @Transactional
    public void remove(UUID id, Long version) {
        restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        holidays.delete(findVersion(id, version));
        holidays.flush();
        audit("HOLIDAY_DEFINITION_DELETED", Set.of("holidayDefinition"));
    }

    private HolidayDefinition findVersion(UUID id, Long version) {
        var row = holidays.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday definition was not found."));
        if (!row.getVersion().equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Holiday definition changed. Reload before saving.");
        }
        return row;
    }
}
