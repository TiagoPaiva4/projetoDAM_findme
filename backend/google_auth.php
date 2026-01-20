<?php
  header('Content-Type: application/json');
  require_once 'db.php';

  $data = json_decode(file_get_contents('php://input'), true);

  if (!isset($data['id_token'])) {
      http_response_code(400);
      echo json_encode(['error' => 'Token não fornecido']);
      exit;
  }

  $id_token = $data['id_token'];

  // Verify token with Google
  $google_url = 'https://oauth2.googleapis.com/tokeninfo?id_token=' . urlencode($id_token);
  $response = file_get_contents($google_url);

  if ($response === false) {
      http_response_code(401);
      echo json_encode(['error' => 'Falha ao verificar token']);
      exit;
  }

  $google_data = json_decode($response, true);

  // Check if token is valid and has email
  if (!isset($google_data['email'])) {
      http_response_code(401);
      echo json_encode(['error' => 'Token inválido']);
      exit;
  }

  $email = $google_data['email'];
  $name = $google_data['name'] ?? $google_data['email'];

  try {
      // Check if user exists
      $stmt = $pdo->prepare("SELECT id_user, name, email FROM users WHERE email = ?");
      $stmt->execute([$email]);
      $user = $stmt->fetch(PDO::FETCH_ASSOC);

      if (!$user) {
          // Create new user (no password for Google users)
          $stmt = $pdo->prepare("INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)");
          $stmt->execute([$name, $email, 'GOOGLE_AUTH']);
          $userId = $pdo->lastInsertId();
          $user = ['id_user' => $userId, 'name' => $name, 'email' => $email];
      }

      // Generate token
      $token = bin2hex(random_bytes(32));
      $expires = date('Y-m-d H:i:s', strtotime('+1 hour'));

      $stmt = $pdo->prepare("INSERT INTO user_tokens (id_user, token, expires_at) VALUES (?, ?, ?)");
      $stmt->execute([$user['id_user'], $token, $expires]);

      echo json_encode([
          'token' => $token,
          'expires_in' => 3600,
          'user' => [
              'id' => (int)$user['id_user'],
              'name' => $user['name'],
              'email' => $user['email']
          ]
      ]);

  } catch (PDOException $e) {
      http_response_code(500);
      echo json_encode(['error' => 'Erro no servidor']);
  }
