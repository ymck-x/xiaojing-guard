# 真机驱动小工具：靠 uiautomator 的节点文本来定位并点击，避免手写坐标。
# 用法（在每个新的 pwsh 进程里先 dot-source 一次）：
#   . E:\fangchenmi\tools\ui.ps1
#   Tap-Text "设置密码"
#   Shot "shot.png"

$script:AdbDir = "E:\fangchenmi\build-artifacts"
New-Item -ItemType Directory -Force -Path $script:AdbDir | Out-Null

function Get-UiXml {
    adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    adb pull /sdcard/ui.xml "$script:AdbDir\ui.xml" 2>$null | Out-Null
    return [xml](Get-Content "$script:AdbDir\ui.xml" -Raw)
}

function Find-Node {
    param([xml]$Xml, [string]$Text, [switch]$Contains)
    $nodes = $Xml.SelectNodes("//node")
    foreach ($n in $nodes) {
        $t = $n.text
        if ($null -eq $t) { continue }
        if ($Contains) {
            if ($t -like "*$Text*") { return $n }
        } elseif ($t -eq $Text) {
            return $n
        }
    }
    return $null
}

function Tap-Text {
    param([string]$Text, [switch]$Contains, [int]$Index = 0)
    $xml = Get-UiXml
    $matches = @()
    foreach ($n in $xml.SelectNodes("//node")) {
        $t = $n.text
        if ($null -eq $t) { continue }
        if (($Contains -and $t -like "*$Text*") -or (-not $Contains -and $t -eq $Text)) { $matches += $n }
    }
    if ($matches.Count -eq 0) { Write-Output "NOT_FOUND: $Text"; return $false }
    $node = $matches[[Math]::Min($Index, $matches.Count - 1)]
    $m = [regex]::Match($node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    adb shell input tap ([int]$x) ([int]$y) | Out-Null
    Write-Output "TAPPED: $Text at $([int]$x),$([int]$y)"
    return $true
}

function Tap-Points {
    param([string[]]$Digits)
    foreach ($d in $Digits) {
        Tap-Text $d | Out-Null
        Start-Sleep -Milliseconds 120
    }
}

function Shot {
    param([string]$Name = "shot.png")
    adb shell screencap -p /sdcard/shot.png | Out-Null
    adb pull /sdcard/shot.png "$script:AdbDir\$Name" 2>$null | Out-Null
    Write-Output "$script:AdbDir\$Name"
}

function Back {
    adb shell input keyevent 4 | Out-Null
}
