$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\.."
$maven = Join-Path $repo ".tools\apache-maven-3.9.11\bin\mvn.cmd"
if (!(Test-Path $maven)) {
    $maven = "mvn"
}
Push-Location "$repo\backend"
try {
    & $maven "-Dmaven.repo.local=$repo\.m2" spring-boot:run
} finally {
    Pop-Location
}
