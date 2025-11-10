<?php
require 'config.php';
header('Content-Type: application/json');

$headers = getallheaders();
$token = $headers['Authorization'] ?? '';

if (str_starts_with($token, 'Bearer ')) {
    $token = substr($token, 7);
}

if (!$token) {
    http_response_code(401);
    echo json_encode(['error' => 'Token em falta']);
    exit;
}

$stmt = $pdo->prepare("SELECT * FROM user_tokens WHERE token = ?");
$stmt->execute([$token]);
$tokenData = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$tokenData) {
    http_response_code(401);
    echo json_encode(['error' => 'Token inválido']);
    exit;
}

if (strtotime($tokenData['expires_at']) < time()) {
    $pdo->prepare("DELETE FROM user_tokens WHERE token = ?")->execute([$token]);
    http_response_code(401);
    echo json_encode(['error' => 'Token expirado']);
    exit;
}

$currentUserId = $tokenData['id_user'];
