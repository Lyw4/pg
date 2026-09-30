<#
.SYNOPSIS
    FeedFlow E2E 스위트를 한 번에 실행한다.

.DESCRIPTION
    서버 기동부터 스위트 실행, 결과 요약까지 처리한다.

    기본값으로 실행하면
      1) 별도 DB(data/e2e-demo)로 앱을 8092 포트에 띄운다
      2) demo-e2e / scroll / hotfix / cart-merge 를 순서대로 돌린다
      3) 결과를 표로 요약하고, 띄운 서버를 정리한다

    E2E 는 데이터를 만든다(회원·상담·주문 생성, 입금 확인 처리).
    그래서 기본 DB 는 실제 개발 DB(finalproject)가 아니라 버려도 되는
    data/e2e-demo 다. -DbName finalproject 로 바꾸면 개발 DB 에 기록된다.

.PARAMETER Port
    앱을 띄울 포트. 기본 8092. (8080 은 보통 STS/Eclipse 가 쓰고 있다)

.PARAMETER DbName
    H2 파일 DB 이름. 기본 e2e-demo (data/<이름>.mv.db).

.PARAMETER AdminPassword
    관리자 계정 비밀번호. Step 5·6(관리자 로그인 → 입금확인 → 출고준비)에 필요하다.
    기본 DemoAdmin!23 으로 계정을 만들어 띄운다.

.PARAMETER Headed
    브라우저 창을 띄워 동작을 눈으로 본다. 조작마다 300ms 씩 쉬므로 느리다.

.PARAMETER Target
    이미 떠 있는 서버를 대상으로 할 때 지정한다. 이 경우 서버를 띄우지 않는다.
    예: -Target http://localhost:8080

.PARAMETER Suites
    돌릴 스위트. 기본 all.
    all | demo | scroll | hotfix | cart

.PARAMETER KeepServer
    끝난 뒤에도 서버를 남겨 둔다. 반복 실행할 때 편하다.

.EXAMPLE
    .\run-e2e.ps1
    기본값으로 전체 실행 (8092, 별도 DB, 헤드리스)

.EXAMPLE
    .\run-e2e.ps1 -Headed
    브라우저 창을 띄워 전체 실행

.EXAMPLE
    .\run-e2e.ps1 -Suites scroll
    스크롤 검증만 빠르게

.EXAMPLE
    .\run-e2e.ps1 -Target http://localhost:8080 -AdminPassword '내비밀번호'
    이미 띄워 둔 8080 인스턴스를 대상으로 실행
#>
[CmdletBinding()]
param(
    [int]    $Port = 8092,
    [string] $DbName = 'e2e-demo',
    [string] $AdminPassword = 'DemoAdmin!23',
    [switch] $Headed,
    [string] $Target,
    [ValidateSet('all', 'demo', 'scroll', 'hotfix', 'cart')]
    [string] $Suites = 'all',
    [switch] $KeepServer,
    [string] $AppPath = 'd:\Dev\github\pg-teamfix'
)

$ErrorActionPreference = 'Stop'

# 한글 출력이 깨지지 않게 콘솔 인코딩을 UTF-8 로 맞춘다.
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }

$QaPath = $PSScriptRoot
$startedServer = $false
$serverPid = $null

function Write-Head($text) {
    Write-Host ''
    Write-Host ('─' * 66) -ForegroundColor DarkGray
    Write-Host $text -ForegroundColor Cyan
    Write-Host ('─' * 66) -ForegroundColor DarkGray
}

function Write-Ok($text)   { Write-Host "  OK    $text" -ForegroundColor Green }
function Write-Bad($text)  { Write-Host "  실패  $text" -ForegroundColor Red }
function Write-Info($text) { Write-Host "        $text" -ForegroundColor DarkGray }

