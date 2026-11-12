$ErrorActionPreference = "Stop"
$base = "http://localhost:8088"
$headers = @{ "X-User-Id" = "u_alice" }

function Assert-True($condition, $message) {
    if (-not $condition) { throw $message }
}

$health = Invoke-RestMethod -Uri "$base/api/health/overview" -Headers $headers
Assert-True ($health.activeDocuments -ge 1) "activeDocuments should be positive"

$projects = Invoke-RestMethod -Uri "$base/api/projects" -Headers $headers
Assert-True ($projects.Count -ge 1) "projects should not be empty"

$answerBody = @{
    question = "番茄小说增长实验当前最大的风险是什么？"
    projectIds = @("p_tomato")
    sessionId = "smoke-session"
} | ConvertTo-Json
$answer = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -Headers $headers -ContentType "application/json" -Body $answerBody
Assert-True ($answer.status -eq "ANSWERED") "risk question should be answered"
Assert-True ($answer.citations.Count -ge 1) "answer should contain citations"
Assert-True ($answer.citationCoverage -gt 0) "citation coverage should be positive"

$abstainBody = @{
    question = "番茄小说在南极洲的用户覆盖率是多少？"
    projectIds = @("p_tomato")
    sessionId = "smoke-abstain"
} | ConvertTo-Json
$abstain = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -Headers $headers -ContentType "application/json" -Body $abstainBody
Assert-True ($abstain.status -eq "ABSTAINED") "unknown question should abstain"

$gaps = Invoke-RestMethod -Uri "$base/api/gaps" -Headers $headers
Assert-True ($gaps.Count -ge 1) "abstained question should create a knowledge gap"

$evaluation = Invoke-RestMethod -Uri "$base/api/evaluations/run" -Method Post -Headers $headers -ContentType "application/json" -Body "{}"
Assert-True ($evaluation.permissionViolations -eq 0) "permission violations must be zero"

Write-Host "Smoke test passed."
