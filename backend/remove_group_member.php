<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

$group_id = $_POST['group_id'] ?? '';
$user_id = $_POST['user_id'] ?? '';
$is_creator = $_POST['is_creator'] ?? 'false';
$creator_id = $_POST['creator_id'] ?? '';

if (empty($group_id) || empty($user_id)) {
    echo json_encode(["error" => "Dados incompletos."]);
    exit();
}

try {
    $pdo->beginTransaction();

    // 1. Verificar se o grupo existe e quem é o criador
    $stmtGroup = $pdo->prepare("SELECT created_by FROM `groups` WHERE id_group = ?");
    $stmtGroup->execute([$group_id]);
    $groupInfo = $stmtGroup->fetch(PDO::FETCH_ASSOC);

    if (!$groupInfo) {
        $pdo->rollBack();
        echo json_encode(["error" => "Grupo não encontrado."]);
        exit();
    }
    $group_creator_id = $groupInfo['created_by'];

    // Lógica para REMOVER/SAIR
    $stmtDelete = $pdo->prepare("DELETE FROM group_members WHERE id_group = ? AND id_user = ?");
    $stmtDelete->execute([$group_id, $user_id]);

    // 2. Verificar se o grupo ficou vazio (ou se o criador saiu/foi removido)
    $stmtCount = $pdo->prepare("SELECT COUNT(*) FROM group_members WHERE id_group = ?");
    $stmtCount->execute([$group_id]);
    $memberCount = $stmtCount->fetchColumn();

    $message = "Saiu do grupo.";

    // 3. Lógica de ELIMINAÇÃO (se o grupo ficou vazio, ou se o criador o eliminou)
    if ($memberCount == 0 || ($is_creator === 'true' && $user_id == $group_creator_id)) {
        // Eliminar o grupo se não tiver mais membros ou se o criador o eliminar
        $pdo->exec("DELETE FROM `groups` WHERE id_group = $group_id");
        $message = "Grupo eliminado.";
    }
    // TODO: Adicionar lógica para transferir posse se o criador sair e houver outros membros.

    $pdo->commit();
    echo json_encode(["success" => $message]);

} catch (PDOException $e) {
    $pdo->rollBack();
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>