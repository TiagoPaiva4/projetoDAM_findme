<?php
require 'auth.php';

$stmt = $pdo->prepare("DELETE FROM user_tokens WHERE token = ?");
$stmt->execute([$headers['Authorization'] ? substr($headers['Authorization'], 7) : '']);

echo json_encode(['success' => true]);
