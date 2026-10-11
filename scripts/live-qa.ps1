# Creates uniquely named QA-only records. It never deletes existing data or prints credentials.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081'
$run = [guid]::NewGuid().ToString('N').Substring(0, 10)
$passwordBytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($passwordBytes) } finally { $random.Dispose() }
$password = [Convert]::ToBase64String($passwordBytes) + 'Qa9!'
$emailA = "finstack-live-qa-a-$run@example.invalid"
$emailB = "finstack-live-qa-b-$run@example.invalid"
$month = [DateTime]::UtcNow.ToString('yyyy-MM') + '-01'
$day = [DateTime]::UtcNow.ToString('yyyy-MM-dd')
$normalTime = "${day}T12:00:00Z"
$oddTime = "${day}T02:00:00Z"

function Invoke-Api($method, $path, $body, $token, $key) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    if ($key) { $headers['Idempotency-Key'] = $key }
    $args = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 20; ErrorAction = 'Stop' }
    if ($null -ne $body) {
        $args['ContentType'] = 'application/json'
        $args['Body'] = ConvertTo-Json -InputObject $body -Depth 12 -Compress
    }
    try {
        $response = Invoke-WebRequest @args
        $parsed = if ($response.Content) { ConvertFrom-Json -InputObject $response.Content } else { $null }
        return @{ status = [int]$response.StatusCode; body = $parsed }
    } catch {
        if (-not $_.Exception.Response) { throw "${method} ${path}: network failure" }
        $response = $_.Exception.Response
        $stream = $response.GetResponseStream()
        $content = if ($stream) { (New-Object System.IO.StreamReader($stream)).ReadToEnd() } else { '' }
        $parsed = try { if ($content) { ConvertFrom-Json -InputObject $content } } catch { $null }
        return @{ status = [int]$response.StatusCode; body = $parsed }
    }
}

function Check($label, $actual, $expected) {
    if ($actual -ne $expected) { throw "FAIL $label (expected $expected, got $actual)" }
    Write-Output "PASS $label ($actual)"
}
function EqualMoney($label, $actual, $expected) {
    if ([decimal]$actual -ne [decimal]$expected) { throw "FAIL $label (expected $expected, got $actual)" }
    Write-Output "PASS $label ($actual)"
}
function EqualMoneyNonfatal($label, $actual, $expected) {
    if ([decimal]$actual -ne [decimal]$expected) {
        Write-Output "FAIL $label (expected $expected, got $actual)"
        $script:analyticsFailure = $true
    } else { Write-Output "PASS $label ($actual)" }
}
function CheckNonfatal($label, $actual, $expected) {
    if ($actual -ne $expected) {
        Write-Output "FAIL $label (expected $expected, got $actual)"
        $script:liveFailure = $true
    } else { Write-Output "PASS $label ($actual)" }
}
function TransactionBody($accountId, $categoryId, $type, $amount, $time, $description) {
    return @{ accountId = $accountId; categoryId = $categoryId; transactionType = $type; amount = $amount;
        merchant = $null; description = $description; paymentMethod = $null; transactionTime = $time;
        status = 'COMPLETED'; reference = $null }
}

Write-Output "QA run $run (new records retained; credentials withheld)"
$registrationA = Invoke-Api 'POST' '/api/v1/auth/register' @{ firstName = 'FinStack Live'; lastName = 'QA A'; email = $emailA; password = $password } $null $null
Check 'register A' $registrationA.status 201
Check 'A initially unverified without SMTP' $registrationA.body.user.emailVerified $false
$loginA = Invoke-Api 'POST' '/api/v1/auth/login' @{ email = $emailA; password = $password } $null $null
Check 'login A' $loginA.status 200
$tokenA = $loginA.body.accessToken
if (-not $tokenA) { throw 'FAIL login A: missing access token' }
Check 'current user A' (Invoke-Api 'GET' '/api/v1/users/me' $null $tokenA $null).status 200

$accountA = Invoke-Api 'POST' '/api/v1/accounts' @{ name = "QA Savings $run"; accountType = 'SAVINGS_ACCOUNT'; institutionName = 'FinStack QA'; currency = 'INR'; currentBalance = 1000; creditLimit = $null } $tokenA $null
Check 'create account A' $accountA.status 201
$accountIdA = $accountA.body.id
$categoryA = Invoke-Api 'POST' '/api/v1/categories' @{ name = "QA Expense $run"; categoryType = 'EXPENSE' } $tokenA $null
Check 'create category A' $categoryA.status 201
$categoryIdA = $categoryA.body.id

