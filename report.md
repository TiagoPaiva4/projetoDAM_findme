# Relatório Técnico - Funcionalidade de Zonas (Áreas de Controlo)

## Visão Geral

A funcionalidade de Zonas permite aos utilizadores criar áreas geográficas (polígonos) no mapa para monitorizar a localização de si próprios ou de amigos. Quando o utilizador monitorizado entra ou sai da zona, o sistema deteta automaticamente e atualiza a cor do polígono.

---

## Arquitetura do Sistema

```
┌─────────────────┐     HTTP/JSON      ┌─────────────────┐
│   Android App   │ ◄─────────────────► │   Backend PHP   │
│    (Kotlin)     │                     │    (Azure)      │
└─────────────────┘                     └─────────────────┘
                                                │
                                                ▼
                                        ┌─────────────────┐
                                        │   MySQL (Azure) │
                                        └─────────────────┘
```

---

## Ficheiros Principais

### Android (Kotlin)

| Ficheiro | Descrição |
|----------|-----------|
| `Zone.kt` | Modelo de dados da zona (Parcelable) |
| `ZonesActivity.kt` | Lista de zonas, menu de contexto, CRUD |
| `ZonesAdapter.kt` | RecyclerView adapter para mostrar zonas |
| `MapsActivity.kt` | Mapa: criar, ver e editar zonas |
| `activity_zones.xml` | Layout da lista de zonas |
| `activity_maps.xml` | Layout do mapa |
| `item_zone.xml` | Layout de cada item na lista |
| `dialog_create_zone.xml` | Dialog para nome e utilizador alvo |
| `menu_zone_options.xml` | Menu de contexto (Editar, Ativar/Desativar, Eliminar) |

### Backend (PHP)

| Ficheiro | Método | Descrição |
|----------|--------|-----------|
| `create_area.php` | POST | Criar nova zona |
| `get_user_areas.php` | GET | Obter zonas do utilizador |
| `update_area.php` | POST | Atualizar zona existente |
| `delete_area.php` | POST | Eliminar zona |
| `toggle_area_status.php` | POST | Ativar/desativar zona |

---

## Modelo de Dados

### Zone.kt
```kotlin
@Parcelize
data class Zone(
    val id: String,              // ID único da zona
    val name: String,            // Nome da zona
    val adminId: String,         // ID do utilizador que criou
    val associatedUserId: String, // ID do utilizador monitorizado
    val coordinates: List<LatLng>, // Pontos do polígono
    val isActive: Boolean = true  // Se a zona está ativa
) : Parcelable
```

### Tabela `areas` (MySQL)
```sql
id              INT PRIMARY KEY AUTO_INCREMENT
name            VARCHAR         -- Nome da zona
admin_id        INT             -- Quem criou
user_id         INT             -- Quem é monitorizado
area_type       VARCHAR         -- "polygon"
coordinates     LONGTEXT        -- JSON: [{"lat":x,"lng":y},...]
is_active       TINYINT(1)      -- 1=ativa, 0=desativada
last_status     VARCHAR         -- "inside"/"outside"
```

---

## Fluxos Principais

### 1. Criar Zona

```
ZonesActivity (FAB +)
    │
    ▼
MapsActivity (MODE="CREATE_ZONE")
    │
    ├── onMapReady() → mostra controlos de desenho
    ├── setOnMapClickListener → addPolygonPoint()
    │       │
    │       ▼
    │   redrawPolygon() → atualiza polígono no mapa
    │
    ├── btnConfirm → showZoneCreationDialog()
    │       │
    │       ▼
    │   Dialog: nome + spinner (utilizador alvo)
    │       │
    │       ▼
    └── saveZone() → POST /create_area.php
            │
            ▼
        finish() → volta para ZonesActivity
```

### 2. Ver Zona

