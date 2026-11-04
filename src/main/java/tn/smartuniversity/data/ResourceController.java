
package tn.smartuniversity.data;

import java.time.Instant;
import java.util.*;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final MongoTemplate db;

    private static final Set<String> ALLOWED = Set.of(
            "courses",
            "events",
            "clubs",
            "document-requests",
            "sessions",
            "attendance",
            "grades",
            "announcements",
            "study-spaces",
            "comments",
            "enrollments",
            "course-materials"
    );

    private static final Set<String> ADMIN = Set.of(
            "document-requests",
            "users",
            "events"
    );

    public ResourceController(MongoTemplate db) {
        this.db = db;
    }

    private String collection(String resource) {
        if (!ALLOWED.contains(resource)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Unknown resource"
            );
        }
        return resource;
    }

    private Map<String, Object> safe(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>(map);

        Object id = result.get("_id");

        if (id != null) {
            result.put("id", id.toString());
        }

        result.remove("_id");

        return result;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/{resource}")
    public List<Map<String, Object>> list(
            @PathVariable String resource,
            Authentication authentication) {

        String collection = collection(resource);
        Query query = new Query();

        if (collection.equals("document-requests") && !isAdmin(authentication)) {
            query.addCriteria(
                    Criteria.where("studentEmail").is(authentication.getName())
            );
        }

        if (collection.equals("grades") || collection.equals("attendance")) {
            if (!isStaff(authentication)) {
                query.addCriteria(
                        Criteria.where("studentEmail").is(authentication.getName())
                );
            }
        }

        List<Map> documents = db.find(query, Map.class, collection);

        return documents.stream()
                .map(document -> safe((Map<String, Object>) document))
                .toList();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/{resource}")
    public ResponseEntity<?> create(
            @PathVariable String resource,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        String collection = collection(resource);

        if (collection.equals("document-requests")) {
            body.put("studentEmail", authentication.getName());
            body.put("status", "PENDING");
        }

        if (collection.equals("grades")
                || collection.equals("attendance")
                || collection.equals("sessions")
                || collection.equals("courses")) {

            if (!isStaff(authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Professor permission required"));
            }
        }

        if (collection.equals("events") || collection.equals("clubs")) {
            body.putIfAbsent("createdBy", authentication.getName());
        }

        body.put("createdAt", Instant.now().toString());
        body.put("updatedAt", Instant.now().toString());

        Map saved = db.insert(body, collection);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(safe((Map<String, Object>) saved));
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{resource}/{id}")
    public Map<String, Object> update(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        String collection = collection(resource);

        if (ADMIN.contains(collection) && !isAdmin(authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        if ((collection.equals("courses")
                || collection.equals("sessions")
                || collection.equals("grades")
                || collection.equals("attendance"))
                && !isStaff(authentication)) {

            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        body.remove("_id");
        body.put("updatedAt", Instant.now().toString());

        db.updateFirst(
                Query.query(Criteria.where("_id").is(toId(id))),
                buildUpdate(body),
                collection
        );

        Map existing = db.findById(toId(id), Map.class, collection);

        if (existing == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND
            );
        }

        return safe((Map<String, Object>) existing);
    }

    @SuppressWarnings("unchecked")
    @PatchMapping("/{resource}/{id}/status")
    public Map<String, Object> status(
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        String collection = collection(resource);

        if (!isAdmin(authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        Map existing = db.findById(toId(id), Map.class, collection);

        if (existing == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND
            );
        }

        existing.put("status", body.get("status"));
        existing.put("reviewedBy", authentication.getName());
        existing.put("updatedAt", Instant.now().toString());

        db.save(existing, collection);

        return safe((Map<String, Object>) existing);
    }

    @DeleteMapping("/{resource}/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String resource,
            @PathVariable String id,
            Authentication authentication) {

        String collection = collection(resource);

        if (!isAdmin(authentication) && !isStaff(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        db.remove(
                Query.query(Criteria.where("_id").is(toId(id))),
                collection
        );

        return ResponseEntity.noContent().build();
    }

    private Update buildUpdate(Map<String, Object> body) {
        Update update = new Update();
        body.forEach(update::set);
        return update;
    }

    private ObjectId toId(String id) {
        try {
            return new ObjectId(id);
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid id"
            );
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_DEPARTMENT_HEAD")
        );
    }

    private boolean isStaff(Authentication authentication) {
        return isAdmin(authentication)
                || authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_PROFESSOR")
        );
    }
}
