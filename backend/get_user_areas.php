<?php
require_once 'db.php';
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] != 'GET') {
    echo json_encode(["error" => "Método inválido"]);
    exit();
}

$user_id = $_GET['user_id'] ?? '';

if (empty($user_id)) {
    echo json_encode(["error" => "ID do utilizador não fornecido."]);
    exit();
}

try {
    // Fetch areas where the user is either the admin or the monitored user
    $stmt = $pdo->prepare(
        "SELECT * FROM `areas` WHERE `admin_id` = ? OR `user_id` = ?"
    );
    $stmt->execute([$user_id, $user_id]);
    $areas = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "areas" => $areas
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

