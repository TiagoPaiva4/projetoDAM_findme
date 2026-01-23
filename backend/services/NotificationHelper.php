<?php
// /backend/services/NotificationHelper.php

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/EmailService.php';

class NotificationHelper {

    private $pdo;
    private $emailService;

    public function __construct($pdo) {
        $this->pdo = $pdo;
        $this->emailService = new EmailService();
    }

    /**
     * Check if we can send an email for this zone (rate limiting)
     *
     * @param int $areaId The zone ID
     * @return bool True if under rate limit
     */
    private function canSendEmail($areaId) {
        $windowStart = date('Y-m-d H:i:s', time() - EMAIL_RATE_LIMIT_WINDOW);

        $stmt = $this->pdo->prepare(
            "SELECT COUNT(*) as count FROM email_notifications
             WHERE area_id = ? AND sent_at >= ? AND status = 'sent'"
        );
        $stmt->execute([$areaId, $windowStart]);
        $result = $stmt->fetch();

        return $result['count'] < EMAIL_RATE_LIMIT_MAX;
    }

    /**
     * Log a notification attempt to the database
     */
    private function logNotification($areaId, $adminId, $userId, $eventType, $email, $status, $errorMessage = null) {
        $stmt = $this->pdo->prepare(
            "INSERT INTO email_notifications (area_id, admin_id, user_id, event_type, email_sent_to, status, error_message)
             VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
        $stmt->execute([$areaId, $adminId, $userId, $eventType, $email, $status, $errorMessage]);
    }

    /**
     * Send a geofence notification to the zone admin
     *
     * @param int $areaId Zone ID
     * @param int $adminId Zone creator user ID
     * @param int $userId Monitored user ID
     * @param string $areaName Zone name
     * @param string $currentStatus 'inside' or 'outside'
     * @param string $lastStatus Previous status
     * @return array Result with status information
     */
    public function sendGeofenceNotification($areaId, $adminId, $userId, $areaName, $currentStatus, $lastStatus) {
        // Determine event type
        $eventType = ($currentStatus === 'inside' && $lastStatus === 'outside') ? 'enter' : 'leave';

        // Get admin details
        $adminStmt = $this->pdo->prepare("SELECT name, email FROM users WHERE id_user = ?");
        $adminStmt->execute([$adminId]);
        $admin = $adminStmt->fetch();

        if (!$admin) {
            error_log("NotificationHelper: Admin user {$adminId} not found");
            return ['success' => false, 'reason' => 'admin_not_found'];
        }

        // Get monitored user name
        $userStmt = $this->pdo->prepare("SELECT name FROM users WHERE id_user = ?");
        $userStmt->execute([$userId]);
        $user = $userStmt->fetch();

        if (!$user) {
            error_log("NotificationHelper: Monitored user {$userId} not found");
            return ['success' => false, 'reason' => 'user_not_found'];
        }

        // Check rate limit
        if (!$this->canSendEmail($areaId)) {
            $this->logNotification(
                $areaId, $adminId, $userId, $eventType,
                $admin['email'], 'rate_limited',
                'Limite de emails por hora atingido'
            );
            error_log("NotificationHelper: Rate limit exceeded for area {$areaId}");
            return ['success' => false, 'reason' => 'rate_limited'];
        }

        // Send the email
        $result = $this->emailService->sendGeofenceNotification(
            $admin['email'],
            $admin['name'],
            $user['name'],
            $areaName,
            $eventType
        );

        // Log the result
        if ($result['success']) {
            $this->logNotification(
                $areaId, $adminId, $userId, $eventType,
                $admin['email'], 'sent'
            );
            error_log("NotificationHelper: Email sent to {$admin['email']} for area {$areaName}");
        } else {
            $this->logNotification(
                $areaId, $adminId, $userId, $eventType,
                $admin['email'], 'failed', $result['error']
            );
            error_log("NotificationHelper: Email failed - " . $result['error']);
        }

        return $result;
    }
}
?>
