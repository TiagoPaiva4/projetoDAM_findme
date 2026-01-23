<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'POST') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

// Get the posted data
$data = json_decode(file_get_contents('php://input'), true);

$name = $data['name'] ?? '';
$admin_id = $data['admin_id'] ?? '';
$user_id = $data['user_id'] ?? '';
$area_type = $data['area_type'] ?? 'polygon'; // Default to polygon
$coordinates = $data['coordinates'] ?? '';

// Validate input
if (empty($name) || empty($admin_id) || empty($user_id) || empty($coordinates)) {
    echo json_encode([
        "error" => "Dados incompletos. Faltam 'name', 'admin_id', 'user_id' ou 'coordinates'."
    ]);
    exit();
}

try {
    $stmt = $pdo->prepare(
        "INSERT INTO `areas` (`name`, `admin_id`, `user_id`, `area_type`, `coordinates`)
         VALUES (?, ?, ?, ?, ?)"
    );
    $stmt->execute([$name, $admin_id, $user_id, $area_type, $coordinates]);

    $area_id = $pdo->lastInsertId();
    echo json_encode([
        "success" => "Área criada com sucesso!",
        "id" => $area_id
    ]);

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

