package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuCollectionItemRepository extends JpaRepository<MenuCollectionItemJpaEntity, MenuAssociationId> {
    @org.springframework.data.jpa.repository.Query("""
        select m from MenuCollectionItemJpaEntity m
        join fetch m.menuItem i join fetch i.category
        left join fetch m.collectionCategory cc left join fetch cc.category
        where m.collection.id = :collectionId and i.status = 'PUBLISHED'
        order by m.displayOrder, i.id
        """)
    java.util.List<MenuCollectionItemJpaEntity> findPublishedMemberships(java.util.UUID collectionId);
}
