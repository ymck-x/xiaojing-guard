# 把付费版传到服务器，生成一条一次性下载链接
#
#   .\tools\publish-apk.ps1 -Apk dist\pro-张三.apk -Days 7
#
# 每个 token 目录只放一个买家的包，链接泄露了删掉这个目录即可。
# 服务器上的 /usr/local/bin/xj-cleanup.sh 按 .expires 文件自动清理过期目录。
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [int]$Days = 7,
    [string]$Key = "C:\Users\Administrator\.ssh\speakveZG.pem",
    [string]$SshHost = "ubuntu@182.254.157.23",
    [string]$BaseUrl = "https://dl.speakve.com/xj"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Apk)) { throw "找不到安装包: $Apk" }

$bytes = New-Object byte[] 16
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$token = ($bytes | ForEach-Object { $_.ToString("x2") }) -join ""

$fileName = "xiaojing-guard-pro.apk"
$remoteDir = "/var/www/xiaojing-dl/$token"
$stamp = [int][double]::Parse((Get-Date -UFormat %s))

Write-Output "上传中…"
ssh -i $Key -o BatchMode=yes $SshHost "sudo mkdir -p $remoteDir && sudo chown -R www-data:www-data $remoteDir"
scp -i $Key $Apk "${SshHost}:/tmp/xj-upload.apk"
ssh -i $Key -o BatchMode=yes $SshHost "sudo mv /tmp/xj-upload.apk $remoteDir/$fileName && sudo chown www-data:www-data $remoteDir/$fileName && echo '$stamp + $Days * 86400' | sudo tee $remoteDir/.expires >/dev/null && sudo chown www-data:www-data $remoteDir/.expires && sudo chmod 644 $remoteDir/.expires"

$url = "$BaseUrl/$token/$fileName"
Write-Output ""
Write-Output ("下载链接（$Days 天内有效）:")
Write-Output $url
Write-Output ""
Write-Output "发给买家，让他装上去覆盖免费版即可。"
