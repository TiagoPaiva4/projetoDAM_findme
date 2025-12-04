<?php
// update_location.php
require_once 'db.php';

// Verificar se o pedido é POST
if ($_SERVER['REQUEST_METHOD'] == 'POST') {

    // Receber os dados do Android
    $user_id = $_POST['user_id'];
    $latitude = $_POST['latitude'];
    $longitude = $_POST['longitude'];

    // Validação básica
    if (empty($user_id) || empty($latitude) || empty($longitude)) {
        echo json_encode(["error" => "Dados incompletos"]);
        exit();
    }

    try {
        // 1. Primeiro verificamos se este utilizador já tem localização na tabela
        $checkStmt = $pdo->prepare("SELECT id_location FROM locations WHERE id_user = ?");
        $checkStmt->execute([$user_id]);
        $row = $checkStmt->fetch();

        if ($row) {
            // CENÁRIO A: O utilizador já existe -> ATUALIZAMOS a localização (UPDATE)
            $updateStmt = $pdo->prepare("UPDATE locations SET latitude = ?, longitude = ?, last_update = NOW() WHERE id_user = ?");
            if ($updateStmt->execute([$latitude, $longitude, $user_id])) {
                echo json_encode(["success" => "Localização atualizada"]);
            } else {
                echo json_encode(["error" => "Erro ao atualizar"]);
            }
        } else {
            // CENÁRIO B: É a primeira vez -> CRIAMOS uma nova linha (INSERT)
            $insertStmt = $pdo->prepare("INSERT INTO locations (id_user, latitude, longitude, last_update) VALUES (?, ?, ?, NOW())");
            if ($insertStmt->execute([$user_id, $latitude, $longitude])) {
                echo json_encode(["success" => "Localização criada"]);
            } else {
                echo json_encode(["error" => "Erro ao inserir"]);
            }
        }

    } catch (PDOException $e) {
        echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["error" => "Método não permitido (use POST)"]);
}
?>