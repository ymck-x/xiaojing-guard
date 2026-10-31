# 生成带买家水印的付费版安装包
#
#   .\tools\make-pro-apk.ps1 -Name "张三"
#
# 输出 dist\pro-<slug>.apk，「关于」页会显示「授权给 张三」。
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$OutDir = "E:\fangchenmi\dist"
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# 文件名用 ASCII slug，中文名放进包内的授权信息里
$slug = ($Name -replace '[^\w\u4e00-\u9fa5]', '_')

& "$root\gradlew.bat" -p $root :app:assembleProRelease "-Plicensee=$Name" --console=plain 2>&1 |
    Select-String -Pattern "^e:|BUILD SUCCESS|BUILD FAIL" | Select-Object -Last 2

$src = Join-Path $root "app\build\outputs\apk\pro\release\app-pro-release.apk"
if (-not (Test-Path $src)) { throw "构建产物不存在: $src" }

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }
$dst = Join-Path $OutDir ("pro-" + $slug + ".apk")
Copy-Item $src $dst -Force

Write-Output ("已生成: " + $dst)
Write-Output ("大小: " + [math]::Round((Get-Item $dst).Length / 1MB, 2) + " MB")
Write-Output ("授权给: " + $Name)
