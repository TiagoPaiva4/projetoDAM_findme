<?php
require 'config.php';
header('Content-Type: application/json');

$data = json_decode(file_get_contents('php://input'), true);
$email = trim($data['email'] ?? '');
$password = $data['password'] ?? '';

if (!$email || !$password) {
    echo json_encode(['error' => 'Credenciais inválidas']);
    exit;
}

$stmt = $pdo->prepare("SELECT * FROM users WHERE email = ?");
$stmt->execute([$email]);
$user = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$user || !password_verify($password, $user['password_hash'])) {
    echo json_encode(['error' => 'Email ou password incorretos']);
    exit;
}

// Gerar token
$token = bin2hex(random_bytes(32)); // 64 caracteres
$expires_at = date('Y-m-d H:i:s', time() + 3600); // 1 hora

$stmt = $pdo->prepare("INSERT INTO user_tokens (id_user, token, expires_at) VALUES (?, ?, ?)");
$stmt->execute([$user['id_user'], $token, $expires_at]);

echo json_encode([
    'token' => $token,
    'expires_in' => 3600,
    'user' => [
        'id' => $user['id_user'],
        'name' => $user['name'],
        'email' => $user['email']
    ]
]);
