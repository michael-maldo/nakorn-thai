package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemVariationRepository extends JpaRepository<MenuItemVariationJpaEntity, java.util.UUID> {
}
