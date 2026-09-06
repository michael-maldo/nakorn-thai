package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.EntityManager;

/** All menu configuration writes and checkout use this transaction-scoped lock.
 * Readers can check out concurrently; writes cannot change a price/rule mid-checkout. */
public final class MenuCatalogLock {
    private MenuCatalogLock() {}
    public static void read(EntityManager em) { lock(em, "pg_advisory_xact_lock_shared"); }
    public static void write(EntityManager em) { lock(em, "pg_advisory_xact_lock"); }
    private static void lock(EntityManager em, String function) {
        em.createNativeQuery("SELECT " + function + "(193576483, 1)", Object.class).getSingleResult();
    }
}