$incomeBody = TransactionBody $accountIdA $null 'INCOME' 200 $normalTime 'QA idempotent income'
$key = "finstack-qa-$run"
$income1 = Invoke-Api 'POST' '/api/v1/transactions' $incomeBody $tokenA $key
Check 'create keyed income' $income1.status 201
$income2 = Invoke-Api 'POST' '/api/v1/transactions' $incomeBody $tokenA $key
Check 'replay same key' $income2.status 201
Check 'replay returns same transaction ID' $income2.body.id $income1.body.id
$changedBody = TransactionBody $accountIdA $null 'INCOME' 201 $normalTime 'QA idempotent income'
$conflict = Invoke-Api 'POST' '/api/v1/transactions' $changedBody $tokenA $key
Check 'same key different payload' $conflict.status 409
Check 'conflict error code' $conflict.body.code 'IDEMPOTENCY_KEY_CONFLICT'
$accountReadA = Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenA $null
EqualMoney 'one income balance effect' $accountReadA.body.currentBalance 1200
$transactionsA = Invoke-Api 'GET' '/api/v1/transactions?page=0&size=20' $null $tokenA $null
Check 'one transaction after replay' $transactionsA.body.totalElements 1

$budgetA = Invoke-Api 'POST' '/api/v1/budgets' @{ name = "QA Monthly $run"; budgetMonth = $month; totalLimit = 100; warningThresholdPercent = 80; categoryLimits = @(@{ categoryId = $categoryIdA; limit = 100 }) } $tokenA $null
Check 'create budget A' $budgetA.status 201
$budgetIdA = $budgetA.body.id
$expense1 = Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdA $categoryIdA 'EXPENSE' 90 $normalTime 'QA budget warning') $tokenA $null
Check 'create completed expense 90' $expense1.status 201
$expense2 = Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdA $categoryIdA 'EXPENSE' 20 $normalTime 'QA budget exceeded') $tokenA $null
Check 'create completed expense 20' $expense2.status 201
$largeIncome = Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdA $null 'INCOME' 100000 $oddTime 'QA high-risk rule check') $tokenA $null
Check 'create high-risk eligible income' $largeIncome.status 201
EqualMoney 'balance after all creates' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenA $null).body.currentBalance 101090

$progress = Invoke-Api 'GET' "/api/v1/budgets/$budgetIdA/progress" $null $tokenA $null
Check 'budget progress' $progress.status 200
EqualMoney 'overall budget spending' $progress.body.totalSpent 110
EqualMoney 'category budget spending' $progress.body.categories[0].spent 110
$alertsA = Invoke-Api 'GET' '/api/v1/alerts?page=0&size=2' $null $tokenA $null
Check 'alert pagination' $alertsA.status 200
if ($alertsA.body.totalElements -lt 3 -or $alertsA.body.content.Count -gt 2) { throw 'FAIL alert pagination/count' }
Write-Output "PASS alert pagination/count ($($alertsA.body.totalElements) total, $($alertsA.body.content.Count) on page)"
$allAlertsA = Invoke-Api 'GET' '/api/v1/alerts?page=0&size=20' $null $tokenA $null
$types = @($allAlertsA.body.content | ForEach-Object { $_.alertType })
foreach ($type in @('BUDGET_WARNING', 'BUDGET_EXCEEDED', 'FRAUD_SUSPECTED')) {
    if ($types -notcontains $type) { throw "FAIL missing alert type $type" }
    Write-Output "PASS alert type $type"
}
$fraudA = Invoke-Api 'GET' '/api/v1/fraud/assessments?page=0&size=20' $null $tokenA $null
Check 'fraud assessments list' $fraudA.status 200
$highAssessment = @($fraudA.body.content | Where-Object { $_.transactionId -eq $largeIncome.body.id })
if ($highAssessment.Count -ne 1 -or $highAssessment[0].riskLevel -ne 'HIGH') { throw 'FAIL persisted high-risk assessment' }
Write-Output "PASS persisted high-risk assessment (score $($highAssessment[0].riskScore))"
Check 'fraud by transaction' (Invoke-Api 'GET' "/api/v1/fraud/transactions/$($largeIncome.body.id)" $null $tokenA $null).status 200
$fraudSummaryA = Invoke-Api 'GET' '/api/v1/fraud/summary' $null $tokenA $null
if ($fraudSummaryA.body.highRisk -lt 1) { throw 'FAIL fraud summary high risk' }
Write-Output "PASS fraud summary (high risk $($fraudSummaryA.body.highRisk))"

