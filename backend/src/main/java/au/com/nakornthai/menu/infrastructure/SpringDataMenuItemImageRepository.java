package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemImageRepository extends JpaRepository<MenuItemImageJpaEntity, java.util.UUID> {
}
