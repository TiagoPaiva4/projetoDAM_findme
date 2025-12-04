<?php
// db.php

$host = 'localhost';
$port = 3307;              // <- ADICIONA ISTO
$dbname = 'findmyandroid';
$username = 'root';
$password = '';

try {
    $pdo = new PDO("mysql:host=$host;port=$port;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $pdo->setAttribute(PDO::ATTR_EMULATE_PREPARES, false);
} catch (PDOException $e) {
    die(json_encode(["error" => "Erro ao conectar à base de dados: " . $e->getMessage()]));
}
?>