```
ZonesActivity (click numa zona)
    │
    ▼
MapsActivity (MODE="VIEW_ZONE", ZONE=zone)
    │
    ├── onMapReady() → showZoneOnMap(zone)
    │       │
    │       ├── Desenha polígono (cor baseada em isActive)
    │       │   - Ativa: azul #3A8DDE
    │       │   - Desativada: cinza #808080
    │       │
    │       └── startRealTimeLocationTracking()
    │               │
    │               ├── Se monitorizar a si próprio:
    │               │   startSelfLocationUpdates()
    │               │   (GPS via FusedLocationProviderClient)
    │               │
    │               └── Se monitorizar amigo:
    │                   startFriendLocationPolling()
    │                   (API polling cada 5 segundos)
    │
    └── updateUserLocationOnMap()
            │
            ├── Atualiza posição do marcador
            └── isPointInPolygon() → updateZonePolygonColor()
                    │
                    ├── Dentro: Verde #4CAF50
                    └── Fora: Vermelho #F44336
```

### 3. Editar Zona

```
ZonesActivity (long-press → "Editar")
    │
    ▼
MapsActivity (MODE="EDIT_ZONE", ZONE=zone)
    │
    ├── onMapReady() → loadExistingPolygon(zone)
    │       │
    │       └── Adiciona pontos existentes ao mapa
    │
    ├── Utilizador modifica polígono
    │
    ├── btnConfirm → showZoneCreationDialog()
    │       │
    │       └── Pre-preenche nome e utilizador atual
    │
    └── updateZone() → POST /update_area.php
```

### 4. Eliminar Zona

```
ZonesActivity (long-press → "Eliminar")
    │
    ▼
confirmDeleteZone() → AlertDialog
    │
    ▼
deleteZone() → POST /delete_area.php
    │
    ▼
fetchZones() → atualiza lista
```

### 5. Ativar/Desativar Zona

```
ZonesActivity (long-press → "Ativar/Desativar")
    │
    ▼
toggleZoneStatus() → POST /toggle_area_status.php
    │
    ▼
fetchZones() → atualiza lista (mostra estado visual)
```

---

## Funções-Chave Explicadas

### MapsActivity.kt

#### `addPolygonPoint(latLng: LatLng)`
Adiciona um ponto ao polígono quando o utilizador toca no mapa.
- Adiciona coordenada à lista `polygonPoints`
- Cria marcador azul no ponto
- Chama `redrawPolygon()` para atualizar visualmente

#### `redrawPolygon()`
Atualiza o polígono no mapa.
- Se já existe polígono, atualiza os pontos (`polygon.points = newPoints`)
- Se não existe e há 3+ pontos, cria novo polígono
- Reutiliza o mesmo objeto para evitar artefactos visuais

#### `isPointInPolygon(point, polygon): Boolean`
Algoritmo Ray Casting para detetar se um ponto está dentro do polígono.
- Conta quantas vezes uma linha horizontal cruza as arestas
- Número ímpar = dentro, par = fora

#### `startRealTimeLocationTracking(monitoredUserId)`
Inicia o tracking de localização em tempo real.
- Se monitorizar a si próprio: usa GPS (`LocationRequest` + `LocationCallback`)
- Se monitorizar amigo: faz polling à API cada 5 segundos

