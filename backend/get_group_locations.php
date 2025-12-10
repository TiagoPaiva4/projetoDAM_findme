<?php
// get_group_locations.php
require_once 'db.php';

header('Content-Type: application/json');

$group_id = isset($_GET['group_id']) ? $_GET['group_id'] : 0;
// $requester_id não é estritamente necessário para a query, mas ajuda na segurança
$requester_id = isset($_GET['requester_id']) ? $_GET['requester_id'] : 0;

if ($group_id == 0) {
    echo json_encode(["members" => []]);
    exit();
}

try {
    // Busca a localização (l) e o nome (u) de todos os membros (gm)
    // de um grupo específico.
    $sql = "
        SELECT u.id_user, u.name, l.latitude, l.longitude, l.last_update
        FROM group_members gm
        JOIN users u ON gm.id_user = u.id_user
        LEFT JOIN locations l ON gm.id_user = l.id_user
        WHERE gm.id_group = ?
    ";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([$group_id]);

    $members = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["members" => $members]);

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>