package learn.java.billingsoftware.service;

import learn.java.billingsoftware.io.ActivityLogPageResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ActivityLogService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void logActivity(String userEmail, String action, String entityType, String entityId, String description);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void logActivity(String action, String entityType, String entityId, String description);

    ActivityLogPageResponse getActivityLogs(int page, int size, String userEmail, String action, String startDate, String endDate, String authenticatedEmail, boolean isAdmin);
}
