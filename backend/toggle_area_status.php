<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

// Get the posted data
$data = json_decode(file_get_contents('php://input'), true);

$area_id = $data['id'] ?? '';
$is_active = $data['is_active'] ?? null;

// Validate input
if (empty($area_id)) {
    echo json_encode(["error" => "ID da área não fornecido."]);
    exit();
}

if ($is_active === null) {
    echo json_encode(["error" => "Estado 'is_active' não fornecido."]);
    exit();
}

// Convert to integer (0 or 1)
$is_active = $is_active ? 1 : 0;

try {
    $stmt = $pdo->prepare("UPDATE `areas` SET `is_active` = ? WHERE `id` = ?");
    $stmt->execute([$is_active, $area_id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode([
            "success" => "Estado da área atualizado!",
            "is_active" => $is_active
        ]);
    } else {
        echo json_encode(["error" => "Área não encontrada ou estado já igual."]);
    }

} catch (PDOException $e) {
    echo json_encode([
        "error" => "Erro na base de dados: " . $e->getMessage()
    ]);
} catch (Exception $e) {
    echo json_encode([
        "error" => "Ocorreu um erro inesperado: " . $e->getMessage()
    ]);
}
?>
