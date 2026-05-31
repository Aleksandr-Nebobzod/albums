<?php
// Простой шлюз для пересылки фото между телефонами
// Работает на PHP 7.0+

$storage_dir = __DIR__ . '/queue/';
if (!file_exists($storage_dir)) {
    mkdir($storage_dir, 0777, true);
}

// Получаем действие из параметра ?action=
$action = isset($_GET['action']) ? $_GET['action'] : '';

// ========== 1. ЗАГРУЗКА ФОТО (отправитель) ==========
if ($action === 'upload' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!isset($_FILES['photo']) || $_FILES['photo']['error'] !== UPLOAD_ERR_OK) {
        http_response_code(400);
        die('No photo uploaded');
    }
    
    $file = $_FILES['photo'];
    
    // Проверка размера (20MB)
    if ($file['size'] > 20 * 1024 * 1024) {
        http_response_code(413);
        die('File too large');
    }
    
    // Уникальное имя
    $id = uniqid() . '_' . time() . '.jpg';
    $destination = $storage_dir . $id;
    
    if (move_uploaded_file($file['tmp_name'], $destination)) {
        header('Content-Type: application/json');
        echo json_encode(array('status' => 'ok', 'id' => $id));
    } else {
        http_response_code(500);
        die('Save failed');
    }
}

// ========== 2.0 ПОЛУЧИТЬ Число ФОТО (получатель) ==========
elseif ($action === 'count') {
    $files = glob($storage_dir . '*.*');
    $count = 0;
    foreach ($files as $file) {
        if (strpos(mime_content_type($file), 'image/') === 0) {
            $count++;
        }
    }
    header('Content-Type: application/json');
    echo json_encode(['count' => $count]);
}

// ========== 2.1 ПОЛУЧИТЬ СПИСОК ФОТО (получатель) ==========
elseif ($action === 'list') {
    $allFiles = scandir($storage_dir);
    $result = array();

    foreach ($allFiles as $file) {
        if ($file === '.' || $file === '..') continue;
        $filePath = $storage_dir . $file;
        $mime = mime_content_type($filePath);
        if (strpos($mime, 'image/') === 0) {
            $result[] = array(
                'id' => $file,
                'size' => filesize($filePath),
                'time' => filemtime($filePath)
            );
        }
    }

    // Сортируем от старых к новым (без стрелочной функции)
    usort($result, 'compare_by_time');
    
    header('Content-Type: application/json');
    echo json_encode($result);
}

// ========== 3. СКАЧАТЬ КОНКРЕТНОЕ ФОТО (получатель) ==========
elseif ($action === 'get' && isset($_GET['id'])) {
    $id = preg_replace('/[^a-zA-Z0-9_\.]/', '', $_GET['id']);
    $file = $storage_dir . $id;
    
    if (file_exists($file)) {
        header('Content-Type: image/jpeg');
        header('Content-Disposition: attachment; filename="' . $id . '"');
        readfile($file);
    } else {
        http_response_code(404);
        die('Not found');
    }
}

// ========== 4. УДАЛИТЬ ФОТО ПОСЛЕ СИНХРОНИЗАЦИИ (получатель) ==========
elseif ($action === 'delete' && isset($_GET['id'])) {
    $id = preg_replace('/[^a-zA-Z0-9_\.]/', '', $_GET['id']);
    $file = $storage_dir . $id;
    
    if (file_exists($file)) {
        unlink($file);
        header('Content-Type: application/json');
        echo json_encode(array('status' => 'ok'));
    } else {
        http_response_code(404);
        die('Not found');
    }
}

// ========== 5. ПРОВЕРКА ЖИЗНЕСПОСОБНОСТИ СЕРВЕРА ==========
elseif ($action === 'ping') {
    header('Content-Type: application/json');
    echo json_encode(array('status' => 'ok', 'timestamp' => time()));
}

// ========== 6. ПО УМОЛЧАНИЮ: простой HTML-интерфейс для проверки ==========
else {
    $queue_count = count(glob($storage_dir . '*.jpg'));
    ?>
    <!DOCTYPE html>
    <html>
    <head>
        <title>Photo Gateway</title>
        <meta charset="UTF-8">
        <style>body { font-family: sans-serif; padding: 20px; }</style>
    </head>
    <body>
        <h1>Photo Gateway</h1>
        <p>Сервер работает. Фото в очереди: <strong><?php echo $queue_count; ?></strong></p>
        
        <h2>Отправить фото для теста:</h2>
<form method="post" enctype="multipart/form-data" action="?action=upload">
    <input type="file" name="photo" accept="image/*">
    <input type="submit" value="Отправить">
</form>
        
        <?php
        if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES)) {
            echo '<p style="color: green">Фото отправлено!</p>';
        }
        ?>
    </body>
    </html>
    <?php
}

// Вспомогательная функция для сортировки (вместо стрелочной)
function compare_by_time($a, $b) {
    if ($a['time'] == $b['time']) {
        return 0;
    }
    return ($a['time'] < $b['time']) ? -1 : 1;
}
?>