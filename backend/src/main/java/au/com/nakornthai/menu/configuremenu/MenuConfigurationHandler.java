package au.com.nakornthai.menu.configuremenu;

import au.com.nakornthai.menu.infrastructure.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class MenuConfigurationHandler {
    private final EntityManager em;
    public record Resource(Object id, Long version, Object data) {}
    public record CollectionView(Resource collection, List<Resource> schedules, List<Resource> categories, List<Resource> memberships) {}
    public record GroupView(Resource group, List<Resource> options) {}

    @Transactional(readOnly=true)
    public List<CollectionView> collections() {
        return em.createQuery("from MenuCollectionJpaEntity order by displayOrder, id", MenuCollectionJpaEntity.class)
                .getResultList().stream().map(c -> new CollectionView(collectionView(c),
                        c.getSchedules().stream().sorted(Comparator.comparingInt(MenuCollectionScheduleJpaEntity::getDisplayOrder).thenComparing(MenuCollectionScheduleJpaEntity::getId)).map(this::scheduleView).toList(),
                        c.getCategories().stream().sorted(Comparator.comparingInt(MenuCollectionCategoryJpaEntity::getDisplayOrder).thenComparing(MenuCollectionCategoryJpaEntity::getId)).map(this::categoryView).toList(),
                        em.createQuery("from MenuCollectionItemJpaEntity where collection.id=:id order by displayOrder, menuItem.id", MenuCollectionItemJpaEntity.class)
                                .setParameter("id", c.getId()).getResultList().stream().map(this::membershipView).toList())).toList();
    }
    @Transactional(readOnly=true)
    public List<GroupView> groups() {
        return em.createQuery("from MenuOptionGroupJpaEntity order by code, id", MenuOptionGroupJpaEntity.class).getResultList()
                .stream().map(g -> new GroupView(groupView(g), g.getOptions().stream()
                        .sorted(Comparator.comparingInt(MenuOptionJpaEntity::getDisplayOrder).thenComparing(MenuOptionJpaEntity::getId)).map(this::optionView).toList())).toList();
    }
    @Transactional(readOnly=true)
    public List<Resource> assignments(UUID itemId) {
        return required(MenuItemJpaEntity.class, itemId).getOptionGroups().stream()
                .sorted(Comparator.comparingInt(MenuItemOptionGroupJpaEntity::getDisplayOrder).thenComparing(a -> a.getOptionGroup().getId()))
                .map(this::assignmentView).toList();
    }
    public Resource saveCollection(UUID id, MenuConfigurationRequest.Collection r) {
        MenuCatalogLock.write(em);
        try { ZoneId.of(r.timezone()); } catch (DateTimeException e) { throw bad("Invalid collection timezone"); }
        if (r.startsAt()!=null && r.endsAt()!=null && !r.startsAt().isBefore(r.endsAt())) throw bad("Collection end must follow start");
        var c = id == null ? new MenuCollectionJpaEntity() : required(MenuCollectionJpaEntity.class, id);
        check(c, r.version(), id == null);
        c.setName(r.name()); c.setSlug(r.slug()); c.setDescription(r.description()); c.setStatus(r.status());
        c.setActive(r.active()); c.setTimezone(r.timezone()); c.setStartsAt(r.startsAt()); c.setEndsAt(r.endsAt()); c.setDisplayOrder(r.displayOrder());
        if (id == null) em.persist(c); em.flush(); return collectionView(c);
    }
    public void archiveCollection(UUID id, Long version) {
        MenuCatalogLock.write(em); var c=required(MenuCollectionJpaEntity.class,id); check(c,version,false); c.setStatus("ARCHIVED");
    }
    public Resource saveSchedule(UUID collectionId, UUID id, MenuConfigurationRequest.Schedule r) {
        MenuCatalogLock.write(em); var c=required(MenuCollectionJpaEntity.class,collectionId);
        boolean weekly="WEEKLY".equals(r.ruleType());
        if (weekly ? r.dayOfWeek()==null || r.specificDate()!=null : r.specificDate()==null || r.dayOfWeek()!=null) throw bad("Invalid schedule date shape");
        if ((r.startTime()==null)!=(r.endTime()==null) || r.startTime()!=null && r.startTime().equals(r.endTime())) throw bad("Supply distinct start and end times, or neither");
        var s=id==null?new MenuCollectionScheduleJpaEntity():required(MenuCollectionScheduleJpaEntity.class,id);
        if(id!=null) belongs(s.getCollection().getId(),collectionId); check(s,r.version(),id==null);
        s.setCollection(c); s.setRuleType(r.ruleType()); s.setDayOfWeek(r.dayOfWeek()); s.setSpecificDate(r.specificDate());
        s.setStartTime(r.startTime()); s.setEndTime(r.endTime()); s.setActive(r.active()); s.setDisplayOrder(r.displayOrder());
        if(id==null)em.persist(s); em.flush(); return scheduleView(s);
    }
    public void deleteSchedule(UUID collectionId, UUID id, Long version) {
        MenuCatalogLock.write(em); var s=required(MenuCollectionScheduleJpaEntity.class,id); belongs(s.getCollection().getId(),collectionId); check(s,version,false); em.remove(s);
    }
    public Resource saveCategory(UUID collectionId, UUID id, MenuConfigurationRequest.Category r) {
        MenuCatalogLock.write(em); var c=required(MenuCollectionJpaEntity.class,collectionId);
        var category=required(MenuCategoryJpaEntity.class,r.categoryId());
        var placement=id==null?new MenuCollectionCategoryJpaEntity():required(MenuCollectionCategoryJpaEntity.class,id);
        if(id!=null)belongs(placement.getCollection().getId(),collectionId); check(placement,r.version(),id==null);
        placement.setCollection(c); placement.setCategory(category); placement.setDisplayOrder(r.displayOrder());
        if(id==null)em.persist(placement); em.flush(); return categoryView(placement);
    }
    public void deleteCategory(UUID collectionId, UUID id, Long version) {
        MenuCatalogLock.write(em); var c=required(MenuCollectionCategoryJpaEntity.class,id); belongs(c.getCollection().getId(),collectionId); check(c,version,false); em.remove(c);
    }
    public Resource saveMembership(UUID collectionId, UUID itemId, MenuConfigurationRequest.Membership r) {
        MenuCatalogLock.write(em); var c=required(MenuCollectionJpaEntity.class,collectionId); var item=required(MenuItemJpaEntity.class,itemId);
        var m=em.find(MenuCollectionItemJpaEntity.class,new MenuAssociationId(collectionId,itemId)); boolean fresh=m==null;
        if(fresh)m=new MenuCollectionItemJpaEntity(); check(m,r.version(),fresh);
        var category=r.collectionCategoryId()==null?null:required(MenuCollectionCategoryJpaEntity.class,r.collectionCategoryId());
        if(category!=null)belongs(category.getCollection().getId(),collectionId);
        m.setCollection(c); m.setMenuItem(item); m.setCollectionCategory(category); m.setPriceOverrideMinor(r.priceOverrideMinor()); m.setDisplayOrder(r.displayOrder());
        // Existing item CRUD checks this version before replacing collection membership IDs.
        em.lock(item,LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        if(fresh)em.persist(m); em.flush(); return membershipView(m);
    }
    public void deleteMembership(UUID collectionId, UUID itemId, Long version) {
        MenuCatalogLock.write(em); var m=required(MenuCollectionItemJpaEntity.class,new MenuAssociationId(collectionId,itemId)); check(m,version,false);
        em.lock(m.getMenuItem(),LockModeType.PESSIMISTIC_FORCE_INCREMENT); em.remove(m);
    }
    public Resource saveGroup(UUID id, MenuConfigurationRequest.Group r) {
        MenuCatalogLock.write(em); var g=id==null?new MenuOptionGroupJpaEntity():required(MenuOptionGroupJpaEntity.class,id); check(g,r.version(),id==null);
        if("SINGLE".equals(r.selectionType()) && id!=null && em.createQuery("select count(a) from MenuItemOptionGroupJpaEntity a where a.optionGroup.id=:id and (a.minSelections>1 or a.maxSelections>1)",Long.class).setParameter("id",id).getSingleResult()>0)
            throw bad("Adjust assignments before changing this group to SINGLE");
        g.setCode(r.code()); g.setName(r.name()); g.setSelectionType(r.selectionType()); g.setActive(r.active());
        if(id==null)em.persist(g); em.flush(); return groupView(g);
    }
    public void deactivateGroup(UUID id, Long version) {
        MenuCatalogLock.write(em); var g=required(MenuOptionGroupJpaEntity.class,id); check(g,version,false); g.setActive(false);
    }
    public Resource saveOption(UUID groupId, UUID id, MenuConfigurationRequest.Option r) {
        MenuCatalogLock.write(em); var g=required(MenuOptionGroupJpaEntity.class,groupId);
        var o=id==null?new MenuOptionJpaEntity():required(MenuOptionJpaEntity.class,id);
        if(id!=null)belongs(o.getOptionGroup().getId(),groupId); check(o,r.version(),id==null);
        o.setOptionGroup(g); o.setCode(r.code()); o.setName(r.name()); o.setPriceDeltaMinor(r.priceDeltaMinor()); o.setCurrency("AUD"); o.setActive(r.active()); o.setDisplayOrder(r.displayOrder());
        if(id==null)em.persist(o); em.flush(); return optionView(o);
    }
    public void deactivateOption(UUID groupId, UUID id, Long version) {
        MenuCatalogLock.write(em); var o=required(MenuOptionJpaEntity.class,id); belongs(o.getOptionGroup().getId(),groupId); check(o,version,false); o.setActive(false);
    }
    public Resource saveAssignment(UUID itemId, UUID groupId, MenuConfigurationRequest.Assignment r) {
        MenuCatalogLock.write(em); var item=required(MenuItemJpaEntity.class,itemId); var g=required(MenuOptionGroupJpaEntity.class,groupId);
        if(r.maxSelections()<r.minSelections() || "SINGLE".equals(g.getSelectionType()) && (r.minSelections()>1 || r.maxSelections()!=1)) throw bad("Invalid option selection limits");
        var a=em.find(MenuItemOptionGroupJpaEntity.class,new MenuAssociationId(itemId,groupId)); boolean fresh=a==null;
        if(fresh)a=new MenuItemOptionGroupJpaEntity(); check(a,r.version(),fresh);
        a.setMenuItem(item); a.setOptionGroup(g); a.setMinSelections(r.minSelections()); a.setMaxSelections(r.maxSelections()); a.setDisplayOrder(r.displayOrder());
        if(fresh)em.persist(a); em.flush(); return assignmentView(a);
    }
    public void deleteAssignment(UUID itemId, UUID groupId, Long version) {
        MenuCatalogLock.write(em); var a=required(MenuItemOptionGroupJpaEntity.class,new MenuAssociationId(itemId,groupId)); check(a,version,false); em.remove(a);
    }
    private <T> T required(Class<T> type, Object id) {
        var value=em.find(type,id); if(value==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu resource not found"); return value;
    }
    private void check(MenuAuditJpaEntity entity, Long version, boolean fresh) {
        if(fresh) { if(version!=null)throw bad("New resource must not have a version"); }
        else {
            if(version==null || version<0)throw bad("Version is required");
            if(!version.equals(entity.getVersion()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Menu resource changed; reload before saving");
        }
    }
    private static void belongs(UUID actual,UUID expected) { if(!actual.equals(expected))throw bad("Resource belongs to a different parent"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private Resource collectionView(MenuCollectionJpaEntity c) { return new Resource(c.getId(),c.getVersion(),new MenuConfigurationRequest.Collection(c.getName(),c.getSlug(),c.getDescription(),c.getStatus(),c.isActive(),c.getTimezone(),c.getStartsAt(),c.getEndsAt(),c.getDisplayOrder(),c.getVersion())); }
    private Resource scheduleView(MenuCollectionScheduleJpaEntity s) { return new Resource(s.getId(),s.getVersion(),new MenuConfigurationRequest.Schedule(s.getRuleType(),s.getDayOfWeek(),s.getSpecificDate(),s.getStartTime(),s.getEndTime(),s.isActive(),s.getDisplayOrder(),s.getVersion())); }
    private Resource categoryView(MenuCollectionCategoryJpaEntity c) { return new Resource(c.getId(),c.getVersion(),new MenuConfigurationRequest.Category(c.getCategory().getId(),c.getDisplayOrder(),c.getVersion())); }
    private Resource membershipView(MenuCollectionItemJpaEntity m) { return new Resource(m.getMenuItem().getId(),m.getVersion(),new MenuConfigurationRequest.Membership(m.getCollectionCategory()==null?null:m.getCollectionCategory().getId(),m.getPriceOverrideMinor(),m.getDisplayOrder(),m.getVersion())); }
    private Resource groupView(MenuOptionGroupJpaEntity g) { return new Resource(g.getId(),g.getVersion(),new MenuConfigurationRequest.Group(g.getCode(),g.getName(),g.getSelectionType(),g.isActive(),g.getVersion())); }
    private Resource optionView(MenuOptionJpaEntity o) { return new Resource(o.getId(),o.getVersion(),new MenuConfigurationRequest.Option(o.getCode(),o.getName(),o.getPriceDeltaMinor(),o.isActive(),o.getDisplayOrder(),o.getVersion())); }
    private Resource assignmentView(MenuItemOptionGroupJpaEntity a) { return new Resource(a.getOptionGroup().getId(),a.getVersion(),new MenuConfigurationRequest.Assignment(a.getMinSelections(),a.getMaxSelections(),a.getDisplayOrder(),a.getVersion())); }
}
