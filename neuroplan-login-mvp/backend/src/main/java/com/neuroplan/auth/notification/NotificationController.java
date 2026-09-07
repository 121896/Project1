package com.neuroplan.auth.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.neuroplan.auth.auth.CurrentUserService;
import com.neuroplan.auth.error.ApiException;
import com.neuroplan.auth.user.UserRecord;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private static final Set<String> TARGET_PAGES = Set.of("dashboard", "plan", "quiz", "wrong", "history");
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public NotificationController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<NotificationResponse> notifications(HttpServletRequest request) {
        UserRecord user = currentUserService.require(request);
        return jdbcTemplate.query("""
                SELECT id, notification_type, source_key, title, message, target_page,
                       is_read, created_at, read_at
                  FROM user_notifications
                 WHERE user_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 50
                """, (rs, rowNum) -> new NotificationResponse(
                rs.getLong("id"), rs.getString("notification_type"), rs.getString("source_key"),
                rs.getString("title"), rs.getString("message"), rs.getString("target_page"),
                rs.getBoolean("is_read"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toLocalDateTime()
        ), user.id());
    }

    @PostMapping
    public NotificationResponse create(@Valid @RequestBody NotificationRequest body, HttpServletRequest request) {
        UserRecord user = currentUserService.require(request);
        String page = normalizePage(body.targetPage());
        String type = body.type() == null || body.type().isBlank() ? "GENERAL" : body.type().trim().toUpperCase();
        jdbcTemplate.update("""
                INSERT INTO user_notifications (
                    user_id, notification_type, source_key, title, message, target_page,
                    is_read, created_at, read_at
                ) VALUES (?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP(6), NULL)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title), message = VALUES(message), target_page = VALUES(target_page),
                    is_read = FALSE, created_at = CURRENT_TIMESTAMP(6), read_at = NULL
                """, user.id(), type, blankToNull(body.sourceKey()), body.title().trim(), body.message().trim(), page);
        return jdbcTemplate.query("""
                SELECT id, notification_type, source_key, title, message, target_page,
                       is_read, created_at, read_at
                  FROM user_notifications
                 WHERE user_id = ? AND ((source_key IS NULL AND ? IS NULL) OR source_key = ?)
                 ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> new NotificationResponse(
                rs.getLong("id"), rs.getString("notification_type"), rs.getString("source_key"),
                rs.getString("title"), rs.getString("message"), rs.getString("target_page"),
                rs.getBoolean("is_read"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toLocalDateTime()
        ), user.id(), blankToNull(body.sourceKey()), blankToNull(body.sourceKey())).stream().findFirst().orElseThrow();
    }

    @PatchMapping("/{notificationId}/read")
    public void markRead(@PathVariable long notificationId, HttpServletRequest request) {
        UserRecord user = currentUserService.require(request);
        int updated = jdbcTemplate.update("""
                UPDATE user_notifications
                   SET is_read = TRUE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
                 WHERE id = ? AND user_id = ?
                """, notificationId, user.id());
        if (updated == 0) throw new ApiException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");
    }

    @PatchMapping("/read-all")
    public void markAllRead(HttpServletRequest request) {
        UserRecord user = currentUserService.require(request);
        jdbcTemplate.update("""
                UPDATE user_notifications
                   SET is_read = TRUE, read_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND is_read = FALSE
                """, user.id());
    }

    private String normalizePage(String page) {
        String normalized = page == null ? "dashboard" : page.trim().toLowerCase();
        if (!TARGET_PAGES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "알림 이동 페이지가 올바르지 않습니다.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record NotificationRequest(
            @NotBlank @Size(max = 30) String type,
            @Size(max = 100) String sourceKey,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String message,
            @NotBlank @Size(max = 30) String targetPage
    ) {}
    public record NotificationResponse(long id, String type, String sourceKey, String title, String message,
                                       String targetPage, boolean read, LocalDateTime createdAt, LocalDateTime readAt) {}
}
