<?php
require_once 'db.php';
header('Content-Type: application/json');

$my_id = $_GET['user_id'] ?? '';

if (!$my_id) { echo json_encode(["requests" => []]); exit(); }

// Buscar quem (users) enviou pedido para mim (friends.user_b) e status é 'pending'
$sql = "SELECT f.id_friendship, u.name, u.email
        FROM friends f
        JOIN users u ON f.user_a = u.id_user
        WHERE f.user_b = ? AND f.status = 'pending'";

$stmt = $pdo->prepare($sql);
$stmt->execute([$my_id]);
$requests = $stmt->fetchAll(PDO::FETCH_ASSOC);

echo json_encode(["requests" => $requests]);
?>