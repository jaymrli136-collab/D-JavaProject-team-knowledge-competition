$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\.."
Push-Location "$repo\backend"
try {
    mvn -Dmaven.repo.local=D:\JavaProject\.m2 spring-boot:run
} finally {
    Pop-Location
}
