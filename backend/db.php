<?php
// db.php

// 1. CORREÇÃO: O nome EXATO que está no seu 2º print
$host = 'findme.mysql.database.azure.com';

// 2. O nome da base de dados (Geralmente cria-se uma dentro do servidor)
// Se der erro de "Unknown database", mude isto para 'findme' ou crie a base no portal
$dbname = 'findmyandroid';

// 3. Porta padrão
$port = 3306;

// 4. O utilizador que está no seu 2º print
$username = 'findmeadmin';
$password = 'findmeadmin1?'; // A password que definiu

try {
    $dsn = "mysql:host=$host;port=$port;dbname=$dbname;charset=utf8mb4";

    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
        // Importante para a Azure não bloquear por causa do SSL
        PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT => false,
    ];

    $pdo = new PDO($dsn, $username, $password, $options);

} catch (PDOException $e) {
    http_response_code(500);
    die(json_encode(["error" => "Erro Conexão: " . $e->getMessage()]));
}
?>