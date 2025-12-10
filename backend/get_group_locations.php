<?php
// get_group_locations.php
require_once 'db.php';

header('Content-Type: application/json');

$group_id = isset($_GET['group_id']) ? $_GET['group_id'] : 0;
$requester_id = isset($_GET['requester_id']) ? $_GET['requester_id'] : 0;

if ($group_id == 0) {
    echo json_encode(["members" => []]);
    exit();
}

try {
    // 1. Obter o ID do Criador do Grupo
    $stmtCreator = $pdo->prepare("SELECT created_by FROM `groups` WHERE id_group = ?");
    $stmtCreator->execute([$group_id]);
    $creator_id = $stmtCreator->fetchColumn();

    // 2. Buscar a localização e o nome de todos os membros
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

    // 3. Devolver a resposta com a informação do criador
    echo json_encode([
        "members" => $members,
        "creator_id" => $creator_id // NOVO: Devolve o ID do criador
    ]);

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>