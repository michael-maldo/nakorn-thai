package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemVariationDietaryTagRepository extends JpaRepository<MenuItemVariationDietaryTagJpaEntity, MenuAssociationId> {
}