#### `updateZonePolygonColor(isUserInside: Boolean)`
Muda a cor do polígono baseado na posição do utilizador.
- Verde (#4CAF50) = utilizador dentro da zona
- Vermelho (#F44336) = utilizador fora da zona

### ZonesActivity.kt

#### `fetchZones()`
Obtém todas as zonas do utilizador via API.
- GET `/get_user_areas.php?user_id=X`
- Parse JSON e cria lista de objetos `Zone`
- Inclui campo `is_active` para estado

#### `showZoneOptionsMenu(zone, anchorView)`
Mostra menu de contexto com PopupMenu.
- Opções: Editar, Ativar/Desativar, Eliminar
- Texto do toggle muda conforme estado atual

### ZonesAdapter.kt

#### `onBindViewHolder()`
Renderiza cada item da lista.
- Mostra inicial, nome e número de pontos
- Indicador visual para zonas desativadas (opacity reduzida, texto vermelho)
- Long-press listener para menu de contexto

---

## Comunicação com API (Volley)

### Padrão usado para POST com JSON:
```kotlin
val request = object : StringRequest(Request.Method.POST, url,
    { response -> /* sucesso */ },
    { error -> /* erro */ }
) {
    override fun getBody() = jsonBody.toString().toByteArray(Charsets.UTF_8)
    override fun getBodyContentType() = "application/json; charset=utf-8"
}
queue.add(request)
```

### Padrão usado para GET:
```kotlin
val request = JsonObjectRequest(Request.Method.GET, url, null,
    { response -> /* parse JSON */ },
    { error -> /* erro */ }
)
queue.add(request)
```

---

## Tecnologias Utilizadas

- **Android SDK** (API 24-36)
- **Kotlin** com Parcelize para serialização
- **Google Maps SDK** para mapas e polígonos
- **Google Play Services Location** para GPS
- **Volley** para HTTP requests
- **PHP** (backend)
- **MySQL** (Azure)

---

## Diagrama de Classes Simplificado

```
┌─────────────────────────────────────────────────────────┐
│                     ZonesActivity                        │
├─────────────────────────────────────────────────────────┤
│ - zonesRecyclerView: RecyclerView                       │
│ - zonesAdapter: ZonesAdapter                            │
│ - zonesList: MutableList<Zone>                          │
├─────────────────────────────────────────────────────────┤
│ + fetchZones()                                          │
│ + showZoneOptionsMenu(zone, view)                       │
│ + deleteZone(zone)                                      │
│ + toggleZoneStatus(zone)                                │
│ + openEditZone(zone)                                    │
└─────────────────────────────────────────────────────────┘
                          │
                          │ usa
                          ▼
┌─────────────────────────────────────────────────────────┐
│                     ZonesAdapter                         │
├─────────────────────────────────────────────────────────┤
│ - zones: List<Zone>                                     │
│ - onZoneClick: (Zone) -> Unit                           │
│ - onZoneLongClick: (Zone, View) -> Unit                 │
├─────────────────────────────────────────────────────────┤
│ + onBindViewHolder() - renderiza item                   │
└─────────────────────────────────────────────────────────┘
                          │
                          │ renderiza
                          ▼
┌─────────────────────────────────────────────────────────┐
│                        Zone                              │
├─────────────────────────────────────────────────────────┤
│ + id: String                                            │
│ + name: String                                          │
│ + adminId: String                                       │
│ + associatedUserId: String                              │
│ + coordinates: List<LatLng>                             │
│ + isActive: Boolean                                     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                     MapsActivity                         │
├─────────────────────────────────────────────────────────┤
│ - mMap: GoogleMap                                       │
│ - isCreateMode / isViewMode / isEditMode: Boolean       │
│ - polygonPoints: MutableList<LatLng>                    │
│ - currentPolygon: Polygon?                              │
│ - zonePolygon: Polygon?                                 │
├─────────────────────────────────────────────────────────┤
│ + onMapReady(googleMap)                                 │
│ + addPolygonPoint(latLng)                               │
│ + redrawPolygon()                                       │
│ + clearPolygon()                                        │
│ + showZoneOnMap(zone)                                   │
│ + isPointInPolygon(point, polygon)                      │
│ + startRealTimeLocationTracking(userId)                 │
│ + saveZone(name, targetUserId)                          │
│ + updateZone(zoneId, name, targetUserId)                │
└─────────────────────────────────────────────────────────┘
```

---

## Resumo para Defesa

1. **O que é**: Sistema de geofencing que permite criar zonas no mapa e monitorizar localização

2. **Como funciona a criação**: Utilizador toca no mapa para adicionar pontos, forma polígono, guarda com nome e pessoa a monitorizar

3. **Como funciona a visualização**: Mostra polígono + localização em tempo real, cor muda conforme posição (verde=dentro, vermelho=fora)

4. **CRUD completo**: Criar, Ler, Atualizar, Eliminar + Ativar/Desativar

5. **Algoritmo chave**: Ray Casting para detetar se ponto está dentro do polígono

6. **Tempo real**: GPS local para si próprio, polling API para amigos (cada 5 segundos)
