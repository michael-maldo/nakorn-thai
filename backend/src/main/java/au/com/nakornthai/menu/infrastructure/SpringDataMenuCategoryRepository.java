package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuCategoryRepository extends JpaRepository<MenuCategoryJpaEntity, java.util.UUID> {
}
