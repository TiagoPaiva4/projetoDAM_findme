<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') exit();

$group_id = $_POST['group_id'] ?? '';
$requester_id = $_POST['requester_id'] ?? ''; // Quem está a fazer a chamada (o utilizador logado)
$member_id = $_POST['member_id'] ?? $requester_id; // Quem está a ser removido (pode ser ele próprio ou outro)

if (empty($group_id) || empty($requester_id)) {
    echo json_encode(["error" => "Dados incompletos."]);
    exit();
}

try {
    $pdo->beginTransaction();

    // 1. Obter informações do grupo
    $stmtGroup = $pdo->prepare("SELECT created_by FROM `groups` WHERE id_group = ?");
    $stmtGroup->execute([$group_id]);
    $groupInfo = $stmtGroup->fetch(PDO::FETCH_ASSOC);

    if (!$groupInfo) {
        $pdo->rollBack();
        echo json_encode(["error" => "Grupo não encontrado."]);
        exit();
    }
    $group_creator_id = $groupInfo['created_by'];
    $is_creator = ($requester_id == $group_creator_id);

    // 2. VERIFICAÇÃO DE PERMISSÃO:
    // Se o requerente não for o membro a ser removido E não for o criador, é negado.
    if ($requester_id != $member_id && !$is_creator) {
        $pdo->rollBack();
        echo json_encode(["error" => "Não tem permissão para remover outros membros."]);
        exit();
    }

    // 3. Remover o membro (ID do membro a remover, que pode ser o próprio requerente)
    $stmtDelete = $pdo->prepare("DELETE FROM group_members WHERE id_group = ? AND id_user = ?");
    $stmtDelete->execute([$group_id, $member_id]);

    // 4. Lógica de ELIMINAÇÃO (se o grupo ficou vazio)
    $stmtCount = $pdo->prepare("SELECT COUNT(*) FROM group_members WHERE id_group = ?");
    $stmtCount->execute([$group_id]);
    $memberCount = $stmtCount->fetchColumn();

    $message = ($requester_id == $member_id) ? "Saiu do grupo." : "Membro removido.";

    if ($memberCount == 0) {
        // Eliminar o grupo se não tiver mais membros
        $pdo->exec("DELETE FROM `groups` WHERE id_group = $group_id");
        $message = ($requester_id == $member_id) ? "Grupo eliminado." : "Grupo eliminado após a remoção do último membro.";
    }
    // Nota: Se o criador for removido/sair e houver outros, o grupo fica sem criador. Isso pode ser tratado depois.

    $pdo->commit();
    echo json_encode(["success" => $message, "group_deleted" => ($memberCount == 0)]);

} catch (PDOException $e) {
    $pdo->rollBack();
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>