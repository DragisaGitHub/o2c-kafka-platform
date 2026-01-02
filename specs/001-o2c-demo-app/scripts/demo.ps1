<#
O2C Demo Script (PowerShell)

Goal: Provide copy/paste friendly commands to exercise US1–US4 via REST.
- Uses curl.exe (not the PowerShell curl alias).
- Prints X-Correlation-Id from response headers when available.

Prereqs:
- Docker Desktop running
- Repo root: run this script from the repository root

Local ports:
- order-service:   http://localhost:8082
- checkout-service: http://localhost:8081
- payment-service:  http://localhost:8083
- web client:       http://localhost:5173
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$baseOrder = 'http://localhost:8082'
$baseCheckout = 'http://localhost:8081'
$basePayment = 'http://localhost:8083'
$baseFrontend = 'http://localhost:5173'

function Require-Command([string]$name, [string]$hint) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $name. $hint"
  }
}

function Write-Section([string]$title) {
  Write-Host ''
  Write-Host ('=' * 80)
  Write-Host $title
  Write-Host ('=' * 80)
}

function Get-CorrelationIdFromHeaders([string[]]$headerLines) {
  foreach ($line in $headerLines) {
    if ($line -match '^X-Correlation-Id:\s*(.+)\s*$') {
      return $Matches[1]
    }
  }
  return $null
}

function Invoke-CurlJson {
  param(
    [Parameter(Mandatory = $true)] [ValidateSet('GET','POST')] [string] $Method,
    [Parameter(Mandatory = $true)] [string] $Url,
    [Parameter(Mandatory = $false)] [string] $BodyJson,
    [Parameter(Mandatory = $false)] [string] $CorrelationId
  )

  $tmpBody = [System.IO.Path]::GetTempFileName()

  $args = @('-sS', '-D', '-', '-o', $tmpBody, '-X', $Method)

  if ($CorrelationId) {
    $args += @('-H', "X-Correlation-Id: $CorrelationId")
  }

  $args += @('-H', 'Accept: application/json')

  if ($Method -eq 'POST') {
    $args += @('-H', 'Content-Type: application/json')
    if ($BodyJson) {
      $args += @('--data', $BodyJson)
    } else {
      $args += @('--data', '{}')
    }
  }

  $args += $Url

  $rawHeaders = & curl.exe @args
  $headerLines = $rawHeaders -split "`r?`n"
  $cid = Get-CorrelationIdFromHeaders $headerLines

  $bodyText = Get-Content -Raw -Encoding UTF8 $tmpBody
  Remove-Item -Force $tmpBody -ErrorAction SilentlyContinue

  $bodyJsonObj = $null
  if ($bodyText -and $bodyText.Trim().Length -gt 0) {
    try { $bodyJsonObj = $bodyText | ConvertFrom-Json } catch { $bodyJsonObj = $null }
  }

  return [pscustomobject]@{
    CorrelationId = $cid
    BodyText = $bodyText
    Json = $bodyJsonObj
    Headers = $headerLines
  }
}

Write-Section 'Step 0: Verify required commands exist'
Require-Command -name 'curl.exe' -hint 'Windows includes curl.exe in modern builds; ensure it is available on PATH.'
Require-Command -name 'docker' -hint 'Install Docker Desktop and ensure docker is on PATH.'

Write-Section 'Step 1: Health checks'
Write-Host 'GET /actuator/health (expect {"status":"UP"})'
$healthOrder = Invoke-CurlJson -Method GET -Url "$baseOrder/actuator/health"
Write-Host "order-service X-Correlation-Id: $($healthOrder.CorrelationId)"
Write-Host $healthOrder.BodyText

$healthCheckout = Invoke-CurlJson -Method GET -Url "$baseCheckout/actuator/health"
Write-Host "checkout-service X-Correlation-Id: $($healthCheckout.CorrelationId)"
Write-Host $healthCheckout.BodyText

$healthPayment = Invoke-CurlJson -Method GET -Url "$basePayment/actuator/health"
Write-Host "payment-service X-Correlation-Id: $($healthPayment.CorrelationId)"
Write-Host $healthPayment.BodyText

Write-Host ''
Write-Host 'If health checks fail:'
Write-Host '  - Start infra: docker compose -f docker/docker-compose.local.yml up -d'
Write-Host '  - Start services: ./gradlew.bat :order-service:bootRun (etc.)'

Write-Section 'Step 2: US1 - Create order (success)'
$customerId = [Guid]::NewGuid().ToString()
$createOkBody = (@{
  customerId = $customerId
  totalAmount = 123.45
  currency = 'EUR'
} | ConvertTo-Json -Compress)

$respOk = Invoke-CurlJson -Method POST -Url "$baseOrder/orders" -BodyJson $createOkBody
Write-Host "X-Correlation-Id: $($respOk.CorrelationId)"
Write-Host "Expected JSON fields: orderId, status=CREATED, correlationId"
Write-Host $respOk.BodyText

if (-not $respOk.Json -or -not $respOk.Json.orderId) {
  throw 'Create order (OK) did not return an orderId.'
}

$orderIdOk = [string]$respOk.Json.orderId
Write-Host "Captured orderIdOk = $orderIdOk"

Write-Section 'Step 3: US1/US4 setup - Create order (currency FAIL to force payment failure)'
$createFailBody = (@{
  customerId = ([Guid]::NewGuid().ToString())
  totalAmount = 10.00
  currency = 'FAIL'
} | ConvertTo-Json -Compress)

$respFail = Invoke-CurlJson -Method POST -Url "$baseOrder/orders" -BodyJson $createFailBody
Write-Host "X-Correlation-Id: $($respFail.CorrelationId)"
Write-Host "Expected: orderId + correlationId; payment should later fail"
Write-Host $respFail.BodyText

