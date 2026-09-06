package au.com.nakornthai.menu.configuremenu;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/staff/menu") @RequiredArgsConstructor
public class MenuConfigurationController {
    private final MenuConfigurationHandler handler;
    @GetMapping("/collections")
    public ResponseEntity<List<MenuConfigurationHandler.CollectionView>> collections() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.collections());
    }
    @GetMapping("/option-groups")
    public ResponseEntity<List<MenuConfigurationHandler.GroupView>> groups() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.groups());
    }
    @GetMapping("/items/{itemId}/option-groups")
    public ResponseEntity<List<MenuConfigurationHandler.Resource>> assignments(@PathVariable UUID itemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.assignments(itemId));
    }
    @PostMapping("/collections")
    public ResponseEntity<MenuConfigurationHandler.Resource> createCollection(@Valid @RequestBody MenuConfigurationRequest.Collection request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveCollection(null, request));
    }
    @PutMapping("/collections/{id}")
    public ResponseEntity<MenuConfigurationHandler.Resource> updateCollection(@PathVariable UUID id, @Valid @RequestBody MenuConfigurationRequest.Collection request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveCollection(id, request));
    }
    @DeleteMapping("/collections/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable UUID id, @RequestParam Long version) {
        handler.archiveCollection(id, version);
    }
    @PostMapping("/collections/{collectionId}/schedules")
    public ResponseEntity<MenuConfigurationHandler.Resource> createSchedule(@PathVariable UUID collectionId, @Valid @RequestBody MenuConfigurationRequest.Schedule request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveSchedule(collectionId, null, request));
    }
    @PutMapping("/collections/{collectionId}/schedules/{id}")
    public ResponseEntity<MenuConfigurationHandler.Resource> updateSchedule(@PathVariable UUID collectionId, @PathVariable UUID id, @Valid @RequestBody MenuConfigurationRequest.Schedule request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveSchedule(collectionId, id, request));
    }
    @DeleteMapping("/collections/{collectionId}/schedules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable UUID collectionId, @PathVariable UUID id, @RequestParam Long version) {
        handler.deleteSchedule(collectionId, id, version);
    }
    @PostMapping("/collections/{collectionId}/categories")
    public ResponseEntity<MenuConfigurationHandler.Resource> createCategory(@PathVariable UUID collectionId, @Valid @RequestBody MenuConfigurationRequest.Category request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveCategory(collectionId, null, request));
    }
    @PutMapping("/collections/{collectionId}/categories/{id}")
    public ResponseEntity<MenuConfigurationHandler.Resource> updateCategory(@PathVariable UUID collectionId, @PathVariable UUID id, @Valid @RequestBody MenuConfigurationRequest.Category request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveCategory(collectionId, id, request));
    }
    @DeleteMapping("/collections/{collectionId}/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID collectionId, @PathVariable UUID id, @RequestParam Long version) {
        handler.deleteCategory(collectionId, id, version);
    }
    @PostMapping("/option-groups")
    public ResponseEntity<MenuConfigurationHandler.Resource> createGroup(@Valid @RequestBody MenuConfigurationRequest.Group request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveGroup(null, request));
    }
    @PutMapping("/option-groups/{id}")
    public ResponseEntity<MenuConfigurationHandler.Resource> updateGroup(@PathVariable UUID id, @Valid @RequestBody MenuConfigurationRequest.Group request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveGroup(id, request));
    }
    @DeleteMapping("/option-groups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable UUID id, @RequestParam Long version) {
        handler.deactivateGroup(id, version);
    }
    @PostMapping("/option-groups/{groupId}/options")
    public ResponseEntity<MenuConfigurationHandler.Resource> createOption(@PathVariable UUID groupId, @Valid @RequestBody MenuConfigurationRequest.Option request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveOption(groupId, null, request));
    }
    @PutMapping("/option-groups/{groupId}/options/{id}")
    public ResponseEntity<MenuConfigurationHandler.Resource> updateOption(@PathVariable UUID groupId, @PathVariable UUID id, @Valid @RequestBody MenuConfigurationRequest.Option request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveOption(groupId, id, request));
    }
    @DeleteMapping("/option-groups/{groupId}/options/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOption(@PathVariable UUID groupId, @PathVariable UUID id, @RequestParam Long version) {
        handler.deactivateOption(groupId, id, version);
    }
    @PutMapping("/collections/{collectionId}/items/{itemId}")
    public ResponseEntity<MenuConfigurationHandler.Resource> saveMembership(@PathVariable UUID collectionId, @PathVariable UUID itemId, @Valid @RequestBody MenuConfigurationRequest.Membership request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveMembership(collectionId, itemId, request));
    }
    @DeleteMapping("/collections/{collectionId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMembership(@PathVariable UUID collectionId, @PathVariable UUID itemId, @RequestParam Long version) {
        handler.deleteMembership(collectionId, itemId, version);
    }
    @PutMapping("/items/{itemId}/option-groups/{groupId}")
    public ResponseEntity<MenuConfigurationHandler.Resource> saveAssignment(@PathVariable UUID itemId, @PathVariable UUID groupId, @Valid @RequestBody MenuConfigurationRequest.Assignment request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.saveAssignment(itemId, groupId, request));
    }
    @DeleteMapping("/items/{itemId}/option-groups/{groupId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable UUID itemId, @PathVariable UUID groupId, @RequestParam Long version) {
        handler.deleteAssignment(itemId, groupId, version);
    }
}
