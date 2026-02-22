param(
    [string]$Path = "app/src/main/java/com/cinerracam/app",
    [int]$MaxLines = 300
)

if (-not (Test-Path $Path)) {
    Write-Host "Path not found: $Path"
    exit 1
}

$violations = @()

Get-ChildItem -Path $Path -Recurse -File -Filter *.kt | ForEach-Object {
    $lineCount = (Get-Content $_.FullName).Count
    if ($lineCount -gt $MaxLines) {
        $violations += [PSCustomObject]@{
            File = $_.FullName
            Lines = $lineCount
        }
    }
}

if ($violations.Count -eq 0) {
    Write-Host "OK: all Kotlin files in '$Path' are <= $MaxLines lines."
    exit 0
}

Write-Host "Line-length guardrail failed (limit = $MaxLines):"
$violations | Sort-Object -Property Lines -Descending | ForEach-Object {
    Write-Host " - $($_.Lines) : $($_.File)"
}
exit 2
