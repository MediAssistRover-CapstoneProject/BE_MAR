param(
    [string]$MapZip = (Join-Path $env:USERPROFILE 'Downloads\map-4b43a4f2-1d8f-4773-b777-e7fc83bf7fec.zip')
)

$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$envPath = Join-Path $PSScriptRoot '..\.env'
$settings = @{}
Get-Content -LiteralPath $envPath | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim("'", '"')
    }
}
if (-not $settings.ADMIN_USERNAME -or -not $settings.ADMIN_PASSWORD) {
    throw 'Missing local ADMIN_USERNAME or ADMIN_PASSWORD in BE .env'
}

$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (
    @{ username = $settings.ADMIN_USERNAME; password = $settings.ADMIN_PASSWORD; deviceInfo = 'Local demo seed' } | ConvertTo-Json
)
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

function Get-Rows([string]$path) {
    $separator = if ($path.Contains('?')) { '&' } else { '?' }
    $response = Invoke-RestMethod -Uri "$base$path${separator}page=0&size=100" -Headers $headers
    return @($response.content)
}
function Post-Json([string]$path, [object]$body) {
    return Invoke-RestMethod -Uri "$base$path" -Headers $headers -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 12)
}
function Ensure-Record([string]$label, [string]$listPath, [string]$key, [string]$value, [string]$postPath, [object]$body) {
    $existing = Get-Rows $listPath | Where-Object { $_.$key -eq $value } | Select-Object -First 1
    if ($existing) { Write-Host "Existing: $label"; return $existing }
    $created = Post-Json $postPath $body
    Write-Host "Created: $label"
    return $created
}

$legacy = Get-Rows '/api/v1/hospital-information' | Where-Object { $_.infoCategory -eq 'DEMO_LOCAL' -and $_.title -eq 'Bệnh viện MediAssist - dữ liệu mẫu local' } | Select-Object -First 1
if ($legacy -and $legacy.status -ne 'RETIRED') {
    Post-Json "/api/v1/hospital-information/$($legacy.hospitalInformationId)/retire" @{} | Out-Null
    Write-Host 'Retired: invalid demo hospital category from the previous run'
}
$hospital = Ensure-Record 'demo hospital' '/api/v1/hospital-information' 'infoCategory' 'HOSPITAL_PROFILE' '/api/v1/hospital-information' @{
    infoCategory = 'HOSPITAL_PROFILE'
    title = 'Bệnh viện MediAssist - dữ liệu mẫu local'
    content = 'Dữ liệu mẫu để thử giao diện. Không dùng cho chỉ dẫn y tế hoặc điều phối thật.'
    languageCode = 'vi'
    keywordsJson = @('demo', 'local', 'mediassist')
    publicVisible = $true
}
if ($hospital.status -eq 'DRAFT') {
    $hospital = Post-Json "/api/v1/hospital-information/$($hospital.hospitalInformationId)/publish" @{}
    Write-Host 'Published: demo hospital information'
}
$articlePath = "/api/v1/hospital-information/$($hospital.hospitalInformationId)/articles"
$article = Get-Rows $articlePath | Where-Object { $_.articleCode -eq 'DEMO_LOCAL_GUIDE' } | Select-Object -First 1
if (-not $article) {
    $article = Post-Json $articlePath @{
        articleCode = 'DEMO_LOCAL_GUIDE'
        category = 'HUONG_DAN'
        canonicalQuestion = 'Dữ liệu trên trang này có phải dữ liệu vận hành thật không?'
        title = 'Giới thiệu dữ liệu mẫu local'
        answer = 'Đây là dữ liệu mẫu để thử giao diện. Robot chưa kết nối Pi; không có nhiệm vụ thật đang chạy.'
        languageCode = 'vi'
        keywordsJson = @('mẫu', 'local', 'hướng dẫn')
        publicVisible = $true
    }
    Write-Host 'Created: public demo article'
}
if ($article.status -eq 'DRAFT') {
    Post-Json "$articlePath/$($article.articleId)/publish" @{} | Out-Null
    Write-Host 'Published: public demo article'
}

