package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuCollectionRepository extends JpaRepository<MenuCollectionJpaEntity, java.util.UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select c from MenuCollectionJpaEntity c
        where c.slug = :slug and c.status = 'PUBLISHED'
        """)
    java.util.Optional<MenuCollectionJpaEntity> findVisibleBySlug(String slug);
    java.util.List<MenuCollectionJpaEntity> findByStatusOrderByDisplayOrderAscIdAsc(String status);
}
