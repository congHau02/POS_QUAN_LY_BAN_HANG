package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.ActivityLogEntity;
import learn.java.billingsoftware.io.ActivityLogPageResponse;
import learn.java.billingsoftware.io.ActivityLogResponse;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Override
    public void logActivity(String userEmail, String action, String entityType, String entityId, String description) {
        try {
            String effectiveEmail = userEmail;
            if (effectiveEmail == null || effectiveEmail.isBlank() || effectiveEmail.equals("anonymousUser")) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getName() != null && !auth.getName().isBlank() && !auth.getName().equals("anonymousUser")) {
                    effectiveEmail = auth.getName();
                } else {
                    effectiveEmail = "system@billing.com";
                }
            }

            ActivityLogEntity entity = ActivityLogEntity.builder()
                    .logId(UUID.randomUUID().toString())
                    .userEmail(effectiveEmail)
                    .action(action != null ? action.toUpperCase() : "UNKNOWN")
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .createdAt(LocalDateTime.now())
                    .build();

            activityLogRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }

    @Override
    public void logActivity(String action, String entityType, String entityId, String description) {
        logActivity(null, action, entityType, entityId, description);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityLogPageResponse getActivityLogs(int page, int size, String userEmail, String action,
                                                   String startDate, String endDate,
                                                   String authenticatedEmail, boolean isAdmin) {
        String targetEmail;
        if (!isAdmin) {
            // Staff is strictly restricted to their own email
            targetEmail = authenticatedEmail;
        } else {
            // Admin can query any userEmail or all users if null/empty
            targetEmail = (userEmail != null && !userEmail.isBlank() && !userEmail.equalsIgnoreCase("ALL")) ? userEmail.trim() : null;
        }

        String targetAction = (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL")) ? action.trim().toUpperCase() : null;

        LocalDateTime startDateTime = null;
        if (startDate != null && !startDate.isBlank()) {
            try {
                startDateTime = LocalDate.parse(startDate.trim()).atStartOfDay();
            } catch (Exception ignored) {
            }
        }

        LocalDateTime endDateTime = null;
        if (endDate != null && !endDate.isBlank()) {
            try {
                endDateTime = LocalDate.parse(endDate.trim()).atTime(LocalTime.MAX);
            } catch (Exception ignored) {
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ActivityLogEntity> logPage = activityLogRepository.findLogsWithFilter(
                targetEmail, targetAction, startDateTime, endDateTime, pageable);

        List<ActivityLogResponse> content = logPage.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return ActivityLogPageResponse.builder()
                .content(content)
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    private ActivityLogResponse convertToResponse(ActivityLogEntity entity) {
        return ActivityLogResponse.builder()
                .id(entity.getId())
                .logId(entity.getLogId())
                .userEmail(entity.getUserEmail())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .description(entity.getDescription())
                .timestamp(entity.getCreatedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
