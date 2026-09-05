package au.com.nakornthai.menu.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMenuCollectionRepository extends JpaRepository<MenuCollectionJpaEntity, java.util.UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select c from MenuCollectionJpaEntity c
        where c.slug = :slug and c.status = 'PUBLISHED'
          and (c.startsAt is null or c.startsAt <= CURRENT_TIMESTAMP)
          and (c.endsAt is null or CURRENT_TIMESTAMP < c.endsAt)
        """)
    java.util.Optional<MenuCollectionJpaEntity> findVisibleBySlug(String slug);
}
