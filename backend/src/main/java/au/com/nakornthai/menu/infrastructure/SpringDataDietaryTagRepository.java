package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDietaryTagRepository extends JpaRepository<DietaryTagJpaEntity, java.util.UUID> {
}
