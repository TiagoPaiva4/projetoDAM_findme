<?php
require_once 'db.php';

header('Content-Type: application/json');

$requester_id = isset($_GET['user_id']) ? $_GET['user_id'] : 0;

if ($requester_id == 0) {
    echo json_encode(["users" => []]); // Se não houver ID, não mostra ninguém
    exit();
}

try {
    // 1. Seleciona localizações (l) e utilizadores (u)
    // 2. Faz um JOIN com a tabela friends (f)
    // 3. Verifica se o requester_id é user_a OU user_b nessa amizade
    // 4. E garante que o status é 'accepted'

    $sql = "
        SELECT u.id_user, u.name, l.latitude, l.longitude, l.last_update
        FROM locations l
        JOIN users u ON l.id_user = u.id_user
        JOIN friends f ON
            (f.user_a = :me AND f.user_b = u.id_user)
            OR
            (f.user_b = :me AND f.user_a = u.id_user)
        WHERE f.status = 'accepted'
    ";

    $stmt = $pdo->prepare($sql);
    $stmt->execute(['me' => $requester_id]);
    $users = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["users" => $users]);

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>