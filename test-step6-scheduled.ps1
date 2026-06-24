<#
  test-step6-scheduled.ps1 — DoD STEP 6 con delivery PROGRAMMATA (scheduled) + deadlineMinutes.
  Pensato per dimostrare il replay in modo PULITO: prima del volo gli eventi persistiti sono pochi
  e netti (DeliveryRequestCreated, ValidationDeliveryPassed, DeliveryScheduled), cosi' dopo il
  riavvio si vede chiaramente lo stato SCHEDULED ricostruito dagli eventi.

  Uso:
    1) avvia i tre servizi (account 9000, delivery 9002/9003, gateway 8080)
    2) .\test-step6-scheduled.ps1
  Se PowerShell blocca gli script:
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>

$ErrorActionPreference = 'Stop'

# ---- Parametri --------------------------------------------------------------
$gateway        = 'http://localhost:8080'
$account        = 'http://localhost:9000'
$username       = 'marco'
$password       = 'Secret#123'
$scheduleMin    = 2     # la delivery parte tra 10 minuti -> resta SCHEDULED durante il test
$deadlineMin    = 60     # deadline: 60 minuti (deve essere > 0)
$eventStorePaths = @(
    'delivery-service/data/delivery-events.json',
    'data/delivery-events.json',
    './delivery-events.json'
)
# -----------------------------------------------------------------------------

function Invoke-Json($uri, $method, $obj) {
    $body = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 8 } else { $null }
    if ($body) { return Invoke-RestMethod -Uri $uri -Method $method -ContentType 'application/json' -Body $body }
    return Invoke-RestMethod -Uri $uri -Method $method
}

Write-Host "`n=== A) Registrazione ===" -ForegroundColor Cyan
try {
    Invoke-Json "$account/api/v1/accounts" 'Post' @{ username = $username; password = $password } | Out-Null
    Write-Host "Account creato." -ForegroundColor Green
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 409) { Write-Host "Account gia' esistente (409): proseguo." -ForegroundColor Yellow } else { throw }
}

Write-Host "`n=== B) Login via gateway ===" -ForegroundColor Cyan
$login = Invoke-Json "$gateway/api/v1/login" 'Post' @{ username = $username; password = $password }
$sid = $login.sessionId
if (-not $sid) { throw "Nessun sessionId." }
Write-Host "sessionId = $sid" -ForegroundColor Green

# ISO-8601 con offset, formato accettato dal controller (OffsetDateTime.parse)
$scheduledAt = (Get-Date).AddMinutes($scheduleMin).ToString("yyyy-MM-ddTHH:mm:sszzz")

Write-Host "`n=== C) Crea delivery PROGRAMMATA (scheduled tra $scheduleMin min, deadline $deadlineMin min) ===" -ForegroundColor Cyan
$create = Invoke-Json "$gateway/api/v1/user-sessions/$sid/create-delivery" 'Post' @{
    weight           = 2
    startingPlace    = @{ street = 'via Emilia'; number = 9 }
    destinationPlace = @{ street = 'via Veneto'; number = 5 }
    immediate        = $false
    scheduledAt      = $scheduledAt
    deadlineMinutes  = $deadlineMin
}
$did = $create.deliveryId
if (-not $did) { throw "Nessun deliveryId. Risposta: $($create | ConvertTo-Json -Depth 5)" }
Write-Host "deliveryId = $did   (scheduledAt = $scheduledAt)" -ForegroundColor Green

Write-Host "`n=== D) Rileggi PRIMA del riavvio (atteso: SCHEDULED) ===" -ForegroundColor Cyan
$before = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
Write-Host "Stato prima del riavvio: $($before.status)" -ForegroundColor Green
Write-Host ($before | ConvertTo-Json -Depth 6)

Write-Host "`n=== E) Ispeziona l'event store ===" -ForegroundColor Cyan
$found = $false
foreach ($p in $eventStorePaths) {
    if (Test-Path $p) {
        Write-Host "File: $p" -ForegroundColor Green
        $content = Get-Content $p -Raw
        Write-Host $content
        if ($content -match 'EstimatedTimeUpdated') {
            Write-Host "ATTENZIONE: EstimatedTimeUpdated nello store (non dovrebbe esserci)." -ForegroundColor Red
        } else { Write-Host "OK: nessun EstimatedTimeUpdated persistito." -ForegroundColor Green }
        $found = $true; break
    }
}
if (-not $found) {
    Write-Host "Event store non trovato. Cercalo con: Get-ChildItem -Recurse -Filter delivery-events.json" -ForegroundColor Yellow
}

Write-Host "`n=== F) RIAVVIO MANUALE del delivery-service ===" -ForegroundColor Magenta
Write-Host "Ferma SOLO il delivery-service (Ctrl+C) e riavvialo. Lascia su account e gateway." -ForegroundColor Magenta
Read-Host "Premi INVIO dopo aver riavviato il delivery-service"

Write-Host "`n=== G) Rileggi DOPO il riavvio (atteso: ancora SCHEDULED, ricostruito dal replay) ===" -ForegroundColor Cyan
try {
    $after = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
} catch {
    Write-Host "Sessione scaduta, rifaccio login..." -ForegroundColor Yellow
    $login2 = Invoke-Json "$gateway/api/v1/login" 'Post' @{ username = $username; password = $password }
    $sid = $login2.sessionId
    $after = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
}
Write-Host "Stato dopo il riavvio: $($after.status)" -ForegroundColor Green
Write-Host ($after | ConvertTo-Json -Depth 6)

Write-Host "`n=== ESITO ===" -ForegroundColor Cyan
if ($after.status -eq 'SCHEDULED' -and $after.deliveryId -eq $did) {
    Write-Host "OK: delivery PROGRAMMATA ricostruita dal replay (SCHEDULED) dopo il riavvio, senza stato persistito." -ForegroundColor Green
} else {
    Write-Host "Stato inatteso dopo il riavvio: $($after.status). Verifica l'event store e il replay." -ForegroundColor Yellow
}
Write-Host ""
