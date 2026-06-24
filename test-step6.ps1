<#
  test-step6.ps1 — DoD STEP 6: event sourcing sul delivery-service
  Verifica: (2) lo stato e' ricostruito dal replay dopo riavvio,
            (3) il file dell'event store contiene una SEQUENZA di eventi (non uno stato),
            (4) il tracking WS dello STEP 5 funziona ancora.

  Uso:
    1) avvia i tre servizi (account 9000, delivery 9002/9003, gateway 8080)
    2) .\test-step6.ps1
  Se PowerShell blocca gli script:
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

  NB: lo step "riavvia il delivery-service" e' MANUALE (lo fai tu tra le due fasi):
      lo script si ferma e ti dice quando riavviarlo.
#>

$ErrorActionPreference = 'Stop'

# ---- Parametri --------------------------------------------------------------
$gateway  = 'http://localhost:8080'
$account  = 'http://localhost:9000'
$username = 'marco'
$password = 'Secret#123'
# percorsi possibili del file event store (a seconda di dove parte il delivery)
$eventStorePaths = @(
    'delivery-service/data/delivery-events.json',
    'data/delivery-events.json',
    './delivery-events.json'
)
# -----------------------------------------------------------------------------

function Invoke-Json($uri, $method, $obj) {
    $body = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 8 } else { $null }
    if ($body) {
        return Invoke-RestMethod -Uri $uri -Method $method -ContentType 'application/json' -Body $body
    }
    return Invoke-RestMethod -Uri $uri -Method $method
}

Write-Host "`n=== A) Registrazione (diretto su account:9000) ===" -ForegroundColor Cyan
try {
    Invoke-Json "$account/api/v1/accounts" 'Post' @{ username = $username; password = $password } | Out-Null
    Write-Host "Account creato." -ForegroundColor Green
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 409) { Write-Host "Account gia' esistente (409): proseguo." -ForegroundColor Yellow }
    else { throw }
}

Write-Host "`n=== B) Login via gateway ===" -ForegroundColor Cyan
$login = Invoke-Json "$gateway/api/v1/login" 'Post' @{ username = $username; password = $password }
$sid = $login.sessionId
if (-not $sid) { throw "Nessun sessionId. Risposta: $($login | ConvertTo-Json -Depth 5)" }
Write-Host "sessionId = $sid" -ForegroundColor Green

Write-Host "`n=== C) Crea delivery via gateway ===" -ForegroundColor Cyan
$create = Invoke-Json "$gateway/api/v1/user-sessions/$sid/create-delivery" 'Post' @{
    weight           = 2
    startingPlace    = @{ street = 'via Emilia'; number = 9 }
    destinationPlace = @{ street = 'via Veneto'; number = 5 }
    immediate        = $true
}
$did = $create.deliveryId
if (-not $did) { throw "Nessun deliveryId. Risposta: $($create | ConvertTo-Json -Depth 5)" }
Write-Host "deliveryId = $did" -ForegroundColor Green

Write-Host "`n=== D) Rileggi la delivery PRIMA del riavvio ===" -ForegroundColor Cyan
$before = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
Write-Host "Stato prima del riavvio: $($before.status)" -ForegroundColor Green
Write-Host ($before | ConvertTo-Json -Depth 6)

Write-Host "`n=== E) Ispeziona l'event store (sequenza di EVENTI, non uno stato) ===" -ForegroundColor Cyan
$found = $false
foreach ($p in $eventStorePaths) {
    if (Test-Path $p) {
        Write-Host "File trovato: $p" -ForegroundColor Green
        $content = Get-Content $p -Raw
        Write-Host $content
        if ($content -match 'EstimatedTimeUpdated') {
            Write-Host "ATTENZIONE: trovato EstimatedTimeUpdated nello store (non dovrebbe esserci)." -ForegroundColor Red
        } else {
            Write-Host "OK: nessun EstimatedTimeUpdated persistito." -ForegroundColor Green
        }
        $found = $true
        break
    }
}
if (-not $found) {
    Write-Host "Event store non trovato nei path noti. Cercalo a mano con:" -ForegroundColor Yellow
    Write-Host "  Get-ChildItem -Recurse -Filter delivery-events.json" -ForegroundColor Yellow
}

Write-Host "`n=== F) RIAVVIO MANUALE del delivery-service ===" -ForegroundColor Magenta
Write-Host "Ora: ferma il delivery-service (Ctrl+C nel suo terminale) e RIAVVIALO." -ForegroundColor Magenta
Write-Host "NON toccare account-service e gateway. Quando il delivery e' di nuovo su 9002, premi INVIO qui." -ForegroundColor Magenta
Read-Host "Premi INVIO dopo aver riavviato il delivery-service"

Write-Host "`n=== G) Rileggi la STESSA delivery DOPO il riavvio (stato dal replay) ===" -ForegroundColor Cyan
# nuova login: il gateway e' rimasto su, ma se la sessione fosse scaduta rifacciamo login
try {
    $after = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
} catch {
    Write-Host "Sessione non valida dopo il riavvio, rifaccio login..." -ForegroundColor Yellow
    $login2 = Invoke-Json "$gateway/api/v1/login" 'Post' @{ username = $username; password = $password }
    $sid = $login2.sessionId
    $after = Invoke-Json "$gateway/api/v1/user-sessions/$sid/deliveries/$did" 'Get' $null
}
Write-Host "Stato dopo il riavvio: $($after.status)" -ForegroundColor Green
Write-Host ($after | ConvertTo-Json -Depth 6)

Write-Host "`n=== ESITO ===" -ForegroundColor Cyan
if ($after.deliveryId -eq $did) {
    Write-Host "OK: la delivery $did e' stata RICOSTRUITA dal replay degli eventi dopo il riavvio." -ForegroundColor Green
    Write-Host "(lo stato puo' essere avanzato rispetto a prima se il volo e' proseguito: e' normale,"
    Write-Host " conta che l'aggregate esista ancora e sia coerente, ricostruito SENZA stato persistito.)"
} else {
    Write-Host "PROBLEMA: la delivery non e' stata ritrovata dopo il riavvio. Controlla l'event store." -ForegroundColor Red
}
Write-Host "`nPer la verifica (4) del tracking WS, lancia test-step5.ps1 e controlla che arrivino gli update.`n" -ForegroundColor Cyan