$floor = Ensure-Record 'floor 1' '/api/v1/floors' 'floorCode' 'DEMO_F1' '/api/v1/floors' @{
    hospitalInformationId = $hospital.hospitalInformationId
    floorCode = 'DEMO_F1'
    floorName = 'Tầng 1 - khu mô phỏng'
    levelNo = 1
    displayOrder = 1
    description = 'Tầng mẫu dùng cho bản đồ LiDAR đã cung cấp.'
    active = $true
}

$roomSpecs = @(
    @{ code = 'DEMO_PHARMACY'; name = 'Phòng thuốc'; type = 'PHARMACY'; department = 'KHO_DUOC' },
    @{ code = 'DEMO_RECEPTION'; name = 'Quầy tiếp nhận'; type = 'RECEPTION'; department = 'TIEP_NHAN' },
    @{ code = 'DEMO_LAB'; name = 'Phòng xét nghiệm'; type = 'LAB'; department = 'XET_NGHIEM' }
)
$rooms = @{}
foreach ($spec in $roomSpecs) {
    $rooms[$spec.code] = Ensure-Record $spec.name '/api/v1/rooms' 'roomCode' $spec.code '/api/v1/rooms' @{
        floorId = $floor.floorId
        roomCode = $spec.code
        roomName = $spec.name
        roomType = $spec.type
        departmentCode = $spec.department
        description = 'Phòng mẫu trong môi trường local.'
        active = $true
        publicVisible = $true
    }
}

if (Test-Path -LiteralPath $MapZip) {
    $map = Get-Rows '/api/v1/maps' | Where-Object { $_.mapCode -eq 'DEMO_LIDAR_F1' -and $_.versionNo -eq 1 } | Select-Object -First 1
    if (-not $map) {
        $temp = Join-Path ([IO.Path]::GetTempPath()) ("mediassist-map-" + [guid]::NewGuid().ToString('N'))
        try {
            Expand-Archive -LiteralPath $MapZip -DestinationPath $temp
            $params = 'floorId={0}&mapCode=DEMO_LIDAR_F1&versionNo=1&mapName={1}' -f $floor.floorId, [uri]::EscapeDataString('Bản quét LiDAR mẫu - tầng 1')
            $imported = Invoke-RestMethod -Uri "$base/api/v1/maps/import?$params" -Headers $headers -Method Post -Form @{
                yaml = Get-Item -LiteralPath (Join-Path $temp 'map.yaml')
                pgm = Get-Item -LiteralPath (Join-Path $temp 'map.pgm')
            }
            $map = Invoke-RestMethod -Uri "$base/api/v1/maps/$($imported.mapId)" -Headers $headers
            Write-Host 'Imported: supplied LiDAR YAML/PGM map'
        } finally {
            if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
        }
    } else { Write-Host 'Existing: supplied LiDAR map' }
    if ($map.status -eq 'DRAFT') {
        $map = Post-Json "/api/v1/maps/$($map.mapId)/activate" @{}
        Write-Host 'Activated: LiDAR map'
    }

    $candidates = @(
        @{ code = 'DEMO_POINT_A'; name = 'Điểm giữa hành lang'; x = 2.1; y = -0.1 },
        @{ code = 'DEMO_POINT_B'; name = 'Điểm chờ phía nam'; x = 2.1; y = -0.8 }
    )
    foreach ($point in $candidates) {
        $existing = Get-Rows '/api/v1/endpoints' | Where-Object { $_.mapId -eq $map.mapId -and $_.endpointCode -eq $point.code } | Select-Object -First 1
        if ($existing) { Write-Host "Existing: $($point.name)"; continue }
        try {
            Post-Json "/api/v1/maps/$($map.mapId)/validate-navigation-point" @{ xM = $point.x; yM = $point.y } | Out-Null
            Post-Json '/api/v1/endpoints' @{
                mapId = $map.mapId
                endpointCode = $point.code
                endpointName = $point.name
                endpointType = 'WAYPOINT'
                xM = $point.x
                yM = $point.y
                yawRad = 0
                active = $true
                accessible = $true
                publicVisible = $true
                description = 'Điểm mẫu đã kiểm tra ô trống trên bản đồ LiDAR.'
            } | Out-Null
            Write-Host "Created: $($point.name)"
        } catch {
            Write-Warning "Skipped $($point.code): no verified free point at this coordinate"
        }
    }

    Ensure-Record 'offline demo robot' '/api/v1/robots' 'robotCode' 'DEMO_LOCAL_01' '/api/v1/robots' @{
        robotCode = 'DEMO_LOCAL_01'
        robotName = 'Robot mẫu local - chưa kết nối Pi'
        model = 'MediAssist demo'
        currentMapId = $map.mapId
        protocolVersion = 1
        capabilitiesJson = @('MOVE')
    } | Out-Null

    $points = Get-Rows '/api/v1/endpoints' | Where-Object { $_.mapId -eq $map.mapId -and $_.endpointCode -like 'DEMO_POINT_*' }
    if ($points.Count -ge 2) {
        $pickup = $points | Where-Object endpointCode -eq 'DEMO_POINT_A' | Select-Object -First 1
        $destination = $points | Where-Object endpointCode -eq 'DEMO_POINT_B' | Select-Object -First 1
        if ($pickup -and $destination) {
            $requestHeaders = $headers.Clone()
            $requestHeaders['Idempotency-Key'] = 'mediassist-local-demo-delivery-v1'
            $draft = Invoke-RestMethod -Uri "$base/api/v1/delivery-requests" -Headers $requestHeaders -Method Post -ContentType 'application/json' -Body (@{
                mapId = $map.mapId
                pickupRoomId = $rooms['DEMO_PHARMACY'].roomId
                deliveryRoomId = $rooms['DEMO_RECEPTION'].roomId
                pickupEndpointId = $pickup.endpointId
                deliveryEndpointId = $destination.endpointId
                targetMode = 'ENDPOINT'
                recipientName = 'Người nhận mẫu'
                requiresConfirmation = $true
                deliveryNote = 'Yêu cầu mẫu local; chưa gửi robot.'
                items = @(@{ itemCode = 'DEMO_ITEM_01'; itemName = 'Hộp vật tư mẫu'; itemCategory = 'VATTU'; quantity = 1; unit = 'hộp'; sensitive = $false })
                priority = 10
                note = 'DỮ LIỆU MẪU - GIỮ Ở BẢN NHÁP, KHÔNG GỬI ROBOT'
            } | ConvertTo-Json -Depth 8)
            Write-Host "Ready: demo delivery request remains $($draft.request.status)"
        }
    }
} else {
    Write-Warning "Map ZIP not found: $MapZip. Map and robot were skipped."
}

