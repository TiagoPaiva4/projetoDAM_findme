<?php
// config.php
date_default_timezone_set('Europe/Lisbon');

$host = 'localhost';
$dbname = 'findmyandroid';
$username = 'root'; // muda se necessário
$password = '';     // coloca a tua password

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
} catch (PDOException $e) {
    die(json_encode(['error' => 'Erro na ligação à base de dados: ' . $e->getMessage()]));
}