if (-not $respFail.Json -or -not $respFail.Json.orderId) {
  throw 'Create order (FAIL) did not return an orderId.'
}

$orderIdFail = [string]$respFail.Json.orderId
Write-Host "Captured orderIdFail = $orderIdFail"

Write-Section 'Step 4: US2 - List/search orders'
Write-Host 'GET /orders (expect a JSON array of OrderSummaryDto)'
$ordersList = Invoke-CurlJson -Method GET -Url "$baseOrder/orders?limit=10"
Write-Host "X-Correlation-Id: $($ordersList.CorrelationId)"
if ($ordersList.Json -is [System.Array]) {
  $ids = @($ordersList.Json | ForEach-Object { $_.orderId } | Where-Object { $_ } | Select-Object -First 5)
  Write-Host "OrderIds (first 5): $($ids -join ', ')"
} else {
  Write-Host $ordersList.BodyText
}

Write-Section 'Step 5: US3 - Order details'
Write-Host 'GET /orders/{orderId}'
$detailsOk = Invoke-CurlJson -Method GET -Url "$baseOrder/orders/$([uri]::EscapeDataString($orderIdOk))"
Write-Host "X-Correlation-Id: $($detailsOk.CorrelationId)"
Write-Host $detailsOk.BodyText

Write-Section 'Step 6: US2 - Batch status endpoints'
Write-Host 'GET /checkouts/status?orderIds=...'
$checkoutStatus = Invoke-CurlJson -Method GET -Url "$baseCheckout/checkouts/status?orderIds=$orderIdOk,$orderIdFail"
Write-Host "X-Correlation-Id: $($checkoutStatus.CorrelationId)"
Write-Host $checkoutStatus.BodyText

Write-Host ''
Write-Host 'GET /payments/status?orderIds=...'
$paymentStatus = Invoke-CurlJson -Method GET -Url "$basePayment/payments/status?orderIds=$orderIdOk,$orderIdFail"
Write-Host "X-Correlation-Id: $($paymentStatus.CorrelationId)"
Write-Host $paymentStatus.BodyText

Write-Section 'Step 7: US3 - Timeline endpoints'
Write-Host 'GET /checkouts/{orderId}/timeline'
$checkoutTimelineOk = Invoke-CurlJson -Method GET -Url "$baseCheckout/checkouts/$([uri]::EscapeDataString($orderIdOk))/timeline"
Write-Host "X-Correlation-Id: $($checkoutTimelineOk.CorrelationId)"
Write-Host $checkoutTimelineOk.BodyText

Write-Host ''
Write-Host 'GET /payments/{orderId}/timeline'
$paymentTimelineFail = Invoke-CurlJson -Method GET -Url "$basePayment/payments/$([uri]::EscapeDataString($orderIdFail))/timeline"
Write-Host "X-Correlation-Id: $($paymentTimelineFail.CorrelationId)"
Write-Host $paymentTimelineFail.BodyText

Write-Host ''
Write-Host 'Repeat details + timelines for the FAIL orderId'
$detailsFail = Invoke-CurlJson -Method GET -Url "$baseOrder/orders/$([uri]::EscapeDataString($orderIdFail))"
Write-Host "X-Correlation-Id: $($detailsFail.CorrelationId)"
Write-Host $detailsFail.BodyText

$checkoutTimelineFail = Invoke-CurlJson -Method GET -Url "$baseCheckout/checkouts/$([uri]::EscapeDataString($orderIdFail))/timeline"
Write-Host "X-Correlation-Id: $($checkoutTimelineFail.CorrelationId)"
Write-Host $checkoutTimelineFail.BodyText

Write-Section 'Step 8: US4 - Retry payment (idempotent)'
$retryRequestId = [Guid]::NewGuid().ToString()
$retryBody = (@{
  orderId = $orderIdFail
  retryRequestId = $retryRequestId
} | ConvertTo-Json -Compress)

Write-Host "POST /payments/{orderId}/retry (retryRequestId=$retryRequestId)"
$retryResp1 = Invoke-CurlJson -Method POST -Url "$basePayment/payments/$([uri]::EscapeDataString($orderIdFail))/retry" -BodyJson $retryBody
Write-Host "X-Correlation-Id: $($retryResp1.CorrelationId)"
Write-Host 'Expected: 202 ACCEPTED (first time) with status=ACCEPTED'
Write-Host $retryResp1.BodyText

Write-Host ''
Write-Host 'Repeat the same retryRequestId (expected: 200 ALREADY_ACCEPTED and no double-enqueue)'
$retryResp2 = Invoke-CurlJson -Method POST -Url "$basePayment/payments/$([uri]::EscapeDataString($orderIdFail))/retry" -BodyJson $retryBody
Write-Host "X-Correlation-Id: $($retryResp2.CorrelationId)"
Write-Host $retryResp2.BodyText

Write-Host ''
Write-Host 'Idempotency note: second call should NOT create a duplicate attempt for the same retryRequestId.'

Write-Section 'Step 9: Re-check timelines after a short wait'
Write-Host 'Sleeping 5 seconds to allow async processing...'
Start-Sleep -Seconds 5

$paymentTimelineFail2 = Invoke-CurlJson -Method GET -Url "$basePayment/payments/$([uri]::EscapeDataString($orderIdFail))/timeline"
Write-Host "X-Correlation-Id: $($paymentTimelineFail2.CorrelationId)"
Write-Host $paymentTimelineFail2.BodyText

Write-Section 'Open browser pages'
Write-Host "Create Order: $baseFrontend/create"
Write-Host "Orders List:   $baseFrontend/orders"
Write-Host "Order Details (OK):   $baseFrontend/orders/$orderIdOk"
Write-Host "Order Details (FAIL): $baseFrontend/orders/$orderIdFail"
