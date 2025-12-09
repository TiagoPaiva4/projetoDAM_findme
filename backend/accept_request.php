<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') exit();

$id_friendship = $_POST['id_friendship'];
$action = $_POST['action']; // 'accept' ou 'reject'

if ($action == 'accept') {
    $stmt = $pdo->prepare("UPDATE friends SET status = 'accepted' WHERE id_friendship = ?");
    $stmt->execute([$id_friendship]);
    echo json_encode(["success" => "Amigo adicionado!"]);
} else {
    $stmt = $pdo->prepare("DELETE FROM friends WHERE id_friendship = ?");
    $stmt->execute([$id_friendship]);
    echo json_encode(["success" => "Pedido removido."]);
}
?>