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

// Validate input
if (empty($area_id)) {
    echo json_encode(["error" => "ID da área não fornecido."]);
    exit();
}

try {
    $stmt = $pdo->prepare("DELETE FROM `areas` WHERE `id` = ?");
    $stmt->execute([$area_id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["success" => "Área eliminada com sucesso!"]);
    } else {
        echo json_encode(["error" => "Área não encontrada ou já eliminada."]);
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

