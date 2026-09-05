package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemVariationAllergenRepository extends JpaRepository<MenuItemVariationAllergenJpaEntity, MenuAssociationId> {
}
