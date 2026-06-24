<#
  test-step5.ps1 — DoD STEP 5/6b: tracking client <-> gateway <-> delivery
  Uso:
    1) avvia i tre servizi (account 9000, delivery 9002/9003, gateway 8080)
    2) da PowerShell:  .\test-step5.ps1
  Se PowerShell blocca l'esecuzione di script:
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>

$ErrorActionPreference = 'Stop'   # ferma al primo errore vero (non a un 409 gestito)

# ---- Parametri (cambiali qui se servono) -------------------------------------
$gateway        = 'http://localhost:8080'
$account        = 'http://localhost:9000'
$username       = 'marco'
$password       = 'Secret#123'
$maxWaitSeconds = 300            # attesa massima complessiva per l'arrivo (volo lungo => alza)
# ------------------------------------------------------------------------------

Write-Host "`n=== A) Registrazione sender (diretto su account:9000) ===" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$account/api/v1/accounts" -Method Post `
        -ContentType 'application/json' `
        -Body (@{ username = $username; password = $password } | ConvertTo-Json) | Out-Null
    Write-Host "Account creato." -ForegroundColor Green
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 409) { Write-Host "Account gia' esistente (409): proseguo." -ForegroundColor Yellow }
    else { Write-Host "Registrazione fallita ($code): $($_.Exception.Message)" -ForegroundColor Red; throw }
}

Write-Host "`n=== B) Login via gateway (8080) ===" -ForegroundColor Cyan
$login = Invoke-RestMethod -Uri "$gateway/api/v1/login" -Method Post `
    -ContentType 'application/json' `
    -Body (@{ username = $username; password = $password } | ConvertTo-Json)
$sid = $login.sessionId
if (-not $sid) { throw "Login non ha restituito sessionId. Risposta: $($login | ConvertTo-Json -Depth 5)" }
Write-Host "sessionId = $sid" -ForegroundColor Green

Write-Host "`n=== C) Crea delivery via gateway (indirizzi nel bounding box di Bologna) ===" -ForegroundColor Cyan
$createBody = @{
    weight           = 2
    startingPlace    = @{ street = 'via Emilia'; number = 9 }
    destinationPlace = @{ street = 'via Veneto'; number = 5 }
    immediate        = $true
} | ConvertTo-Json
$create = Invoke-RestMethod -Uri "$gateway/api/v1/user-sessions/$sid/create-delivery" `
    -Method Post -ContentType 'application/json' -Body $createBody
$did = $create.deliveryId
if (-not $did) { throw "Create non ha restituito deliveryId. Risposta: $($create | ConvertTo-Json -Depth 5)" }
Write-Host "deliveryId = $did" -ForegroundColor Green

Write-Host "`n=== D) Avvia tracking via gateway -> verifica webSocketUrl ===" -ForegroundColor Cyan
$track = Invoke-RestMethod -Uri "$gateway/api/v1/user-sessions/$sid/track-delivery" `
    -Method Post -ContentType 'application/json' `
    -Body (@{ deliveryId = $did } | ConvertTo-Json)
$wsUrl = $track.webSocketUrl
Write-Host "webSocketUrl = $wsUrl"
if ($wsUrl -match ':9002') {
    Write-Host "ATTENZIONE: l'URL punta al delivery (9002), NON al gateway. Il relay/rewrite non e' attivo." -ForegroundColor Red
} elseif ($wsUrl -match ':8080') {
    Write-Host "OK: l'URL punta al gateway (8080). Encapsulation rispettata." -ForegroundColor Green
} else {
    Write-Host "URL inatteso: controlla la rewrite nel gateway." -ForegroundColor Yellow
}

Write-Host "`n=== E) Apri la WS verso il GATEWAY e ricevi gli update ===" -ForegroundColor Cyan
$client = [System.Net.WebSockets.ClientWebSocket]::new()
$ct     = [System.Threading.CancellationToken]::None
$client.ConnectAsync([Uri]$wsUrl, $ct).Wait()
Write-Host "WS connessa: $wsUrl (stato: $($client.State))" -ForegroundColor Green

# Primo frame del protocollo: {"deliveryId":"..."} — il gateway lo inoltra al delivery
$open  = (@{ deliveryId = $did } | ConvertTo-Json -Compress)
$bytes = [System.Text.Encoding]::UTF8.GetBytes($open)
$seg   = [System.ArraySegment[byte]]::new($bytes)
$client.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
Write-Host "SENT: $open"

# Loop di ricezione: legge finche' e' il SERVER a chiudere il socket (cosa che, con lo STEP 6b,
# avviene subito DOPO il frame finale DELIVERED). Un deadline complessivo evita attese infinite.
$buffer       = New-Object byte[] 8192
$gotDelivered = $false
$recvCts      = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($maxWaitSeconds))
try {
    while ($client.State -eq 'Open') {
        # Riassembla un singolo messaggio logico (puo' arrivare in piu' frame)
        $ms      = New-Object System.IO.MemoryStream
        $isClose = $false
        $r       = $null
        do {
            $segIn  = [System.ArraySegment[byte]]::new($buffer)
            $result = $client.ReceiveAsync($segIn, $recvCts.Token); $result.Wait()
            $r = $result.Result
            if ($r.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                $isClose = $true
                break
            }
            $ms.Write($buffer, 0, $r.Count)
        } while (-not $r.EndOfMessage)

        if ($isClose) { Write-Host "Server ha chiuso il socket." -ForegroundColor Cyan; break }

        $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        Write-Host ("RECV: " + $text)

        # Parsing null-safe: una frame senza 'status' (es. errore) NON deve interrompere lo script
        $status = $null
        try { $status = ($text | ConvertFrom-Json).status } catch { }
        if ($status -eq 'DELIVERED' -or $status -eq 'ABOLISHED') {
            Write-Host ">>> FRAME FINALE: $status <<<" -ForegroundColor Green
            $gotDelivered = $true
            # NON uscire qui: lascia che sia il server a chiudere, cosi' confermi 'frame prima della chiusura'
        }
    }
} catch {
    if ($recvCts.IsCancellationRequested) {
        Write-Host "Timeout di $maxWaitSeconds s in attesa del frame finale (il drone non e' ancora arrivato?)." -ForegroundColor Yellow
    } else {
        Write-Host "Errore in ricezione: $($_.Exception.Message)" -ForegroundColor Red
    }
}

if ($gotDelivered) {
    Write-Host "Esito: OK, frame finale DELIVERED/ABOLISHED ricevuto PRIMA della chiusura." -ForegroundColor Green
} else {
    Write-Host "Esito: nessun frame finale ricevuto (vedi messaggi sopra)." -ForegroundColor Yellow
}

# Chiusura pulita
try {
    $client.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'bye', $ct).Wait(2000) | Out-Null
} catch { }
$client.Dispose()
$recvCts.Dispose()
Write-Host "`nFine test.`n" -ForegroundColor Cyan
