package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemAllergenRepository extends JpaRepository<MenuItemAllergenJpaEntity, MenuAssociationId> {
}