$summaryA = Invoke-Api 'GET' '/api/v1/dashboard/summary' $null $tokenA $null
Check 'dashboard summary A' $summaryA.status 200
EqualMoney 'dashboard balance before cancel' $summaryA.body.totalBalance 101090
EqualMoney 'dashboard income before cancel' $summaryA.body.totalIncome 100200
EqualMoney 'dashboard expenses before cancel' $summaryA.body.totalExpenses 110
if ($summaryA.body.unreadAlerts -lt 3) { throw 'FAIL dashboard unread alert count' }
Write-Output "PASS dashboard unread alert count ($($summaryA.body.unreadAlerts))"
$analyticsA = Invoke-Api 'GET' '/api/v1/dashboard/analytics' $null $tokenA $null
Check 'dashboard analytics A' $analyticsA.status 200
$currentMonth = @($analyticsA.body.cashFlowTrend | Where-Object { $_.month -eq $month })
if ($currentMonth.Count -ne 1) { throw 'FAIL current-month cash flow row' }
EqualMoneyNonfatal 'analytics current-month income' $currentMonth[0].income 100200
EqualMoneyNonfatal 'analytics current-month expenses' $currentMonth[0].expenses 110
$categorySpending = @($analyticsA.body.categorySpending | Where-Object { $_.categoryId -eq $categoryIdA })
if ($categorySpending.Count -ne 1) { throw 'FAIL QA category analytics row' }
EqualMoney 'analytics QA category spending' $categorySpending[0].amount 110

$registrationB = Invoke-Api 'POST' '/api/v1/auth/register' @{ firstName = 'FinStack Live'; lastName = 'QA B'; email = $emailB; password = $password } $null $null
Check 'register B' $registrationB.status 201
$loginB = Invoke-Api 'POST' '/api/v1/auth/login' @{ email = $emailB; password = $password } $null $null
Check 'login B' $loginB.status 200
$tokenB = $loginB.body.accessToken
$accountB = Invoke-Api 'POST' '/api/v1/accounts' @{ name = "QA B Savings $run"; accountType = 'SAVINGS_ACCOUNT'; institutionName = $null; currency = 'INR'; currentBalance = 100; creditLimit = $null } $tokenB $null
Check 'create account B' $accountB.status 201
$accountIdB = $accountB.body.id
$sameKeyB = Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdB $null 'INCOME' 7 $normalTime 'QA same key other owner') $tokenB $key
Check 'same key accepted for B' $sameKeyB.status 201
$noKeyBody = TransactionBody $accountIdB $null 'INCOME' 5 $normalTime 'QA legacy unkeyed request'
Check 'unkeyed POST first' (Invoke-Api 'POST' '/api/v1/transactions' $noKeyBody $tokenB $null).status 201
Check 'unkeyed POST second' (Invoke-Api 'POST' '/api/v1/transactions' $noKeyBody $tokenB $null).status 201
EqualMoney 'B balance proves independent key and legacy behavior' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdB" $null $tokenB $null).body.currentBalance 117
Check 'B transaction count' (Invoke-Api 'GET' '/api/v1/transactions?page=0&size=20' $null $tokenB $null).body.totalElements 3

