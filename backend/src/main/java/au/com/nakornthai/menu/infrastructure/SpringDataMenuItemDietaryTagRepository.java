package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemDietaryTagRepository extends JpaRepository<MenuItemDietaryTagJpaEntity, MenuAssociationId> {
}
