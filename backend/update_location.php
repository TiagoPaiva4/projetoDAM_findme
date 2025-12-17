<?php
require_once 'db.php';

// Function to check if a point is inside a polygon
function isPointInPolygon($point, $polygon) {
    $num_vertices = count($polygon);
    if ($num_vertices < 3) {
        return false;
    }

    $lat = $point['lat'];
    $lng = $point['lng'];
    $is_inside = false;

    $j = $num_vertices - 1;
    for ($i = 0; $i < $num_vertices; $j = $i++) {
        $vertex_i_lat = $polygon[$i]['lat'];
        $vertex_i_lng = $polygon[$i]['lng'];
        $vertex_j_lat = $polygon[$j]['lat'];
        $vertex_j_lng = $polygon[$j]['lng'];

        if ((($vertex_i_lng > $lng) != ($vertex_j_lng > $lng)) &&
            ($lat < ($vertex_j_lat - $vertex_i_lat) * ($lng - $vertex_i_lng) / ($vertex_j_lng - $vertex_i_lng) + $vertex_i_lat)
        ) {
            $is_inside = !$is_inside;
        }
    }
    return $is_inside;
}

// Check if the request is POST
if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    // Receive data from Android
    $user_id = $_POST['user_id'];
    $latitude = $_POST['latitude'];
    $longitude = $_POST['longitude'];

    // Basic validation
    if (empty($user_id) || !is_numeric($latitude) || !is_numeric($longitude)) {
        echo json_encode(["error" => "Dados incompletos ou inválidos para localização."]);
        exit();
    }

    try {
        // Start a transaction for atomicity
        $pdo->beginTransaction();

        // 1. Update or Insert user's location
        $checkStmt = $pdo->prepare("SELECT id_location FROM locations WHERE id_user = ?");
        $checkStmt->execute([$user_id]);
        $row = $checkStmt->fetch();

        if ($row) {
            // User exists -> UPDATE location
            $updateStmt = $pdo->prepare(
                "UPDATE locations SET latitude = ?, longitude = ?, last_update = NOW() WHERE id_user = ?"
            );
            $updateStmt->execute([$latitude, $longitude, $user_id]);
        } else {
            // First time -> INSERT new location
            $insertStmt = $pdo->prepare(
                "INSERT INTO locations (id_user, latitude, longitude, last_update) VALUES (?, ?, ?, NOW())"
            );
            $insertStmt->execute([$user_id, $latitude, $longitude]);
        }

        // 2. Geofencing Logic
        $current_point = [
            'lat' => (float)$latitude,
            'lng' => (float)$longitude
        ];

        // Fetch areas where the current user is either the admin or the monitored user
        $areasStmt = $pdo->prepare(
            "SELECT id, name, admin_id, user_id, coordinates, last_status
             FROM `areas`
             WHERE `admin_id` = ? OR `user_id` = ?"
        );
        $areasStmt->execute([$user_id, $user_id]);
        $relevant_areas = $areasStmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($relevant_areas as $area) {
            $area_id = $area['id'];
            $area_name = $area['name'];
            $admin_id = $area['admin_id'];
            $monitored_user_id = $area['user_id'];
            $last_status = $area['last_status'];
            $polygon_coords_json = $area['coordinates'];

            // Decode coordinates to an array of points
            $polygon_points = json_decode($polygon_coords_json, true);

            if ($polygon_points === null && json_last_error() !== JSON_ERROR_NONE) {
                error_log(
                    "Error decoding polygon coordinates for area ID: " .
                    $area_id . " - " . json_last_error_msg()
                );
                continue;
            }

            // Determine if the current point is inside the polygon
            $is_currently_inside = isPointInPolygon($current_point, $polygon_points);
            $current_status = $is_currently_inside ? 'inside' : 'outside';

            // Check if status has changed
            if ($last_status !== $current_status) {
                $notification_message = "";

                // If the monitored user is the one whose location is being updated
                if ($monitored_user_id == $user_id) {
                    if ($current_status == 'inside' && $last_status == 'outside') {
                        $notification_message = "{$user_id} entrou na área '{$area_name}'.";
                    } elseif ($current_status == 'outside' && $last_status == 'inside') {
                        $notification_message = "{$user_id} saiu da área '{$area_name}'.";
                    }

                    // TODO: Implement actual push notification to $admin_id
                    if (!empty($notification_message)) {
                        error_log(
                            "GEOTRACKING_NOTIFICATION to admin {$admin_id}: " .
                            $notification_message
                        );
                    }
                }

                // Update the last_status in the areas table
                $updateAreaStatusStmt = $pdo->prepare(
                    "UPDATE `areas` SET `last_status` = ? WHERE `id` = ?"
                );
                $updateAreaStatusStmt->execute([$current_status, $area_id]);
            }
        }

        // Commit the transaction
        $pdo->commit();
        echo json_encode([
            "success" => "Localização atualizada e geofencing processado."
        ]);

    } catch (PDOException $e) {
        $pdo->rollBack();
        error_log("Erro SQL em update_location.php: " . $e->getMessage());
        echo json_encode(["error" => "Erro SQL: " . $e->getMessage()]);
    } catch (Exception $e) {
        $pdo->rollBack();
        error_log("Erro inesperado em update_location.php: " . $e->getMessage());
        echo json_encode([
            "error" => "Ocorreu um erro inesperado: " . $e->getMessage()
        ]);
    }
} else {
    echo json_encode(["error" => "Método não permitido (use POST)"]);
}
?>