Check 'B cannot read A account' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenB $null).status 404
Check 'B cannot update A account' (Invoke-Api 'PUT' "/api/v1/accounts/$accountIdA" @{ name = 'Forbidden QA edit'; accountType = 'SAVINGS_ACCOUNT'; institutionName = $null; currency = 'INR'; creditLimit = $null } $tokenB $null).status 404
Check 'B cannot read A transaction' (Invoke-Api 'GET' "/api/v1/transactions/$($income1.body.id)" $null $tokenB $null).status 404
Check 'B cannot read A budget' (Invoke-Api 'GET' "/api/v1/budgets/$budgetIdA" $null $tokenB $null).status 404
CheckNonfatal 'B cannot read A fraud result' (Invoke-Api 'GET' "/api/v1/fraud/transactions/$($largeIncome.body.id)" $null $tokenB $null).status 404
Check 'B cannot mark A alert read' (Invoke-Api 'PATCH' "/api/v1/alerts/$($allAlertsA.body.content[0].id)/read" $null $tokenB $null).status 404
$analyticsB = Invoke-Api 'GET' '/api/v1/dashboard/analytics' $null $tokenB $null
Check 'dashboard analytics B' $analyticsB.status 200
$currentMonthB = @($analyticsB.body.cashFlowTrend | Where-Object { $_.month -eq $month })
if ($currentMonthB.Count -ne 1) { throw 'FAIL B current-month cash flow row' }
EqualMoneyNonfatal 'B income isolated from A' $currentMonthB[0].income 17
Check 'B alert list isolated from A' (Invoke-Api 'GET' '/api/v1/alerts?page=0&size=20' $null $tokenB $null).body.totalElements 0
Check 'B fraud list isolated from A' (Invoke-Api 'GET' '/api/v1/fraud/assessments?page=0&size=20' $null $tokenB $null).body.totalElements 3
Check 'B cannot transact on A account' (Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdA $null 'INCOME' 1 $normalTime 'Forbidden QA write') $tokenB $null).status 404
Check 'B cannot use A custom category' (Invoke-Api 'POST' '/api/v1/transactions' (TransactionBody $accountIdB $categoryIdA 'EXPENSE' 1 $normalTime 'Forbidden QA category') $tokenB $null).status 404
Check 'B cannot update A transaction' (Invoke-Api 'PUT' "/api/v1/transactions/$($income1.body.id)" @{ accountId = $accountIdA; categoryId = $null; transactionType = 'INCOME'; amount = 200; merchant = $null; description = 'Forbidden QA edit'; paymentMethod = $null; transactionTime = $normalTime; status = 'COMPLETED'; reference = $null } $tokenB $null).status 404
Check 'B cannot update A budget' (Invoke-Api 'PUT' "/api/v1/budgets/$budgetIdA" @{ name = 'Forbidden QA edit'; budgetMonth = $month; totalLimit = 100; warningThresholdPercent = 80; categoryLimits = @() } $tokenB $null).status 404

Check 'mark A alert read' (Invoke-Api 'PATCH' "/api/v1/alerts/$($allAlertsA.body.content[0].id)/read" $null $tokenA $null).status 204
$summaryAfterRead = Invoke-Api 'GET' '/api/v1/dashboard/summary' $null $tokenA $null
Check 'unread count after explicit read' $summaryAfterRead.body.unreadAlerts ($summaryA.body.unreadAlerts - 1)
$loginAgainA = Invoke-Api 'POST' '/api/v1/auth/login' @{ email = $emailA; password = $password } $null $null
Check 'login A after B session' $loginAgainA.status 200
Check 'A account visible after new login' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $loginAgainA.body.accessToken $null).status 200
Check 'B token still cannot see A account' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenB $null).status 404

Check 'cancel completed expense' (Invoke-Api 'DELETE' "/api/v1/transactions/$($expense2.body.id)" $null $tokenA $null).status 204
EqualMoney 'balance after cancellation' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenA $null).body.currentBalance 101110
Check 'repeat cancellation' (Invoke-Api 'DELETE' "/api/v1/transactions/$($expense2.body.id)" $null $tokenA $null).status 204
EqualMoney 'idempotent cancellation balance' (Invoke-Api 'GET' "/api/v1/accounts/$accountIdA" $null $tokenA $null).body.currentBalance 101110
EqualMoney 'budget spending after cancellation' (Invoke-Api 'GET' "/api/v1/budgets/$budgetIdA/progress" $null $tokenA $null).body.totalSpent 90
EqualMoney 'dashboard expenses after cancellation' (Invoke-Api 'GET' '/api/v1/dashboard/summary' $null $tokenA $null).body.totalExpenses 90

if ($script:analyticsFailure -or $script:liveFailure) { throw 'LIVE_QA_COMPLETE_WITH_KNOWN_RUNNING_BACKEND_FAILURES' }
Write-Output 'LIVE_QA_COMPLETE'
