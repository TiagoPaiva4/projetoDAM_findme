<?php
// /backend/services/EmailService.php

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../phpmailer/src/Exception.php';
require_once __DIR__ . '/../phpmailer/src/PHPMailer.php';
require_once __DIR__ . '/../phpmailer/src/SMTP.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\SMTP;
use PHPMailer\PHPMailer\Exception;

class EmailService {

    private $mailer;

    public function __construct() {
        $this->mailer = new PHPMailer(true);
        $this->configureMailer();
    }

    private function configureMailer() {
        $this->mailer->isSMTP();
        $this->mailer->Host       = SMTP_HOST;
        $this->mailer->SMTPAuth   = true;
        $this->mailer->Username   = SMTP_USERNAME;
        $this->mailer->Password   = SMTP_PASSWORD;
        $this->mailer->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $this->mailer->Port       = SMTP_PORT;

        $this->mailer->setFrom(SMTP_USERNAME, SMTP_FROM_NAME);
        $this->mailer->CharSet = 'UTF-8';
    }

    /**
     * Send a geofence notification email
     *
     * @param string $toEmail Recipient email address
     * @param string $toName Recipient name
     * @param string $userName Name of the monitored user
     * @param string $zoneName Name of the zone
     * @param string $eventType 'enter' or 'leave'
     * @return array ['success' => bool, 'error' => string|null]
     */
    public function sendGeofenceNotification($toEmail, $toName, $userName, $zoneName, $eventType) {
        try {
            $this->mailer->clearAddresses();
            $this->mailer->addAddress($toEmail, $toName);

            $this->mailer->isHTML(true);

            if ($eventType === 'enter') {
                $this->mailer->Subject = "FindMe: {$userName} entrou na zona \"{$zoneName}\"";
                $actionText = 'entrou na';
                $actionColor = '#28a745';
            } else {
                $this->mailer->Subject = "FindMe: {$userName} saiu da zona \"{$zoneName}\"";
                $actionText = 'saiu da';
                $actionColor = '#dc3545';
            }

            $this->mailer->Body = $this->getGeofenceEmailTemplate(
                $toName, $userName, $zoneName, $actionText, $actionColor
            );

            $this->mailer->AltBody = "{$userName} {$actionText} zona \"{$zoneName}\".";

            $this->mailer->send();
            return ['success' => true, 'error' => null];

        } catch (Exception $e) {
            error_log("EmailService Error: " . $e->getMessage());
            return ['success' => false, 'error' => $e->getMessage()];
        }
    }

    /**
     * Send a registration confirmation email (for future use)
     */
    public function sendRegistrationConfirmation($toEmail, $toName) {
        try {
            $this->mailer->clearAddresses();
            $this->mailer->addAddress($toEmail, $toName);

            $this->mailer->isHTML(true);
            $this->mailer->Subject = 'Bem-vindo ao FindMe!';
            $this->mailer->Body = $this->getRegistrationEmailTemplate($toName);
            $this->mailer->AltBody = "Bem-vindo ao FindMe, {$toName}! A sua conta foi criada com sucesso.";

            $this->mailer->send();
            return ['success' => true, 'error' => null];

        } catch (Exception $e) {
            error_log("EmailService Error: " . $e->getMessage());
            return ['success' => false, 'error' => $e->getMessage()];
        }
    }

    private function getGeofenceEmailTemplate($recipientName, $userName, $zoneName, $actionText, $actionColor) {
        $timestamp = date('d/m/Y H:i:s');

        return <<<HTML
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
</head>
<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
    <div style="background-color: #f8f9fa; border-radius: 10px; padding: 30px;">
        <h1 style="color: #007bff; margin-top: 0;">FindMe</h1>

        <p>Ola <strong>{$recipientName}</strong>,</p>

        <div style="background-color: white; border-left: 4px solid {$actionColor}; padding: 15px; margin: 20px 0; border-radius: 4px;">
            <p style="margin: 0; font-size: 16px;">
                <strong style="color: {$actionColor};">{$userName}</strong> {$actionText} zona <strong>"{$zoneName}"</strong>
            </p>
        </div>

        <p style="color: #666; font-size: 14px;">
            <strong>Data/Hora:</strong> {$timestamp}
        </p>

        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">

        <p style="color: #999; font-size: 12px; margin-bottom: 0;">
            Esta notificacao foi gerada automaticamente pela aplicacao FindMe.<br>
            Esta a receber este email porque criou uma zona de controlo para este utilizador.
        </p>
    </div>
</body>
</html>
HTML;
    }

    private function getRegistrationEmailTemplate($userName) {
        return <<<HTML
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
</head>
<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
    <div style="background-color: #f8f9fa; border-radius: 10px; padding: 30px;">
        <h1 style="color: #007bff; margin-top: 0;">Bem-vindo ao FindMe!</h1>

        <p>Ola <strong>{$userName}</strong>,</p>

        <p>A sua conta foi criada com sucesso. Agora pode:</p>

        <ul>
            <li>Adicionar amigos e partilhar a sua localizacao</li>
            <li>Criar grupos para seguir pessoas</li>
            <li>Definir zonas de controlo com notificacoes</li>
        </ul>

        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">

        <p style="color: #999; font-size: 12px; margin-bottom: 0;">
            Este email foi enviado automaticamente pela aplicacao FindMe.
        </p>
    </div>
</body>
</html>
HTML;
    }
}
?>
