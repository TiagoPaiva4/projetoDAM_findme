<?php
require_once 'db.php';
header('Content-Type: application/json');

$user_id = $_GET['user_id'] ?? 0;

try {
    // Seleciona grupos onde o user_id está na tabela group_members
    // CORREÇÃO: Usar backticks (`) em torno de `groups`
    $sql = "SELECT g.id_group, g.name_group, COUNT(m.id_user) as total_members
            FROM `groups` g
            JOIN group_members gm ON g.id_group = gm.id_group
            LEFT JOIN group_members m ON g.id_group = m.id_group
            WHERE gm.id_user = ?
            GROUP BY g.id_group";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([$user_id]);
    $groups = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["groups" => $groups]);

} catch (Exception $e) {
    echo json_encode(["error" => $e->getMessage()]);
}
?>