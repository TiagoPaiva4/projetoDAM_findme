<?php
// /backend/config.php
// IMPORTANTE: Este ficheiro NAO deve ser incluido no Git!
// Adicione 'backend/config.php' ao .gitignore

// ========================================
// Configuracao SMTP (Gmail)
// ========================================
define('SMTP_HOST', 'smtp.gmail.com');
define('SMTP_PORT', 587);
define('SMTP_USERNAME', 'mario4testing@gmail.com');           // Email da conta Google
define('SMTP_PASSWORD', 'oobu gsce mijm wstg ');            // App Password de 16 caracteres
define('SMTP_FROM_NAME', 'FindMe App');

// ========================================
// Configuracao de Rate Limiting
// ========================================
define('EMAIL_RATE_LIMIT_MAX', 8);        // Maximo de emails por janela de tempo
define('EMAIL_RATE_LIMIT_WINDOW', 3600);  // Janela de tempo em segundos (3600 = 1 hora)

// ========================================
// INSTRUCOES PARA CONFIGURAR GMAIL:
// ========================================
// 1. Aceder a https://myaccount.google.com/security
// 2. Ativar "Verificacao em dois passos"
// 3. Ir a https://myaccount.google.com/apppasswords
// 4. Selecionar "Outro (Nome personalizado)" e escrever "FindMe App"
// 5. Clicar "Gerar" e copiar a password de 16 caracteres
// 6. Colar a password em SMTP_PASSWORD acima (sem espacos)
?>
