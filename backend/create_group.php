<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') exit();

$name = $_POST['name'];
$user_id = $_POST['user_id'];

if (!$name || !$user_id) {
    echo json_encode(["error" => "Dados incompletos"]);
    exit;
}

try {
    $pdo->beginTransaction();

    // 1. Criar Grupo (CORRIGIDO: A tabela 'groups' deve ter backticks)
    $stmt = $pdo->prepare("INSERT INTO `groups` (name_group, created_by) VALUES (?, ?)");
    $stmt->execute([$name, $user_id]);
    $group_id = $pdo->lastInsertId();

    // 2. Adicionar o criador como membro
    $stmtMember = $pdo->prepare("INSERT INTO group_members (id_group, id_user) VALUES (?, ?)");
    $stmtMember->execute([$group_id, $user_id]);

    $pdo->commit();
    echo json_encode(["success" => "Grupo criado!", "id_group" => $group_id]);

} catch (Exception $e) {
    $pdo->rollBack();
    echo json_encode(["error" => "Erro: " . $e->getMessage()]);
}
?>