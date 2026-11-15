package org.sid.Notification_service.repository;

import org.sid.Notification_service.entity.ProcessedNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNotificationRepository extends JpaRepository<ProcessedNotification, String> {}
