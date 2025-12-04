<?php
require_once 'db.php'; // Inclui o ficheiro de conexão à base de dados

// Lida com requisições OPTIONS (preflight requests)
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    exit(0);
}

// Inicia o bloco try-catch para lidar com erros de forma elegante
try {
    // Processa requisições POST para registo
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $data = json_decode(file_get_contents('php://input'), true);

        // Verifica se todos os dados necessários estão presentes
        // A tabela 'users' tem 'name', 'email', 'password_hash'
        if (!isset($data['name'], $data['email'], $data['password'])) {
            echo json_encode(["error" => "Dados incompletos. Faltam 'name', 'email' ou 'password'."]);
            exit;
        }

        // Validação e sanitização básica
        $name = trim($data['name']);
        $email = filter_var(trim($data['email']), FILTER_VALIDATE_EMAIL); // Valida e sanitiza o email
        $password_raw = $data['password']; // Password em texto puro para hashing

        // Verifica a validação do email
        if (!$email) {
            echo json_encode(["error" => "Formato de email inválido."]);
            exit;
        }

        // Verifica se o email já existe na tabela 'users' (que tem UNIQUE no email)
        $stmt_check_email = $pdo->prepare("SELECT id_user FROM users WHERE email = ?");
        $stmt_check_email->execute([$email]);
        if ($stmt_check_email->fetch()) {
            echo json_encode(["error" => "Este email já está registado."]);
            exit;
        }

        // Hash da password
        $password_hashed = password_hash($password_raw, PASSWORD_DEFAULT);

        // Insere o novo utilizador na tabela 'users'
        $stmt_insert_user = $pdo->prepare("INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)");
        $stmt_insert_user->execute([$name, $email, $password_hashed]);

        $id_user = $pdo->lastInsertId(); // Obtém o ID do utilizador recém-inserido

        echo json_encode(["success" => "Utilizador registado com sucesso!", "id_user" => $id_user]);

    } else {
        // Método de requisição não permitido
        echo json_encode(["error" => "Método de requisição não permitido."]);
    }

} catch (PDOException $e) {
    // Para depuração, mostrar o erro exato da base de dados
    echo json_encode(["error" => "Erro na operação da base de dados: " . $e->getMessage()]);

} catch (Exception $e) {
    // Para outros tipos de erros inesperados
    echo json_encode(["error" => "Ocorreu um erro inesperado: " . $e->getMessage()]);
}
?>