package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuItemRepository extends JpaRepository<MenuItemJpaEntity, java.util.UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select i from MenuCollectionItemJpaEntity membership
        join membership.menuItem i join fetch i.category category
        where membership.collection.id = :collectionId
          and i.status = 'PUBLISHED' and category.active = true
        order by membership.displayOrder, i.id
        """)
    java.util.List<MenuItemJpaEntity> findPublishedByCollectionId(java.util.UUID collectionId);
}
