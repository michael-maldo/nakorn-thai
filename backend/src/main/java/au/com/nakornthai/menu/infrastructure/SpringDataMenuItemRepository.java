package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemRepository extends JpaRepository<MenuItemJpaEntity, java.util.UUID> {
}
