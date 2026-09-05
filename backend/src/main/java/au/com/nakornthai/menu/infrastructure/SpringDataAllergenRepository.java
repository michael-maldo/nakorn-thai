package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAllergenRepository extends JpaRepository<AllergenJpaEntity, java.util.UUID> {
}
