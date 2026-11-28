package au.com.nakornthai.restaurant.holidays;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HolidayDefinitionRepository extends JpaRepository<HolidayDefinition, UUID> {
    List<HolidayDefinition> findAllByOrderByStartDateAscNameAscIdAsc();
}