$staff = Get-Rows '/api/v1/admin/staff' | Where-Object { $_.staffCode -eq 'DEMO_STAFF_01' } | Select-Object -First 1
if (-not $staff) {
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $staff = Post-Json '/api/v1/admin/staff' @{
        username = 'demostafflocal'
        temporaryPassword = $password
        fullName = 'Nhân viên trực mẫu'
        email = 'demostafflocal@example.invalid'
        staffCode = 'DEMO_STAFF_01'
        departmentCode = 'KHO_DUOC'
        jobTitle = 'Nhân viên trực'
    }
    Write-Host 'Created: demo staff (reset its password in Admin before any login)'
} else { Write-Host 'Existing: demo staff' }

$shift = Get-Rows '/api/v1/admin/shifts' | Where-Object { $_.shiftCode -eq 'DEMO_SHIFT_01' } | Select-Object -First 1
if (-not $shift) {
    $start = [DateTimeOffset]::UtcNow.AddDays(1).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $end = [DateTimeOffset]::UtcNow.AddDays(1).AddHours(8).ToString('yyyy-MM-ddTHH:mm:ssZ')
    Post-Json '/api/v1/admin/shifts' @{
        staffProfileId = $staff.staffProfileId
        roomId = $rooms['DEMO_PHARMACY'].roomId
        shiftCode = 'DEMO_SHIFT_01'
        shiftName = 'Ca trực mẫu tại phòng thuốc'
        startsAt = $start
        endsAt = $end
        note = 'Ca trực mẫu local; không phải lịch trực thật.'
        slots = @(@{ sequenceNo = 1; startsAt = $start; endsAt = $end; status = 'AVAILABLE'; note = 'Khung giờ mẫu' })
    } | Out-Null
    Write-Host 'Created: planned demo shift'
} else { Write-Host 'Existing: planned demo shift' }

Write-Host 'Local seed complete. No jobs were dispatched and no alert was fabricated.'
