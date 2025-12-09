<?php
require_once 'db.php';
header('Content-Type: application/json');

// Só aceita POST
if ($_SERVER['REQUEST_METHOD'] != 'POST') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

$user_id = $_POST['user_id'] ?? '';      // Quem envia (TU)
$friend_email = $_POST['email'] ?? '';   // Email de quem queres adicionar

if (empty($user_id) || empty($friend_email)) {
    echo json_encode(["error" => "Preencha o email."]);
    exit();
}

try {
    // 1. Procurar o ID da pessoa pelo Email
    $stmt = $pdo->prepare("SELECT id_user FROM users WHERE email = ?");
    $stmt->execute([$friend_email]);
    $friend = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$friend) {
        echo json_encode(["error" => "Utilizador não encontrado."]);
        exit();
    }

    $friend_id = $friend['id_user'];

    // 2. Não podes adicionar-te a ti próprio
    if ($user_id == $friend_id) {
        echo json_encode(["error" => "Não podes adicionar a ti próprio."]);
        exit();
    }

    // 3. Verificar se já existe amizade (ou pedido)
    $check = $pdo->prepare("SELECT id_friendship, status FROM friends
                            WHERE (user_a = ? AND user_b = ?)
                               OR (user_a = ? AND user_b = ?)");
    $check->execute([$user_id, $friend_id, $friend_id, $user_id]);
    $existing = $check->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        echo json_encode(["error" => "Já existe uma ligação ou pedido pendente."]);
        exit();
    }

    // 4. Criar o pedido (Status: pending)
    // user_a = QUEM PEDIU (Tu)
    // user_b = QUEM RECEBE (O Amigo)
    $insert = $pdo->prepare("INSERT INTO friends (user_a, user_b, status) VALUES (?, ?, 'pending')");

    if ($insert->execute([$user_id, $friend_id])) {
        echo json_encode(["success" => "Pedido enviado com sucesso!"]);
    } else {
        echo json_encode(["error" => "Erro ao processar pedido."]);
    }

} catch (PDOException $e) {
    echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
}
?>