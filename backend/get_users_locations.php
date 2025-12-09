<?php
// get_users_locations.php
require_once 'db.php';

header('Content-Type: application/json');

// Receber o ID de quem está a pedir
$requester_id = isset($_GET['user_id']) ? $_GET['user_id'] : 0;

if ($requester_id == 0) {
    echo json_encode(["users" => []]);
    exit();
}

try {
    // Query corrigida usando '?' em vez de nomes
    $sql = "
        SELECT u.id_user, u.name, l.latitude, l.longitude, l.last_update
        FROM locations l
        JOIN users u ON l.id_user = u.id_user
        JOIN friends f ON
            (f.user_a = ? AND f.user_b = u.id_user)
            OR
            (f.user_b = ? AND f.user_a = u.id_user)
        WHERE f.status = 'accepted'
    ";

    $stmt = $pdo->prepare($sql);

    // IMPORTANTE: Passamos o ID duas vezes, uma para cada '?' na query
    $stmt->execute([$requester_id, $requester_id]);

    $users = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["users" => $users]);

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>