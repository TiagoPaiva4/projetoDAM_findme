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
$name = $data['name'] ?? '';
$user_id = $data['user_id'] ?? '';
$coordinates = $data['coordinates'] ?? '';

// Validate input
if (empty($area_id)) {
    echo json_encode(["error" => "ID da área não fornecido."]);
    exit();
}

if (empty($name) || empty($user_id) || empty($coordinates)) {
    echo json_encode([
        "error" => "Dados incompletos. Faltam 'name', 'user_id' ou 'coordinates'."
    ]);
    exit();
}

try {
    $stmt = $pdo->prepare(
        "UPDATE `areas` SET `name` = ?, `user_id` = ?, `coordinates` = ? WHERE `id` = ?"
    );
    $stmt->execute([$name, $user_id, $coordinates, $area_id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["success" => "Área atualizada com sucesso!"]);
    } else {
        echo json_encode(["error" => "Área não encontrada ou dados iguais."]);
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
