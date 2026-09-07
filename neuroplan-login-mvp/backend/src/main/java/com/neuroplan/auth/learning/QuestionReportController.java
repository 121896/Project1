package com.neuroplan.auth.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.neuroplan.auth.admin.AdminAccessService;
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
@RequestMapping("/api")
public class QuestionReportController {
    private static final Set<String> REPORT_TYPES = Set.of("INCORRECT", "AMBIGUOUS", "DUPLICATE", "OTHER");
    private static final Set<String> REPORT_STATUSES = Set.of("OPEN", "RESOLVED", "DISMISSED");
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final AdminAccessService adminAccessService;

    public QuestionReportController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService,
                                    AdminAccessService adminAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.adminAccessService = adminAccessService;
    }

    @PostMapping("/learning/questions/{questionId}/reports")
    public void create(@PathVariable long questionId, @Valid @RequestBody QuestionReportRequest body,
                       HttpServletRequest request) {
        UserRecord user = currentUserService.require(request);
        Long questionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM diagnosis_questions WHERE id = ?", Long.class, questionId);
        if (questionCount == null || questionCount == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "신고할 문제를 찾을 수 없습니다.");
        }
        String type = body.type().trim().toUpperCase();
        if (!REPORT_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "신고 유형이 올바르지 않습니다.");
        jdbcTemplate.update("""
                INSERT INTO question_reports (
                    reporter_user_id, question_id, report_type, detail, report_status,
                    resolution_note, created_at, resolved_at, resolved_by_user_id
                ) VALUES (?, ?, ?, ?, 'OPEN', NULL, CURRENT_TIMESTAMP(6), NULL, NULL)
                """, user.id(), questionId, type, blankToNull(body.detail()));
    }

    @GetMapping("/admin/question-reports")
    public List<QuestionReportResponse> reports(HttpServletRequest request) {
        adminAccessService.require(request);
        return jdbcTemplate.query("""
                SELECT r.id, r.question_id, r.report_type, r.detail, r.report_status, r.resolution_note,
                       r.created_at, r.resolved_at, q.question_text, s.name AS subject_name,
                       u.email AS reporter_email
                  FROM question_reports r
                  JOIN diagnosis_questions q ON q.id = r.question_id
                  JOIN subjects s ON s.id = q.subject_id
                  JOIN users u ON u.id = r.reporter_user_id
                 ORDER BY FIELD(r.report_status, 'OPEN', 'RESOLVED', 'DISMISSED'), r.created_at DESC
                 LIMIT 200
                """, (rs, rowNum) -> new QuestionReportResponse(
                rs.getLong("id"), rs.getLong("question_id"), rs.getString("subject_name"),
                rs.getString("question_text"), rs.getString("report_type"), rs.getString("detail"),
                rs.getString("report_status"), rs.getString("resolution_note"), rs.getString("reporter_email"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toLocalDateTime()
        ));
    }

    @PatchMapping("/admin/question-reports/{reportId}")
    public void resolve(@PathVariable long reportId, @Valid @RequestBody QuestionReportResolutionRequest body,
                        HttpServletRequest request) {
        UserRecord admin = adminAccessService.require(request);
        String status = body.status().trim().toUpperCase();
        if (!REPORT_STATUSES.contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "신고 처리 상태가 올바르지 않습니다.");
        int updated = jdbcTemplate.update("""
                UPDATE question_reports
                   SET report_status = ?, resolution_note = ?,
                       resolved_at = CASE WHEN ? = 'OPEN' THEN NULL ELSE CURRENT_TIMESTAMP(6) END,
                       resolved_by_user_id = CASE WHEN ? = 'OPEN' THEN NULL ELSE ? END
                 WHERE id = ?
                """, status, blankToNull(body.resolutionNote()), status, status, admin.id(), reportId);
        if (updated == 0) throw new ApiException(HttpStatus.NOT_FOUND, "문제 신고를 찾을 수 없습니다.");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record QuestionReportRequest(@NotBlank @Size(max = 30) String type, @Size(max = 500) String detail) {}
    public record QuestionReportResolutionRequest(@NotBlank @Size(max = 20) String status,
                                                  @Size(max = 500) String resolutionNote) {}
    public record QuestionReportResponse(long id, long questionId, String subjectName, String questionText,
                                         String type, String detail, String status, String resolutionNote,
                                         String reporterEmail, LocalDateTime createdAt, LocalDateTime resolvedAt) {}
}