function Test-Ready([string] $url) {
    try {
        $r = Invoke-WebRequest -Uri "$url/api/products" -TimeoutSec 5 `
                -UseBasicParsing -ErrorAction Stop
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Get-PortOwner([int] $p) {
    $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if (-not $c) { return $null }
    return ($c.OwningProcess | Select-Object -Unique)[0]
}

# ─────────────────────────── 사전 점검 ───────────────────────────
Write-Head '사전 점검'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Bad 'node 를 찾을 수 없다. Node.js 를 설치해야 한다.'
    exit 1
}
Write-Ok "node $(node -v)"

if (-not (Test-Path (Join-Path $QaPath 'node_modules\playwright'))) {
    Write-Bad 'playwright 가 없다. 아래를 먼저 실행할 것:'
    Write-Info "cd $QaPath ; npm install ; npx playwright install chromium"
    exit 1
}
Write-Ok 'playwright 설치 확인'

if (-not $Target) {
    if (-not (Test-Path (Join-Path $AppPath 'gradlew.bat'))) {
        Write-Bad "앱 경로를 찾을 수 없다: $AppPath"
        Write-Info '-AppPath 로 프로젝트 경로를 지정할 것'
        exit 1
    }
    Write-Ok "앱 경로 $AppPath"
}

# ─────────────────────────── 서버 준비 ───────────────────────────
if ($Target) {
    $Target = $Target.TrimEnd('/')
    Write-Head "기존 서버 사용: $Target"
    if (-not (Test-Ready $Target)) {
        Write-Bad "$Target 에 응답이 없다. 서버가 떠 있는지 확인할 것."
        exit 1
    }
    Write-Ok '응답 확인'
    Write-Info '이 서버의 관리자 비밀번호가 -AdminPassword 와 다르면 Step 5·6 이 실패한다.'
} else {
    $Target = "http://localhost:$Port"
    Write-Head "서버 기동 (포트 $Port / DB data/$DbName)"

    $owner = Get-PortOwner $Port
    if ($owner) {
        $name = (Get-CimInstance Win32_Process -Filter "ProcessId=$owner").Name
        if (Test-Ready $Target) {
            Write-Ok "이미 떠 있는 서버를 재사용한다 (PID $owner / $name)"
            Write-Info '새로 띄우지 않으므로 DB·관리자 비밀번호는 그 서버 설정을 따른다.'
        } else {
            Write-Bad "$Port 를 PID $owner ($name) 가 쓰고 있는데 응답하지 않는다."
            Write-Info '-Port 로 다른 포트를 쓰거나 해당 프로세스를 정리할 것'
            exit 1
        }
    } else {
        $gradleArgs = @(
            "--server.port=$Port"
            "--spring.datasource.url=jdbc:h2:file:./data/$DbName;AUTO_SERVER=TRUE"
            '--feedflow.admin.username=admin'
            "--feedflow.admin.password=$AdminPassword"
            '--feedflow.staff.username=staff@feedflow.co.kr'
            '--feedflow.staff.password=DemoStaff!23'
        ) -join ' '

        Write-Info '기동 중… 처음에는 시드 생성 때문에 1~2분 걸릴 수 있다.'
        Start-Process -FilePath (Join-Path $AppPath 'gradlew.bat') `
            -ArgumentList 'bootRun', "--args=`"$gradleArgs`"" `
            -WorkingDirectory $AppPath `
            -WindowStyle Minimized | Out-Null
        $startedServer = $true

        $deadline = (Get-Date).AddMinutes(4)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 4
            if (Test-Ready $Target) { break }
        }
        if (-not (Test-Ready $Target)) {
            Write-Bad '4분 안에 기동되지 않았다. 최소화된 gradle 창의 로그를 확인할 것.'
            exit 1
        }
        $serverPid = Get-PortOwner $Port
        Write-Ok "기동 완료 (PID $serverPid)"
    }
}

# 포트원 설정 여부를 미리 알려 준다. 없으면 카드·카카오 검증이 실패한다.
$portOneFile = Join-Path $AppPath 'config\application.properties'
if (Test-Path $portOneFile) {
    Write-Ok '포트원 로컬 설정 발견 (config/application.properties) — 카드·카카오페이 검증 가능'
} else {
    Write-Info '포트원 설정이 없다(config/application.properties). 카드·카카오페이 결제창 검증 2건이 실패한다.'
    Write-Info '무통장입금 흐름은 PG 를 쓰지 않으므로 영향이 없다.'
}

# ─────────────────────────── 스위트 실행 ───────────────────────────
$plan = @(
    @{ Key = 'demo';   Label = 'demo-e2e';       Name = 'demo-e2e (풀코스)';       Script = 'demo-e2e.mjs';          Expect = 77 }
    @{ Key = 'scroll'; Label = 'scroll-verify';  Name = 'scroll-verify (스크롤)';   Script = 'scroll-verify.mjs';     Expect = 21 }
    @{ Key = 'hotfix'; Label = 'hotfix-verify';  Name = 'hotfix-verify (핫픽스)';   Script = 'hotfix-verify.mjs';     Expect = 20 }
    @{ Key = 'cart';   Label = 'cart-merge';     Name = 'verify-cart-merge (병합)'; Script = 'verify-cart-merge.mjs'; Expect = 11 }
) | Where-Object { $Suites -eq 'all' -or $_.Key -eq $Suites }

$results = @()

# hotfix-verify 는 URL 을 문자열로 이어 붙이므로 끝 슬래시가 필수다.
# 나머지 셋은 스스로 끝 슬래시를 떼므로 붙여 줘도 무해하다. 그래서 항상 붙인다.
$suiteUrl = "$Target/"

foreach ($suite in $plan) {
    Write-Head $suite.Name

    $argsList = @($suite.Script, $suiteUrl)
    if ($Headed -and $suite.Key -eq 'demo') { $argsList += '--headed' }

    $env:DEMO_ADMIN_PW = $AdminPassword
    $sw = [Diagnostics.Stopwatch]::StartNew()

    # node 가 stderr 에 쓰면 PowerShell 이 NativeCommandError 를 만든다.
    # $ErrorActionPreference='Stop' 상태면 그게 종료 오류가 되어 스위트 하나가
    # 스크립트 전체를 죽인다. 이 구간만 Continue 로 낮춘다.
    $savedEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    Push-Location $QaPath
    try {
        $out = & node @argsList 2>&1 | ForEach-Object { "$_" }
        $nodeExit = $LASTEXITCODE
    } finally {
        Pop-Location
        $ErrorActionPreference = $savedEap
    }
    $sw.Stop()
    $sec = [math]::Round($sw.Elapsed.TotalSeconds, 1)

    $line = ($out | Where-Object { $_ -match 'PASS \d+' } | Select-Object -Last 1)
    if (-not $line) {
        Write-Bad "결과 줄이 없다 (node 종료코드 $nodeExit). 마지막 출력:"
        $out | Select-Object -Last 12 | ForEach-Object { Write-Info $_ }
        $results += [pscustomobject]@{
            Suite = $suite.Label; PASS = 0; FAIL = 1; SKIP = '-'
            Sec = $sec; Result = 'ERROR'
        }
        continue
    }

    $pass = [int]([regex]::Match($line, 'PASS (\d+)').Groups[1].Value)
    $fail = [int]([regex]::Match($line, 'FAIL (\d+)').Groups[1].Value)
    $skipMatch = [regex]::Match($line, 'SKIP (\d+)')
    $skip = if ($skipMatch.Success) { $skipMatch.Groups[1].Value } else { '-' }

    if ($fail -eq 0) { Write-Ok $line } else { Write-Bad $line }
    if ($pass -lt $suite.Expect) {
        Write-Info "예상 $($suite.Expect)건보다 적다. 중간에 끊겼는지 리포트를 확인할 것."
    }

    $results += [pscustomobject]@{
        Suite = $suite.Label
        PASS = $pass
        FAIL = $fail
        SKIP = $skip
        Sec = $sec
        Result = if ($fail -eq 0) { 'PASS' } else { 'FAIL' }
    }
}

# ─────────────────────────── 정리 ───────────────────────────
if ($startedServer -and -not $KeepServer) {
    Write-Head '서버 정리'
    $owner = Get-PortOwner $Port
    if ($owner) {
        Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue
        Write-Ok "포트 $Port 서버 종료 (PID $owner)"
    }
} elseif ($startedServer) {
    Write-Head '서버 유지'
    Write-Info "$Target 계속 사용 가능. 끝낼 때: Stop-Process -Id (Get-NetTCPConnection -LocalPort $Port -State Listen).OwningProcess -Force"
}

# ─────────────────────────── 요약 ───────────────────────────
Write-Head '결과 요약'
$results | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

$totalFail = ($results | Measure-Object -Property FAIL -Sum -ErrorAction SilentlyContinue).Sum
$totalPass = ($results | Measure-Object -Property PASS -Sum -ErrorAction SilentlyContinue).Sum

Write-Host "  대상 $Target"
Write-Host "  리포트 $QaPath\out-demo-e2e.txt (풀코스 상세), out-scroll-verify.txt 등"

if ($totalFail -eq 0) {
    Write-Host ''
    Write-Host "  전체 통과 — PASS $totalPass / FAIL 0" -ForegroundColor Green
    exit 0
} else {
    Write-Host ''
    Write-Host "  실패 있음 — PASS $totalPass / FAIL $totalFail" -ForegroundColor Red
    Write-Host '  상세 원인은 위 리포트 파일의 "실패 항목" 절을 볼 것' -ForegroundColor Yellow
    exit 1
}
