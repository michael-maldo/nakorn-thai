package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuCollectionItemRepository extends JpaRepository<MenuCollectionItemJpaEntity, MenuAssociationId> {
}
