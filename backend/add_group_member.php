<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

$group_id = $_POST['group_id'] ?? '';
$email_to_add = $_POST['email'] ?? '';
$user_id_to_add = $_POST['user_id'] ?? '';

if (empty($group_id) || (empty($email_to_add) && empty($user_id_to_add))) {
    echo json_encode(["error" => "Dados incompletos."]);
    exit();
}

try {
    // 1. Get user_id either directly or by email lookup
    if (empty($user_id_to_add)) {
        $stmt = $pdo->prepare("SELECT id_user FROM users WHERE email = ?");
        $stmt->execute([$email_to_add]);
        $user_to_add = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$user_to_add) {
            echo json_encode(["error" => "Utilizador não encontrado."]);
            exit();
        }

        $user_id_to_add = $user_to_add['id_user'];
    }

    // 2. Verificar se já é membro do grupo
    $check = $pdo->prepare("SELECT id_member FROM group_members WHERE id_group = ? AND id_user = ?");
    $check->execute([$group_id, $user_id_to_add]);
    if ($check->fetch()) {
        echo json_encode(["error" => "Este utilizador já é membro do grupo."]);
        exit();
    }

    // 3. Adicionar o utilizador como membro
    $insert = $pdo->prepare("INSERT INTO group_members (id_group, id_user) VALUES (?, ?)");

    if ($insert->execute([$group_id, $user_id_to_add])) {
        echo json_encode(["success" => "Membro adicionado com sucesso!"]);
    } else {
        echo json_encode(["error" => "Erro ao adicionar membro."]);
    }

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>