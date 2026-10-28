#Requires -Version 5.1

<#
.SYNOPSIS
    Cuba Hora 2027 - Ajuste y verificacion de la regla horaria de Cuba
.DESCRIPTION
    v1.9.0 - Lee, decodifica y compara los valores TZI reales de Dynamic DST
    para 2026 y 2027. La existencia de claves por si sola no se considera correcta.
.PARAMETER Silent
    Aplica la correccion sin UI y sale.
.PARAMETER VerifyOnly
    Solo verifica y muestra resultado en consola.
.NOTES
    Autor: PC Pro Tech | Version: 1.9.0
#>

param(
    [switch]$Silent,
    [switch]$VerifyOnly
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ============================================================
# CONFIG
# ============================================================

$Script:AppName        = "Cuba Hora 2027"
$Script:AppVersion     = "1.9.0"
$Script:LogPath        = "$env:TEMP\CubaHora2027.log"
$Script:SettingsPath   = "$env:APPDATA\CubaHora2027\settings.json"
$Script:HistoryPath    = "$env:APPDATA\CubaHora2027\history.json"
$Script:BackupDir      = "$env:APPDATA\CubaHora2027\backups"
$Script:RegistrySubKey = "SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time"
$Script:RegistryPath = "HKLM:\$($Script:RegistrySubKey)"
$Script:DynamicDSTSubKey = "$($Script:RegistrySubKey)\Dynamic DST"
$Script:DynamicDSTPath = "$Script:RegistryPath\Dynamic DST"
$Script:MutexName      = "Global\CubaHora2027_Singleton_Mutex"
$Script:Mutex          = $null
$Script:IsExiting      = $false
$Script:CurrentTheme   = "Dark"
$Script:LastApplyOk    = $false
$Script:StatusText     = "Listo"
$Script:Lang           = "ES"   # ES | EN

$Script:TZI_2026 = [byte[]]@(0xf0,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_2027 = [byte[]]@(0x2c,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x0b,0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_Main = $Script:TZI_2027

# ============================================================
# i18n simple
# ============================================================

function T {
    param([string]$Key)
    $map = @{
        "Ready"           = @{ ES="Listo"; EN="Ready" }
        "Apply"           = @{ ES="Aplicar correccion"; EN="Apply fix" }
        "Verify"          = @{ ES="Verificar"; EN="Verify" }
        "Refresh"         = @{ ES="Actualizar"; EN="Refresh" }
        "Health"          = @{ ES="Salud"; EN="Health" }
        "DateTime"        = @{ ES="Fecha/hora"; EN="Date/time" }
        "Restart"         = @{ ES="Reiniciar PC"; EN="Restart PC" }
        "ThemeLight"      = @{ ES="Tema claro"; EN="Light theme" }
        "ThemeDark"       = @{ ES="Tema oscuro"; EN="Dark theme" }
        "CopyLog"         = @{ ES="Copiar log"; EN="Copy log" }
        "Export"          = @{ ES="Exportar informe"; EN="Export report" }
        "StatusTitle"     = @{ ES="ESTADO DEL SISTEMA"; EN="SYSTEM STATUS" }
        "LogTitle"        = @{ ES="LOG DE OPERACIONES"; EN="OPERATION LOG" }
        "ZoneOk"          = @{ ES="Zona + regla en Registro OK"; EN="Zone + Registry rule OK" }
        "ZoneNo"          = @{ ES="Zona NO es Cuba"; EN="Not Cuba zone" }
        "RulesPending"    = @{ ES="Regla DST pendiente"; EN="DST rule pending" }
        "AllPending"      = @{ ES="Zona y regla pendientes"; EN="Zone and rule pending" }
        "SuccessMsg"      = @{ ES="Correccion aplicada.`n`nReinicia el equipo para aplicar el cambio.`n`nDeseas reiniciar ahora?"; EN="Fix applied.`n`nRestart the PC to apply the change.`n`nRestart now?" }
        "SuccessTitle"    = @{ ES="Exito"; EN="Success" }
        "RuleWarn"        = @{ ES="La zona o la regla DST de Cuba requiere atencion."; EN="Cuba timezone or DST rule needs attention." }
    }
    if ($map.ContainsKey($Key) -and $map[$Key].ContainsKey($Script:Lang)) { return $map[$Key][$Script:Lang] }
    return $Key
}

# ============================================================
# SETTINGS + HISTORY
# ============================================================

function Get-SettingsDir {
    $dir = Split-Path $Script:SettingsPath -Parent
    if (-not (Test-Path $dir)) { New-Item -Path $dir -ItemType Directory -Force | Out-Null }
    return $dir
}

function Load-Settings {
    try {
        if (Test-Path $Script:SettingsPath) {
            $obj = (Get-Content $Script:SettingsPath -Raw -Encoding UTF8) | ConvertFrom-Json
            if ($obj.Theme -in @("Dark","Light")) { $Script:CurrentTheme = $obj.Theme }
            if ($null -ne $obj.LastApplyOk) { $Script:LastApplyOk = [bool]$obj.LastApplyOk }
            if ($obj.Lang -in @("ES","EN")) { $Script:Lang = $obj.Lang }
        }
    } catch {}
}

function Save-Settings {
    try {
        Get-SettingsDir | Out-Null
        @{
            Theme       = $Script:CurrentTheme
            LastRun     = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
            Version     = $Script:AppVersion
            LastApplyOk = $Script:LastApplyOk
            Lang        = $Script:Lang
        } | ConvertTo-Json | Set-Content -Path $Script:SettingsPath -Encoding UTF8 -Force
    } catch {}
}

function Add-HistoryEntry {
    param([bool]$Success, [string]$Detail = "")
    try {
        Get-SettingsDir | Out-Null
        $list = @()
        if (Test-Path $Script:HistoryPath) {
            $list = @((Get-Content $Script:HistoryPath -Raw -Encoding UTF8 | ConvertFrom-Json))
        }
        $entry = [PSCustomObject]@{
            Date    = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
            Success = $Success
            Detail  = $Detail
            Version = $Script:AppVersion
        }
        $list = @($entry) + @($list) | Select-Object -First 50
        $list | ConvertTo-Json | Set-Content -Path $Script:HistoryPath -Encoding UTF8 -Force
    } catch {}
}

function Show-History {
    $text = "=== HISTORIAL DE APLICACIONES ===`n`n"
    if (Test-Path $Script:HistoryPath) {
        $list = Get-Content $Script:HistoryPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($list -isnot [array]) { $list = @($list) }
        foreach ($e in $list) {
            $ok = if ($e.Success) { "OK" } else { "FAIL" }
            $text += "[$($e.Date)] $ok  $($e.Detail)`n"
        }
    } else {
        $text += "(sin entradas)`n"
    }
    [System.Windows.Forms.MessageBox]::Show($text, "Historial", "OK", "Information")
}

# ============================================================
# BACKUP REG
# ============================================================

function Backup-CubaRegistry {
    $baseKey = $null
    $existingKey = $null
    $tmp = $null
    try {
        $baseKey = [Microsoft.Win32.RegistryKey]::OpenBaseKey(
            [Microsoft.Win32.RegistryHive]::LocalMachine,
            (Get-CubaRegistryView)
        )
        $existingKey = $baseKey.OpenSubKey($Script:RegistrySubKey, $false)
        if ($null -eq $existingKey) {
            Write-Log "No existe una definicion previa que necesite respaldo." "INFO"
            return $true
        }
        $existingKey.Close()
        $existingKey = $null
        $baseKey.Close()
        $baseKey = $null

        if (-not (Test-Path $Script:BackupDir)) {
            New-Item -Path $Script:BackupDir -ItemType Directory -Force -ErrorAction Stop | Out-Null
        }
        $stamp = Get-Date -Format "yyyyMMdd_HHmmss_fff"
        $unique = [guid]::NewGuid().ToString("N")
        $file = Join-Path $Script:BackupDir ("Cuba_TZ_Backup_{0}_{1}.reg" -f $stamp, $unique)
        $tmp = "${file}.tmp.reg"
        $regArgs = @(
            "export",
            "HKLM\$($Script:RegistrySubKey)",
            $tmp,
            "/y"
        )
        if ([Environment]::Is64BitOperatingSystem) { $regArgs += "/reg:64" }
        & reg.exe @regArgs 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp) -or (Get-Item $tmp).Length -eq 0) {
            throw "reg.exe export no produjo un archivo de respaldo valido."
        }
        $backupContent = [System.IO.File]::ReadAllText($tmp)
        $expectedSection = "[HKEY_LOCAL_MACHINE\$($Script:RegistrySubKey)]"
        if (-not $backupContent.StartsWith("Windows Registry Editor Version 5.00", [System.StringComparison]::Ordinal) -or
            $backupContent -notmatch [regex]::Escape($expectedSection)) {
            throw "El archivo exportado no contiene el encabezado y la clave esperados."
        }
        Move-Item -Path $tmp -Destination $file -ErrorAction Stop
        $tmp = $null
        Write-Log "Backup REG verificado y guardado: $file" "OK"
        return $true
    } catch {
        Write-Log "No se pudo crear un respaldo valido: $_" "ERROR"
        return $false
    } finally {
        if ($existingKey) { $existingKey.Close() }
        if ($baseKey) { $baseKey.Close() }
        if ($tmp -and (Test-Path $tmp)) { Remove-Item -Path $tmp -Force -ErrorAction SilentlyContinue }
    }
}

# ============================================================
# SINGLETON + CLOSE
# ============================================================

function Test-SingleInstance {
    $createdNew = $false
    try {
        $Script:Mutex = New-Object System.Threading.Mutex($true, $Script:MutexName, [ref]$createdNew)
        if (-not $createdNew) {
            if (-not $Silent) {
                [System.Windows.Forms.MessageBox]::Show("Cuba Hora 2027 ya esta en ejecucion.", "Cuba Hora 2027", "OK", "Information")
            }
            return $false
        }
        return $true
    } catch { return $true }
}

function Close-Application {
    if ($Script:IsExiting) { return }
    $Script:IsExiting = $true
    Save-Settings
    try { if ($Script:NotifyIcon) { $Script:NotifyIcon.Visible = $false; $Script:NotifyIcon.Dispose() } } catch {}
    try { if ($Script:MainForm -and -not $Script:MainForm.IsDisposed) { $Script:MainForm.Close(); $Script:MainForm.Dispose() } } catch {}
    try { if ($Script:Mutex) { $Script:Mutex.ReleaseMutex(); $Script:Mutex.Dispose() } } catch {}
    [System.Windows.Forms.Application]::Exit()
    [Environment]::Exit(0)
}

# ============================================================
# LOGGING
# ============================================================

function Set-Status {
    param([string]$Text, [string]$Level = "INFO")
    $Script:StatusText = $Text
    if ($Script:lblStatusBar -and -not $Script:lblStatusBar.IsDisposed) {
        $Script:lblStatusBar.Text = "  $Text"
        $Script:lblStatusBar.ForeColor = switch ($Level) {
            "OK"    { [System.Drawing.Color]::FromArgb(0,200,120) }
            "WARN"  { [System.Drawing.Color]::FromArgb(255,180,40) }
            "ERROR" { [System.Drawing.Color]::FromArgb(255,80,80) }
            default { if ($Script:CurrentTheme -eq "Dark") { [System.Drawing.Color]::FromArgb(160,175,200) } else { [System.Drawing.Color]::FromArgb(60,70,90) } }
        }
    }
}

function Write-Log {
    param([string]$Message, [ValidateSet("INFO","OK","WARN","ERROR")][string]$Level = "INFO")
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [$Level] $Message"
    try { Add-Content -Path $Script:LogPath -Value $line -Encoding UTF8 -EA SilentlyContinue } catch {}
    if ($Script:txtLog -and -not $Script:txtLog.IsDisposed) {
        try {
            $color = if ($Script:CurrentTheme -eq "Dark") {
                switch ($Level) { "OK"{[System.Drawing.Color]::FromArgb(0,200,120)}; "WARN"{[System.Drawing.Color]::FromArgb(255,180,40)}; "ERROR"{[System.Drawing.Color]::FromArgb(255,80,80)}; default{[System.Drawing.Color]::FromArgb(180,200,220)} }
            } else {
                switch ($Level) { "OK"{[System.Drawing.Color]::FromArgb(0,140,70)}; "WARN"{[System.Drawing.Color]::FromArgb(180,120,0)}; "ERROR"{[System.Drawing.Color]::FromArgb(180,30,30)}; default{[System.Drawing.Color]::FromArgb(40,50,70)} }
            }
            $Script:txtLog.SelectionStart = $Script:txtLog.TextLength
            $Script:txtLog.SelectionColor = $color
            $Script:txtLog.AppendText("$line`r`n")
            $Script:txtLog.ScrollToCaret()
        } catch {}
    }
    Set-Status $Message $Level
}

# ============================================================
# TIMEZONE CORE
# ============================================================

function Get-CubaRegistryView {
    if ([Environment]::Is64BitOperatingSystem) {
        return [Microsoft.Win32.RegistryView]::Registry64
    }
    return [Microsoft.Win32.RegistryView]::Default
}

function Test-ByteArrayEqual {
    param([object]$Actual, [byte[]]$Expected)
    if ($null -eq $Actual -or $Actual -isnot [byte[]]) { return $false }
    if ($Actual.Length -ne $Expected.Length) { return $false }
    for ($i = 0; $i -lt $Expected.Length; $i++) {
        if ($Actual[$i] -ne $Expected[$i]) { return $false }
    }
    return $true
}

function Get-TziTransitionDate {
    param(
        [AllowNull()][byte[]]$Tzi,
        [int]$Year,
        [int]$SystemTimeOffset = 12
    )
    if ($null -eq $Tzi -or $Tzi.Length -ne 44) { return $null }
    if ($SystemTimeOffset -lt 12 -or $SystemTimeOffset -gt 28) { return $null }

    $ruleYear = [BitConverter]::ToUInt16($Tzi, $SystemTimeOffset)
    $month = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 2))
    $dayOfWeek = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 4))
    $dayOrWeek = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 6))
    $hour = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 8))
    $minute = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 10))
    $second = [BitConverter]::ToUInt16($Tzi, ($SystemTimeOffset + 12))

    # A zero month means that this transition is deliberately absent.
    if ($month -eq 0) { return $null }
    if ($month -gt 12 -or $hour -gt 23 -or $minute -gt 59 -or $second -gt 59) { return $null }

    if ($ruleYear -gt 0) {
        $targetYear = [int]$ruleYear
        $day = [int]$dayOrWeek
    } else {
        if ($dayOfWeek -gt 6 -or $dayOrWeek -lt 1 -or $dayOrWeek -gt 5) { return $null }
        $targetYear = $Year
        try {
            $firstDay = [datetime]::new($targetYear, [int]$month, 1)
            $firstWeekday = [int]$firstDay.DayOfWeek # Windows: Sunday = 0
            $day = 1 + (($dayOfWeek - $firstWeekday + 7) % 7) + (7 * ($dayOrWeek - 1))
            if ($day -gt [datetime]::DaysInMonth($targetYear, [int]$month)) { $day -= 7 }
        } catch { return $null }
    }

    try {
        return [datetime]::new($targetYear, [int]$month, [int]$day, [int]$hour, [int]$minute, [int]$second)
    } catch { return $null }
}

function Get-CubaRuleAudit {
    $state = [ordered]@{
        IsValid = $false
        Error = $null
        FirstEntry = $null
        LastEntry = $null
        Has2026 = $false
        Has2027 = $false
        BaseLabelsMatch = $false
        BaseTziMatches = $false
        Tzi2026Matches = $false
        Tzi2027Matches = $false
        Bias2026 = $null
        Bias2027 = $null
        Transition2026 = $null
        DaylightTransition2026 = $null
        Transition2027 = $null
        DaylightTransition2027 = $null
    }
    $baseKey = $null
    $tzKey = $null
    $dynamicKey = $null

    try {
        $baseKey = [Microsoft.Win32.RegistryKey]::OpenBaseKey(
            [Microsoft.Win32.RegistryHive]::LocalMachine,
            (Get-CubaRegistryView)
        )
        $tzKey = $baseKey.OpenSubKey($Script:RegistrySubKey, $false)
        if ($null -eq $tzKey) {
            $state.Error = "No existe la clave de zona horaria de Cuba."
            return [pscustomobject]$state
        }

        $dynamicKey = $baseKey.OpenSubKey($Script:DynamicDSTSubKey, $false)
        if ($null -eq $dynamicKey) {
            $state.Error = "No existe la subclave Dynamic DST."
            return [pscustomobject]$state
        }

        $valueNames = @($dynamicKey.GetValueNames())
        $state.Has2026 = $valueNames -contains "2026"
        $state.Has2027 = $valueNames -contains "2027"

        if ($dynamicKey.GetValueNames() -contains "FirstEntry") {
            $state.FirstEntry = $dynamicKey.GetValue("FirstEntry", $null)
        }
        if ($dynamicKey.GetValueNames() -contains "LastEntry") {
            $state.LastEntry = $dynamicKey.GetValue("LastEntry", $null)
        }

        $baseTzi = $tzKey.GetValue("TZI", $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
        $tzi2026 = $dynamicKey.GetValue("2026", $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
        $tzi2027 = $dynamicKey.GetValue("2027", $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)

        $state.BaseTziMatches = (Test-ByteArrayEqual $baseTzi $Script:TZI_Main)
        $state.Tzi2026Matches = (Test-ByteArrayEqual $tzi2026 $Script:TZI_2026)
        $state.Tzi2027Matches = (Test-ByteArrayEqual $tzi2027 $Script:TZI_2027)

        $expectedBaseLabels = @{
            Display = "(UTC-05:00) La Habana"
            Dlt = "Cuba Daylight Time"
            Std = "Cuba Standard Time"
            MapID = "-1,84"
        }
        $state.BaseLabelsMatch = $true
        $baseValueNames = @($tzKey.GetValueNames())
        foreach ($labelName in $expectedBaseLabels.Keys) {
            if ($baseValueNames -notcontains $labelName) {
                $state.BaseLabelsMatch = $false
                continue
            }
            $labelKindOk = $tzKey.GetValueKind($labelName) -eq [Microsoft.Win32.RegistryValueKind]::String
            $labelValue = $tzKey.GetValue($labelName, $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
            if (-not $labelKindOk -or $labelValue -cne $expectedBaseLabels[$labelName]) {
                $state.BaseLabelsMatch = $false
            }
        }

        if ($tzi2026 -is [byte[]] -and $tzi2026.Length -eq 44) {
            $state.Bias2026 = [BitConverter]::ToInt32($tzi2026, 0)
            $state.Transition2026 = Get-TziTransitionDate -Tzi $tzi2026 -Year 2026 -SystemTimeOffset 12
            $state.DaylightTransition2026 = Get-TziTransitionDate -Tzi $tzi2026 -Year 2026 -SystemTimeOffset 28
        }
        if ($tzi2027 -is [byte[]] -and $tzi2027.Length -eq 44) {
            $state.Bias2027 = [BitConverter]::ToInt32($tzi2027, 0)
            $state.Transition2027 = Get-TziTransitionDate -Tzi $tzi2027 -Year 2027 -SystemTimeOffset 12
            $state.DaylightTransition2027 = Get-TziTransitionDate -Tzi $tzi2027 -Year 2027 -SystemTimeOffset 28
        }

        $firstKindOk = $false
        $lastKindOk = $false
        $baseKindOk = $false
        $year2026KindOk = $false
        $year2027KindOk = $false
        if ($dynamicKey.GetValueNames() -contains "FirstEntry") {
            $firstKindOk = $dynamicKey.GetValueKind("FirstEntry") -eq [Microsoft.Win32.RegistryValueKind]::DWord
        }
        if ($dynamicKey.GetValueNames() -contains "LastEntry") {
            $lastKindOk = $dynamicKey.GetValueKind("LastEntry") -eq [Microsoft.Win32.RegistryValueKind]::DWord
        }
        if ($tzKey.GetValueNames() -contains "TZI") {
            $baseKindOk = $tzKey.GetValueKind("TZI") -eq [Microsoft.Win32.RegistryValueKind]::Binary
        }
        if ($state.Has2026) {
            $year2026KindOk = $dynamicKey.GetValueKind("2026") -eq [Microsoft.Win32.RegistryValueKind]::Binary
        }
        if ($state.Has2027) {
            $year2027KindOk = $dynamicKey.GetValueKind("2027") -eq [Microsoft.Win32.RegistryValueKind]::Binary
        }

        $state.IsValid = (
            $firstKindOk -and $lastKindOk -and $baseKindOk -and $year2026KindOk -and $year2027KindOk -and
            $state.BaseLabelsMatch -and
            ([int]$state.FirstEntry -eq 2026) -and ([int]$state.LastEntry -eq 2027) -and
            $state.BaseTziMatches -and $state.Tzi2026Matches -and $state.Tzi2027Matches -and
            ($null -eq $state.Transition2026) -and ($null -eq $state.DaylightTransition2026) -and
            ($null -ne $state.Transition2027) -and ($null -eq $state.DaylightTransition2027) -and
            ($state.Transition2027 -eq [datetime]::new(2027, 11, 7, 1, 0, 0))
        )
        if (-not $state.IsValid) {
            $state.Error = "Los valores base, el rango First/LastEntry o los TZI de 2026-2027 no coinciden exactamente."
        }
    } catch {
        $state.Error = $_.Exception.Message
    } finally {
        if ($dynamicKey) { $dynamicKey.Close() }
        if ($tzKey) { $tzKey.Close() }
        if ($baseKey) { $baseKey.Close() }
    }
    return [pscustomobject]$state
}

function Test-IsAdmin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p = New-Object Security.Principal.WindowsPrincipal($id)
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-CurrentTimeZoneInfo {
    try {
        $tz = [System.TimeZoneInfo]::Local
        $now = Get-Date
        $isDst = $tz.IsDaylightSavingTime($now)
        return [PSCustomObject]@{
            Id = $tz.Id; DisplayName = $tz.DisplayName
            SupportsDST = $tz.SupportsDaylightSavingTime; IsDaylightTime = $isDst
            CurrentOffset = if ($isDst) { $tz.BaseUtcOffset.Add([TimeSpan]::FromHours(1)) } else { $tz.BaseUtcOffset }
        }
    } catch { return $null }
}

function Set-CubaRegistry {
    Write-Log "Escribiendo regla Dynamic DST de Cuba 2026-2027..." "INFO"
    $baseKey = $null
    $tzKey = $null
    $dynamicKey = $null
    try {
        $baseKey = [Microsoft.Win32.RegistryKey]::OpenBaseKey(
            [Microsoft.Win32.RegistryHive]::LocalMachine,
            (Get-CubaRegistryView)
        )
        $tzKey = $baseKey.CreateSubKey($Script:RegistrySubKey)
        if ($null -eq $tzKey) { throw "No se pudo abrir la clave de zona horaria para escritura." }
        $dynamicKey = $baseKey.CreateSubKey($Script:DynamicDSTSubKey)
        if ($null -eq $dynamicKey) { throw "No se pudo abrir la subclave Dynamic DST para escritura." }

        # Overwrite only the values managed by this application; preserve other years and metadata.
        $tzKey.SetValue("Display", "(UTC-05:00) La Habana", [Microsoft.Win32.RegistryValueKind]::String)
        $tzKey.SetValue("Dlt", "Cuba Daylight Time", [Microsoft.Win32.RegistryValueKind]::String)
        $tzKey.SetValue("Std", "Cuba Standard Time", [Microsoft.Win32.RegistryValueKind]::String)
        $tzKey.SetValue("MapID", "-1,84", [Microsoft.Win32.RegistryValueKind]::String)
        $tzKey.SetValue("TZI", [byte[]]$Script:TZI_Main, [Microsoft.Win32.RegistryValueKind]::Binary)
        $dynamicKey.SetValue("FirstEntry", [int]2026, [Microsoft.Win32.RegistryValueKind]::DWord)
        $dynamicKey.SetValue("LastEntry", [int]2027, [Microsoft.Win32.RegistryValueKind]::DWord)
        $dynamicKey.SetValue("2026", [byte[]]$Script:TZI_2026, [Microsoft.Win32.RegistryValueKind]::Binary)
        $dynamicKey.SetValue("2027", [byte[]]$Script:TZI_2027, [Microsoft.Win32.RegistryValueKind]::Binary)
        $dynamicKey.Flush()
        $tzKey.Flush()
    } catch {
        Write-Log "Error al escribir la regla de zona horaria: $_" "ERROR"
        return $false
    } finally {
        if ($dynamicKey) { $dynamicKey.Close() }
        if ($tzKey) { $tzKey.Close() }
        if ($baseKey) { $baseKey.Close() }
    }

    $audit = Get-CubaRuleAudit
    if ($audit.IsValid) {
        Write-Log "Registro releido y validado: TZI 2026/2027 coinciden exactamente." "OK"
        return $true
    }
    Write-Log "La verificacion posterior a la escritura fallo: $($audit.Error)" "ERROR"
    return $false
}

function Set-CubaTimeZone {
    Write-Log "tzutil /s Cuba Standard Time..." "INFO"
    try {
        $r = & tzutil.exe /s "Cuba Standard Time" 2>&1
        if ($LASTEXITCODE -eq 0) {
            [System.TimeZoneInfo]::ClearCachedData()
            Write-Log "Zona establecida; cache de zona horaria renovada." "OK"
            return $true
        }
        Write-Log "tzutil fallo: $r" "ERROR"; return $false
    } catch { Write-Log "Excepcion tzutil: $_" "ERROR"; return $false }
}

function Restart-TimeServices {
    Write-Log "Reiniciando W32Time..." "INFO"
    try {
        $svc = Get-Service w32time -EA SilentlyContinue
        if ($null -eq $svc) { Write-Log "w32time no encontrado." "WARN"; return $true }
        if ($svc.Status -eq "Running") { Stop-Service w32time -Force -EA SilentlyContinue; Start-Sleep -Milliseconds 800 }
        if ($svc.StartType -ne "Disabled") {
            try { Start-Service w32time -EA Stop; Write-Log "W32Time OK." "OK" } catch { Write-Log "No se inicio w32time." "WARN" }
        } else { Write-Log "w32time deshabilitado." "WARN" }
        try { $null = & w32tm.exe /resync /force 2>&1 } catch {}
        return $true
    } catch { return $true }
}

function Apply-FullFix {
    Write-Log "===== APLICACION COMPLETA =====" "INFO"
    if (-not (Backup-CubaRegistry)) {
        Write-Log "Aplicacion cancelada para no modificar el Registro sin un respaldo valido." "ERROR"
        $Script:LastApplyOk = $false
        Add-HistoryEntry -Success $false -Detail "Cancelada: fallo el respaldo previo"
        Save-Settings
        Update-StatusPanel
        return $false
    }
    $ok = Set-CubaRegistry
    if ($ok) { $ok = Set-CubaTimeZone }
    if ($ok) { $ok = Restart-TimeServices }
    $finalAudit = Get-CubaRuleAudit
    if (-not $finalAudit.IsValid) {
        Write-Log "La lectura final de Dynamic DST no coincide: $($finalAudit.Error)" "ERROR"
        $ok = $false
    } else {
        Write-Log "Lectura final de Dynamic DST confirmada tras aplicar." "OK"
    }
    $Script:LastApplyOk = $ok
    Add-HistoryEntry -Success $ok -Detail $(if ($ok) { "Aplicacion completa OK" } else { "Aplicacion con errores" })
    if ($ok) {
        Write-Log "===== EXITO =====" "OK"
        Write-Log "Reinicia el equipo para reflejar el cambio." "WARN"
        if ($Script:btnRestart) {
            $Script:btnRestart.Enabled = $true
            $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(220,80,40)
            $Script:btnRestart.ForeColor = [System.Drawing.Color]::White
        }
        if ($Script:menuRestart) { $Script:menuRestart.Enabled = $true }
    } else {
        Write-Log "===== ERRORES =====" "ERROR"
    }
    Update-StatusPanel
    Save-Settings
    return $ok
}

function Get-VerificationReport {
    param([AllowNull()][object]$Audit)
    if ($null -eq $Audit) { $Audit = Get-CubaRuleAudit }
    $sb = New-Object System.Text.StringBuilder
    $audit = $Audit
    $invariant = [System.Globalization.CultureInfo]::InvariantCulture
    [void]$sb.AppendLine("=== VERIFICACION CUBA HORA ===")
    [void]$sb.AppendLine("Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    [void]$sb.AppendLine("Version: $($Script:AppVersion) | Tema: $($Script:CurrentTheme) | Lang: $($Script:Lang)")
    [void]$sb.AppendLine("")

    $info = Get-CurrentTimeZoneInfo
    if ($info) {
        [void]$sb.AppendLine("Zona activa ID : $($info.Id)")
        [void]$sb.AppendLine("Nombre         : $($info.DisplayName)")
        [void]$sb.AppendLine("Soporta DST    : $($info.SupportsDST)")
        [void]$sb.AppendLine("DST activo ahora: $($info.IsDaylightTime)")
        [void]$sb.AppendLine("Offset ahora   : $($info.CurrentOffset)")
    } else {
        [void]$sb.AppendLine("Zona activa    : no disponible")
    }

    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("--- Regla dinamica leida del Registro ---")
    [void]$sb.AppendLine("Ruta: $Script:DynamicDSTPath")
    [void]$sb.AppendLine("FirstEntry / LastEntry: $($audit.FirstEntry) / $($audit.LastEntry)")
    [void]$sb.AppendLine("Entrada 2026 presente: $($audit.Has2026); TZI binario coincide: $($audit.Tzi2026Matches)")
    [void]$sb.AppendLine("Entrada 2027 presente: $($audit.Has2027); TZI binario coincide: $($audit.Tzi2027Matches)")
    [void]$sb.AppendLine("Etiquetas base coinciden: $($audit.BaseLabelsMatch)")
    [void]$sb.AppendLine("TZI base coincide con la regla 2027: $($audit.BaseTziMatches)")

    if ($null -ne $audit.Bias2026) {
        $offsetSpan2026 = [TimeSpan]::FromMinutes(-[double]$audit.Bias2026)
        $offsetSign2026 = if ($offsetSpan2026.Ticks -lt 0) { "-" } else { "+" }
        $offset2026 = $offsetSign2026 + $offsetSpan2026.Duration().ToString("hh\:mm", $invariant)
        [void]$sb.AppendLine("2026: Bias=$($audit.Bias2026) min; desfase base UTC$offset2026")
    }
    if ($null -ne $audit.Bias2027) {
        $offsetSpan2027 = [TimeSpan]::FromMinutes(-[double]$audit.Bias2027)
        $offsetSign2027 = if ($offsetSpan2027.Ticks -lt 0) { "-" } else { "+" }
        $offset2027 = $offsetSign2027 + $offsetSpan2027.Duration().ToString("hh\:mm", $invariant)
        [void]$sb.AppendLine("2027: Bias=$($audit.Bias2027) min; desfase base UTC$offset2027")
    }

    if ($null -ne $audit.Bias2026) {
        if ($null -ne $audit.Transition2026) {
            $transition2026 = $audit.Transition2026.ToString("yyyy-MM-dd HH:mm:ss", $invariant)
            [void]$sb.AppendLine("2026 StandardDate leido del TZI: $transition2026 (hora local)")
        } else {
            [void]$sb.AppendLine("2026 StandardDate leido del TZI: sin transicion codificada")
        }
        if ($null -ne $audit.DaylightTransition2026) {
            $daylight2026 = $audit.DaylightTransition2026.ToString("yyyy-MM-dd HH:mm:ss", $invariant)
            [void]$sb.AppendLine("2026 DaylightDate leido del TZI: $daylight2026 (hora local)")
        } else {
            [void]$sb.AppendLine("2026 DaylightDate leido del TZI: sin transicion codificada")
        }
    } else {
        [void]$sb.AppendLine("2026 StandardDate / DaylightDate: TZI no disponible o no interpretable")
    }
    if ($null -ne $audit.Bias2027) {
        if ($null -ne $audit.Transition2027) {
            $transition2027 = $audit.Transition2027.ToString("yyyy-MM-dd HH:mm:ss", $invariant)
            [void]$sb.AppendLine("2027 StandardDate leido del TZI: $transition2027 (hora local)")
        } else {
            [void]$sb.AppendLine("2027 StandardDate leido del TZI: ausente o no interpretable")
        }
        if ($null -ne $audit.DaylightTransition2027) {
            $daylight2027 = $audit.DaylightTransition2027.ToString("yyyy-MM-dd HH:mm:ss", $invariant)
            [void]$sb.AppendLine("2027 DaylightDate leido del TZI: $daylight2027 (hora local)")
        } else {
            [void]$sb.AppendLine("2027 DaylightDate leido del TZI: sin transicion codificada")
        }
    } else {
        [void]$sb.AppendLine("2027 StandardDate / DaylightDate: TZI no disponible o no interpretable")
    }

    if ($audit.IsValid) {
        [void]$sb.AppendLine("Resultado: REGLA 2026-2027 VERIFICADA")
    } else {
        [void]$sb.AppendLine("Resultado: CORRECCION PENDIENTE")
        if ($audit.Error) { [void]$sb.AppendLine("Motivo: $($audit.Error)") }
    }
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("La fecha de transicion se deriva de los datos TZI leidos ahora; no es una fecha independiente ni una certificacion oficial.")
    return $sb.ToString()
}

function Test-PostRebootHealth {
    Write-Log "Comprobacion de zona y regla dinamica..." "INFO"
    $info = Get-CurrentTimeZoneInfo
    $audit = Get-CubaRuleAudit
    $okTz = $false
    if ($info) { $okTz = ($info.Id -like "*Cuba*" -or $info.DisplayName -like "*Cuba*" -or $info.DisplayName -like "*Habana*") }

    $msg = "Zona Cuba activa: $okTz`nRegla Dynamic DST en Registro 2026-2027: $($audit.IsValid)`n"
    if ($info) {
        $msg += "Offset actual (informativo): $($info.CurrentOffset)`nDST activo ahora: $($info.IsDaylightTime)`n"
    }
    if ($null -ne $audit.Bias2027) {
        if ($null -ne $audit.Transition2027) {
            $msg += "StandardDate 2027 leido: $($audit.Transition2027.ToString('yyyy-MM-dd HH:mm:ss')) hora local`n"
        } else {
            $msg += "StandardDate 2027: ausente o no interpretable`n"
        }
        if ($null -eq $audit.DaylightTransition2027) {
            $msg += "DaylightDate 2027: sin transicion codificada`n"
        }
    } elseif ($audit.Has2027) {
        $msg += "TZI 2027 presente pero no interpretable (formato distinto de 44 bytes).`n"
    }
    if (-not $audit.IsValid -and $audit.Error) { $msg += "Motivo: $($audit.Error)`n" }
    $msg += "`n"

    if ($okTz -and $audit.IsValid) {
        $msg += "Estado: ZONA ACTIVA Y DATOS REGISTRALES VERIFICADOS"
        Write-Log "Salud: zona activa y valores Dynamic DST verificados en el Registro." "OK"
        [System.Windows.Forms.MessageBox]::Show($msg, "Verificacion registral correcta", "OK", "Information")
    } else {
        $msg += "Estado: CORRECCION PENDIENTE"
        Write-Log "Salud: zona o regla pendiente de corregir." "WARN"
        [System.Windows.Forms.MessageBox]::Show($msg, "Verificacion pendiente", "OK", "Warning")
    }
    Update-StatusPanel
}

function Test-TimeZoneRuleAlert {
    $info = Get-CurrentTimeZoneInfo
    $audit = Get-CubaRuleAudit
    $isCuba = $false
    if ($info) {
        $isCuba = ($info.Id -like "*Cuba*" -or $info.DisplayName -like "*Cuba*" -or $info.DisplayName -like "*Habana*")
    }
    if (-not $isCuba -or -not $audit.IsValid) {
        Write-Log (T "RuleWarn") "WARN"
        if ($Script:NotifyIcon) {
            $Script:NotifyIcon.ShowBalloonTip(4000, $Script:AppName, (T "RuleWarn"), [System.Windows.Forms.ToolTipIcon]::Warning)
        }
    }
}

function Export-Report {
    $report = Get-VerificationReport
    $logContent = if ($Script:txtLog) { $Script:txtLog.Text } else { "" }
    $full = "CUBA HORA 2027 - INFORME`nVersion: $($Script:AppVersion)`n$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n`n$report`n`n=== LOG ===`n$logContent"
    $sfd = New-Object System.Windows.Forms.SaveFileDialog
    $sfd.Filter = "Texto (*.txt)|*.txt"
    $sfd.FileName = "CubaHora2027_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
    if ($sfd.ShowDialog() -eq "OK") {
        $full | Set-Content -Path $sfd.FileName -Encoding UTF8
        Write-Log "Informe: $($sfd.FileName)" "OK"
    }
}

function New-DesktopShortcut {
    try {
        $desktop = [Environment]::GetFolderPath("Desktop")
        $lnk = Join-Path $desktop "Cuba Hora 2027.lnk"
        $wsh = New-Object -ComObject WScript.Shell
        $sc = $wsh.CreateShortcut($lnk)
        $sc.TargetPath = "powershell.exe"
        $sc.Arguments = "-NoProfile -WindowStyle Hidden -File `"$PSCommandPath`""
        $sc.WorkingDirectory = Split-Path $PSCommandPath
        $sc.Description = "Cuba Hora 2027"
        $sc.Save()
        Write-Log "Acceso directo creado." "OK"
        [System.Windows.Forms.MessageBox]::Show("Acceso directo creado en Escritorio.", "OK", "OK", "Information")
    } catch {
        Write-Log "Error acceso directo: $_" "ERROR"
    }
}

function Show-About {
    $msg = "$Script:AppName`nVersion: $($Script:AppVersion)`n`nVerificacion de reglas dinamicas 2026-2027.`n`nPreferencias: $Script:SettingsPath`nLog: $Script:LogPath`nBackups: $Script:BackupDir"
    [System.Windows.Forms.MessageBox]::Show($msg, "Acerca de", "OK", "Information")
}

# ============================================================
# THEME
# ============================================================

function Get-ThemeColors {
    if ($Script:CurrentTheme -eq "Light") {
        return @{
            BgDark=([System.Drawing.Color]::FromArgb(245,247,250)); BgCard=([System.Drawing.Color]::FromArgb(255,255,255))
            BgHeader=([System.Drawing.Color]::FromArgb(0,90,160)); AccentBlue=([System.Drawing.Color]::FromArgb(0,120,215))
            AccentCyan=[System.Drawing.Color]::White; TextMain=([System.Drawing.Color]::FromArgb(30,35,45))
            TextMuted=([System.Drawing.Color]::FromArgb(100,110,130)); LogBg=([System.Drawing.Color]::FromArgb(248,249,252))
            BtnSec=([System.Drawing.Color]::FromArgb(230,235,240)); StatusBg=([System.Drawing.Color]::FromArgb(235,238,242))
        }
    }
    return @{
        BgDark=([System.Drawing.Color]::FromArgb(18,22,32)); BgCard=([System.Drawing.Color]::FromArgb(28,34,48))
        BgHeader=([System.Drawing.Color]::FromArgb(12,16,28)); AccentBlue=([System.Drawing.Color]::FromArgb(0,140,255))
        AccentCyan=([System.Drawing.Color]::FromArgb(0,200,220)); TextMain=([System.Drawing.Color]::FromArgb(230,235,245))
        TextMuted=([System.Drawing.Color]::FromArgb(140,155,180)); LogBg=([System.Drawing.Color]::FromArgb(14,18,28))
        BtnSec=([System.Drawing.Color]::FromArgb(45,55,75)); StatusBg=([System.Drawing.Color]::FromArgb(22,26,36))
    }
}

function Apply-Theme {
    $c = Get-ThemeColors
    if (-not $Script:MainForm -or $Script:MainForm.IsDisposed) { return }
    $Script:MainForm.BackColor = $c.BgDark
    if ($Script:pnlHeader) { $Script:pnlHeader.BackColor = $c.BgHeader }
    if ($Script:lblTitle) { $Script:lblTitle.ForeColor = $c.AccentCyan }
    if ($Script:lblSub) { $Script:lblSub.ForeColor = if ($Script:CurrentTheme -eq "Light") { [System.Drawing.Color]::FromArgb(200,220,240) } else { $c.TextMuted } }
    if ($Script:lblVer) { $Script:lblVer.ForeColor = $c.TextMuted }
    if ($Script:pnlStatus) { $Script:pnlStatus.BackColor = $c.BgCard }
    if ($Script:lblStatusTitle) { $Script:lblStatusTitle.ForeColor = $c.AccentBlue; $Script:lblStatusTitle.Text = (T "StatusTitle") }
    if ($Script:lblZona) { $Script:lblZona.ForeColor = $c.TextMain }
    if ($Script:lblOffset) { $Script:lblOffset.ForeColor = $c.TextMuted }
    if ($Script:lblId) { $Script:lblId.ForeColor = $c.TextMuted }
    if ($Script:lblLog) { $Script:lblLog.ForeColor = $c.AccentBlue; $Script:lblLog.Text = (T "LogTitle") }
    if ($Script:txtLog) { $Script:txtLog.BackColor = $c.LogBg; $Script:txtLog.ForeColor = $c.TextMain }
    if ($Script:pnlStatusBar) { $Script:pnlStatusBar.BackColor = $c.StatusBg }
    if ($Script:btnTheme) {
        $Script:btnTheme.Text = "  $(if ($Script:CurrentTheme -eq 'Dark') { T 'ThemeLight' } else { T 'ThemeDark' })"
        $Script:btnTheme.BackColor = $c.BtnSec; $Script:btnTheme.ForeColor = $c.TextMain
    }
    foreach ($b in @($Script:btnVerify,$Script:btnRefresh,$Script:btnOpenDate,$Script:btnCopyLog,$Script:btnExport,$Script:btnHealth)) {
        if ($b) { $b.BackColor = $c.BtnSec; $b.ForeColor = $c.TextMain }
    }
    Update-StatusPanel
}

function Update-StatusPanel {
    if (-not $Script:lblEstado -or $Script:lblEstado.IsDisposed) { return }
    $info = Get-CurrentTimeZoneInfo
    $audit = Get-CubaRuleAudit
    if ($info) {
        $Script:lblZona.Text = $info.DisplayName
        $Script:lblOffset.Text = "Offset: $($info.CurrentOffset)   |   DST: $($info.IsDaylightTime)"
        $Script:lblId.Text = "ID: $($info.Id)"
        $isCuba = ($info.Id -like "*Cuba*" -or $info.DisplayName -like "*Cuba*" -or $info.DisplayName -like "*Habana*")
        if ($isCuba -and $audit.IsValid) {
            $Script:lblEstado.Text = (T "ZoneOk")
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(0,200,120)
        } elseif ($isCuba) {
            $Script:lblEstado.Text = (T "RulesPending")
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(255,180,40)
        } elseif (-not $audit.IsValid) {
            $Script:lblEstado.Text = (T "AllPending")
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(255,140,40)
        } else {
            $Script:lblEstado.Text = (T "ZoneNo")
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(255,100,80)
        }
    } else {
        $Script:lblZona.Text = "Zona no disponible"
        $Script:lblEstado.Text = (T "AllPending")
        $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(255,140,40)
    }
}

# ============================================================
# UI
# ============================================================

function Show-MainForm {
    $c = Get-ThemeColors
    $form = New-Object System.Windows.Forms.Form
    $form.Text = "$Script:AppName  v$Script:AppVersion"
    $form.Size = New-Object System.Drawing.Size(800, 720)
    $form.StartPosition = "CenterScreen"
    $form.FormBorderStyle = "FixedSingle"
    $form.MaximizeBox = $false
    $form.BackColor = $c.BgDark
    $form.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $form.KeyPreview = $true
    $form.ShowInTaskbar = $true

    # Menu
    $menuStrip = New-Object System.Windows.Forms.MenuStrip
    $mArchivo = New-Object System.Windows.Forms.ToolStripMenuItem("Archivo")
    $miApply = $mArchivo.DropDownItems.Add("Aplicar correccion    Ctrl+A")
    $miVerify = $mArchivo.DropDownItems.Add("Verificar             Ctrl+V")
    $miExport = $mArchivo.DropDownItems.Add("Exportar informe...")
    [void]$mArchivo.DropDownItems.Add("-")
    $Script:menuRestart = $mArchivo.DropDownItems.Add("Reiniciar PC")
    $Script:menuRestart.Enabled = $false
    [void]$mArchivo.DropDownItems.Add("-")
    $miExit = $mArchivo.DropDownItems.Add("Salir                 Alt+F4")

    $mHerr = New-Object System.Windows.Forms.ToolStripMenuItem("Herramientas")
    $miHealth = $mHerr.DropDownItems.Add("Comprobacion post-reinicio")
    $miHist = $mHerr.DropDownItems.Add("Ver historial")
    $miShortcut = $mHerr.DropDownItems.Add("Crear acceso directo")
    $miOpenLog = $mHerr.DropDownItems.Add("Abrir carpeta de log")
    $miOpenSet = $mHerr.DropDownItems.Add("Abrir preferencias")
    $miOpenBak = $mHerr.DropDownItems.Add("Abrir carpeta de backups")
    [void]$mHerr.DropDownItems.Add("-")
    $miTheme = $mHerr.DropDownItems.Add("Cambiar tema          Ctrl+T")
    $miLang = $mHerr.DropDownItems.Add("Idioma ES / EN")
    $miDate = $mHerr.DropDownItems.Add("Abrir Fecha y hora")

    $mAyuda = New-Object System.Windows.Forms.ToolStripMenuItem("Ayuda")
    $miAbout = $mAyuda.DropDownItems.Add("Acerca de...")

    [void]$menuStrip.Items.AddRange(@($mArchivo, $mHerr, $mAyuda))
    $form.MainMenuStrip = $menuStrip
    $form.Controls.Add($menuStrip)

    # Header
    $Script:pnlHeader = New-Object System.Windows.Forms.Panel
    $Script:pnlHeader.Location = New-Object System.Drawing.Point(0, 28)
    $Script:pnlHeader.Size = New-Object System.Drawing.Size(800, 68)
    $Script:pnlHeader.BackColor = $c.BgHeader
    $form.Controls.Add($Script:pnlHeader)

    $Script:lblTitle = New-Object System.Windows.Forms.Label
    $Script:lblTitle.Text = "CUBA HORA 2027"
    $Script:lblTitle.Font = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
    $Script:lblTitle.ForeColor = $c.AccentCyan
    $Script:lblTitle.Location = New-Object System.Drawing.Point(18, 6)
    $Script:lblTitle.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblTitle)

    $Script:lblSub = New-Object System.Windows.Forms.Label
    $Script:lblSub.Text = "Verificacion de reglas dinamicas 2026-2027"
    $Script:lblSub.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $Script:lblSub.ForeColor = $c.TextMuted
    $Script:lblSub.Location = New-Object System.Drawing.Point(20, 38)
    $Script:lblSub.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblSub)

    $Script:lblVer = New-Object System.Windows.Forms.Label
    $Script:lblVer.Text = "v$Script:AppVersion"
    $Script:lblVer.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:lblVer.ForeColor = $c.TextMuted
    $Script:lblVer.Location = New-Object System.Drawing.Point(720, 10)
    $Script:lblVer.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblVer)

    # Status card
    $Script:pnlStatus = New-Object System.Windows.Forms.Panel
    $Script:pnlStatus.Location = New-Object System.Drawing.Point(16, 106)
    $Script:pnlStatus.Size = New-Object System.Drawing.Size(750, 88)
    $Script:pnlStatus.BackColor = $c.BgCard
    $form.Controls.Add($Script:pnlStatus)

    $Script:lblStatusTitle = New-Object System.Windows.Forms.Label
    $Script:lblStatusTitle.Text = (T "StatusTitle")
    $Script:lblStatusTitle.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblStatusTitle.ForeColor = $c.AccentBlue
    $Script:lblStatusTitle.Location = New-Object System.Drawing.Point(14, 6)
    $Script:lblStatusTitle.AutoSize = $true
    $Script:pnlStatus.Controls.Add($Script:lblStatusTitle)

    $Script:lblZona = New-Object System.Windows.Forms.Label
    $Script:lblZona.Location = New-Object System.Drawing.Point(14, 26)
    $Script:lblZona.Size = New-Object System.Drawing.Size(540, 20)
    $Script:lblZona.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
    $Script:lblZona.ForeColor = $c.TextMain
    $Script:pnlStatus.Controls.Add($Script:lblZona)

    $Script:lblOffset = New-Object System.Windows.Forms.Label
    $Script:lblOffset.Location = New-Object System.Drawing.Point(14, 48)
    $Script:lblOffset.Size = New-Object System.Drawing.Size(540, 16)
    $Script:lblOffset.ForeColor = $c.TextMuted
    $Script:pnlStatus.Controls.Add($Script:lblOffset)

    $Script:lblId = New-Object System.Windows.Forms.Label
    $Script:lblId.Location = New-Object System.Drawing.Point(14, 66)
    $Script:lblId.Size = New-Object System.Drawing.Size(440, 16)
    $Script:lblId.ForeColor = $c.TextMuted
    $Script:lblId.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:pnlStatus.Controls.Add($Script:lblId)

    $Script:lblEstado = New-Object System.Windows.Forms.Label
    $Script:lblEstado.Location = New-Object System.Drawing.Point(560, 32)
    $Script:lblEstado.Size = New-Object System.Drawing.Size(170, 24)
    $Script:lblEstado.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
    $Script:lblEstado.TextAlign = "MiddleRight"
    $Script:pnlStatus.Controls.Add($Script:lblEstado)

    # Buttons
    $btnApply = New-Object System.Windows.Forms.Button
    $btnApply.Text = "  $(T 'Apply')"
    $btnApply.Location = New-Object System.Drawing.Point(16, 206)
    $btnApply.Size = New-Object System.Drawing.Size(150, 34)
    $btnApply.BackColor = $c.AccentBlue
    $btnApply.ForeColor = [System.Drawing.Color]::White
    $btnApply.FlatStyle = "Flat"
    $btnApply.FlatAppearance.BorderSize = 0
    $btnApply.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $btnApply.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($btnApply)

    $Script:btnVerify = New-Object System.Windows.Forms.Button
    $Script:btnVerify.Text = "  $(T 'Verify')"
    $Script:btnVerify.Location = New-Object System.Drawing.Point(176, 206)
    $Script:btnVerify.Size = New-Object System.Drawing.Size(95, 34)
    $Script:btnVerify.BackColor = $c.BtnSec
    $Script:btnVerify.ForeColor = $c.TextMain
    $Script:btnVerify.FlatStyle = "Flat"
    $Script:btnVerify.FlatAppearance.BorderSize = 0
    $Script:btnVerify.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnVerify)

    $Script:btnRefresh = New-Object System.Windows.Forms.Button
    $Script:btnRefresh.Text = "  $(T 'Refresh')"
    $Script:btnRefresh.Location = New-Object System.Drawing.Point(281, 206)
    $Script:btnRefresh.Size = New-Object System.Drawing.Size(95, 34)
    $Script:btnRefresh.BackColor = $c.BtnSec
    $Script:btnRefresh.ForeColor = $c.TextMain
    $Script:btnRefresh.FlatStyle = "Flat"
    $Script:btnRefresh.FlatAppearance.BorderSize = 0
    $Script:btnRefresh.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnRefresh)

    $Script:btnHealth = New-Object System.Windows.Forms.Button
    $Script:btnHealth.Text = "  $(T 'Health')"
    $Script:btnHealth.Location = New-Object System.Drawing.Point(386, 206)
    $Script:btnHealth.Size = New-Object System.Drawing.Size(85, 34)
    $Script:btnHealth.BackColor = $c.BtnSec
    $Script:btnHealth.ForeColor = $c.TextMain
    $Script:btnHealth.FlatStyle = "Flat"
    $Script:btnHealth.FlatAppearance.BorderSize = 0
    $Script:btnHealth.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnHealth)

    $Script:btnOpenDate = New-Object System.Windows.Forms.Button
    $Script:btnOpenDate.Text = "  $(T 'DateTime')"
    $Script:btnOpenDate.Location = New-Object System.Drawing.Point(481, 206)
    $Script:btnOpenDate.Size = New-Object System.Drawing.Size(95, 34)
    $Script:btnOpenDate.BackColor = $c.BtnSec
    $Script:btnOpenDate.ForeColor = $c.TextMain
    $Script:btnOpenDate.FlatStyle = "Flat"
    $Script:btnOpenDate.FlatAppearance.BorderSize = 0
    $Script:btnOpenDate.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnOpenDate)

    $Script:btnRestart = New-Object System.Windows.Forms.Button
    $Script:btnRestart.Text = "  $(T 'Restart')"
    $Script:btnRestart.Location = New-Object System.Drawing.Point(586, 206)
    $Script:btnRestart.Size = New-Object System.Drawing.Size(100, 34)
    $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(50,50,60)
    $Script:btnRestart.ForeColor = $c.TextMuted
    $Script:btnRestart.FlatStyle = "Flat"
    $Script:btnRestart.FlatAppearance.BorderSize = 0
    $Script:btnRestart.Enabled = $false
    $Script:btnRestart.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnRestart)

    $Script:btnTheme = New-Object System.Windows.Forms.Button
    $Script:btnTheme.Text = "  $(if ($Script:CurrentTheme -eq 'Dark') { T 'ThemeLight' } else { T 'ThemeDark' })"
    $Script:btnTheme.Location = New-Object System.Drawing.Point(696, 206)
    $Script:btnTheme.Size = New-Object System.Drawing.Size(90, 34)
    $Script:btnTheme.BackColor = $c.BtnSec
    $Script:btnTheme.ForeColor = $c.TextMain
    $Script:btnTheme.FlatStyle = "Flat"
    $Script:btnTheme.FlatAppearance.BorderSize = 0
    $Script:btnTheme.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnTheme)

    # Log
    $Script:lblLog = New-Object System.Windows.Forms.Label
    $Script:lblLog.Text = (T "LogTitle")
    $Script:lblLog.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblLog.ForeColor = $c.AccentBlue
    $Script:lblLog.Location = New-Object System.Drawing.Point(18, 252)
    $Script:lblLog.AutoSize = $true
    $form.Controls.Add($Script:lblLog)

    $Script:btnCopyLog = New-Object System.Windows.Forms.Button
    $Script:btnCopyLog.Text = (T "CopyLog")
    $Script:btnCopyLog.Location = New-Object System.Drawing.Point(540, 248)
    $Script:btnCopyLog.Size = New-Object System.Drawing.Size(100, 24)
    $Script:btnCopyLog.BackColor = $c.BtnSec
    $Script:btnCopyLog.ForeColor = $c.TextMain
    $Script:btnCopyLog.FlatStyle = "Flat"
    $Script:btnCopyLog.FlatAppearance.BorderSize = 0
    $Script:btnCopyLog.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnCopyLog.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnCopyLog)

    $Script:btnExport = New-Object System.Windows.Forms.Button
    $Script:btnExport.Text = (T "Export")
    $Script:btnExport.Location = New-Object System.Drawing.Point(650, 248)
    $Script:btnExport.Size = New-Object System.Drawing.Size(115, 24)
    $Script:btnExport.BackColor = $c.BtnSec
    $Script:btnExport.ForeColor = $c.TextMain
    $Script:btnExport.FlatStyle = "Flat"
    $Script:btnExport.FlatAppearance.BorderSize = 0
    $Script:btnExport.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnExport.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnExport)

    $Script:txtLog = New-Object System.Windows.Forms.RichTextBox
    $Script:txtLog.Location = New-Object System.Drawing.Point(16, 278)
    $Script:txtLog.Size = New-Object System.Drawing.Size(750, 330)
    $Script:txtLog.Font = New-Object System.Drawing.Font("Cascadia Mono", 8.5)
    $Script:txtLog.ReadOnly = $true
    $Script:txtLog.BackColor = $c.LogBg
    $Script:txtLog.ForeColor = $c.TextMain
    $Script:txtLog.BorderStyle = "None"
    $form.Controls.Add($Script:txtLog)

    # Status bar
    $Script:pnlStatusBar = New-Object System.Windows.Forms.Panel
    $Script:pnlStatusBar.Dock = "Bottom"
    $Script:pnlStatusBar.Height = 26
    $Script:pnlStatusBar.BackColor = $c.StatusBg
    $form.Controls.Add($Script:pnlStatusBar)

    $Script:lblStatusBar = New-Object System.Windows.Forms.Label
    $Script:lblStatusBar.Dock = "Fill"
    $Script:lblStatusBar.Text = "  $(T 'Ready')"
    $Script:lblStatusBar.TextAlign = "MiddleLeft"
    $Script:lblStatusBar.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:pnlStatusBar.Controls.Add($Script:lblStatusBar)

    # Events
    $btnApply.Add_Click({
        $btnApply.Enabled = $false; $btnApply.Text = "..."
        [System.Windows.Forms.Application]::DoEvents()
        $result = Apply-FullFix
        $btnApply.Enabled = $true; $btnApply.Text = "  $(T 'Apply')"
        if ($result) {
            $r = [System.Windows.Forms.MessageBox]::Show((T "SuccessMsg"), (T "SuccessTitle"), "YesNo", "Information")
            if ($r -eq "Yes") {
                Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027"
                Close-Application
            }
        } else {
            [System.Windows.Forms.MessageBox]::Show("Errores. Revisa el log.", "Error", "OK", "Error")
        }
    })
    $Script:btnVerify.Add_Click({
        Update-StatusPanel
        $report = Get-VerificationReport
        Write-Log "Verificacion." "INFO"
        $Script:txtLog.AppendText("`r`n$report`r`n")
        [System.Windows.Forms.MessageBox]::Show($report, "Verificacion", "OK", "Information")
    })
    $Script:btnRefresh.Add_Click({ Update-StatusPanel; Test-TimeZoneRuleAlert; Write-Log "Actualizado." "INFO" })
    $Script:btnHealth.Add_Click({ Test-PostRebootHealth })
    $Script:btnOpenDate.Add_Click({ Start-Process "timedate.cpl" })
    $Script:btnRestart.Add_Click({
        $r = [System.Windows.Forms.MessageBox]::Show("Reiniciar en 5 s?", "Reiniciar", "YesNo", "Warning")
        if ($r -eq "Yes") {
            Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027"
            Close-Application
        }
    })
    $Script:btnTheme.Add_Click({
        $Script:CurrentTheme = if ($Script:CurrentTheme -eq "Dark") { "Light" } else { "Dark" }
        Apply-Theme; Save-Settings
        Write-Log "Tema: $($Script:CurrentTheme)" "INFO"
    })
    $Script:btnCopyLog.Add_Click({
        try { if ($Script:txtLog.Text.Length -gt 0) { [System.Windows.Forms.Clipboard]::SetText($Script:txtLog.Text); Write-Log "Log copiado." "OK" } } catch {}
    })
    $Script:btnExport.Add_Click({ Export-Report })

    # Menu
    $miApply.Add_Click({ $btnApply.PerformClick() })
    $miVerify.Add_Click({ $Script:btnVerify.PerformClick() })
    $miExport.Add_Click({ Export-Report })
    $Script:menuRestart.Add_Click({ $Script:btnRestart.PerformClick() })
    $miExit.Add_Click({ Close-Application })
    $miHealth.Add_Click({ Test-PostRebootHealth })
    $miHist.Add_Click({ Show-History })
    $miShortcut.Add_Click({ New-DesktopShortcut })
    $miOpenLog.Add_Click({ Start-Process "explorer.exe" -ArgumentList "/select,`"$Script:LogPath`"" })
    $miOpenSet.Add_Click({ Start-Process "explorer.exe" -ArgumentList "$(Split-Path $Script:SettingsPath)" })
    $miOpenBak.Add_Click({ if (Test-Path $Script:BackupDir) { Start-Process "explorer.exe" $Script:BackupDir } else { Write-Log "Sin backups aun." "INFO" } })
    $miTheme.Add_Click({ $Script:btnTheme.PerformClick() })
    $miLang.Add_Click({
        $Script:Lang = if ($Script:Lang -eq "ES") { "EN" } else { "ES" }
        Save-Settings
        Write-Log "Idioma: $($Script:Lang)" "INFO"
        Apply-Theme
    })
    $miDate.Add_Click({ Start-Process "timedate.cpl" })
    $miAbout.Add_Click({ Show-About })

    # Keyboard shortcuts
    $form.Add_KeyDown({
        param($s, $e)
        if ($e.Control -and $e.KeyCode -eq "A") { $e.SuppressKeyPress = $true; $btnApply.PerformClick() }
        if ($e.Control -and $e.KeyCode -eq "V") { $e.SuppressKeyPress = $true; $Script:btnVerify.PerformClick() }
        if ($e.Control -and $e.KeyCode -eq "T") { $e.SuppressKeyPress = $true; $Script:btnTheme.PerformClick() }
        if ($e.Control -and $e.KeyCode -eq "H") { $e.SuppressKeyPress = $true; Test-PostRebootHealth }
        if ($e.Control -and $e.KeyCode -eq "E") { $e.SuppressKeyPress = $true; Export-Report }
    })

    # Tray
    $notify = New-Object System.Windows.Forms.NotifyIcon
    $notify.Icon = [System.Drawing.SystemIcons]::Application
    $notify.Text = $Script:AppName
    $notify.Visible = $true
    $ctx = New-Object System.Windows.Forms.ContextMenuStrip
    $tShow = $ctx.Items.Add("Mostrar")
    $tApply = $ctx.Items.Add("Aplicar")
    $tTheme = $ctx.Items.Add("Tema")
    [void]$ctx.Items.Add("-")
    $tExit = $ctx.Items.Add("Salir")
    $notify.ContextMenuStrip = $ctx
    $tShow.Add_Click({ $form.Show(); $form.WindowState = "Normal"; $form.Activate() })
    $tApply.Add_Click({ $btnApply.PerformClick() })
    $tTheme.Add_Click({ $Script:btnTheme.PerformClick() })
    $tExit.Add_Click({ Close-Application })
    $notify.Add_DoubleClick({ $form.Show(); $form.WindowState = "Normal"; $form.Activate() })

    $form.Add_Resize({
        if ($form.WindowState -eq "Minimized") {
            $form.Hide()
            $notify.ShowBalloonTip(1500, $Script:AppName, "En bandeja. Clic derecho -> Salir.", [System.Windows.Forms.ToolTipIcon]::Info)
        }
    })
    $form.Add_FormClosing({
        param($s,$e)
        if ($Script:IsExiting) { $e.Cancel = $false; return }
        $e.Cancel = $true; $form.Hide()
    })
    $form.Add_Shown({
        Update-StatusPanel
        Write-Log "$Script:AppName v$Script:AppVersion iniciado." "INFO"
        if ($Script:LastApplyOk) {
            Write-Log "Ultima aplicacion fue exitosa. Ejecutando auto-check..." "INFO"
            Test-TimeZoneRuleAlert
        }
        Set-Status (T "Ready") "INFO"
    })

    $Script:MainForm = $form
    $Script:NotifyIcon = $notify
    [System.Windows.Forms.Application]::Run($form)
    Close-Application
}

# ============================================================
# ENTRY
# ============================================================

if (-not (Test-IsAdmin) -and -not $VerifyOnly) {
    if ($Silent) {
        Write-Error "Ejecuta el modo -Silent desde una consola de PowerShell elevada; no se aplicaron cambios."
        exit 1
    }
    [System.Windows.Forms.MessageBox]::Show("Necesita Administrador.", $Script:AppName, "OK", "Warning")
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "powershell.exe"
    $psi.Arguments = "-NoProfile -File `"$PSCommandPath`""
    $psi.Verb = "runas"
    try {
        [System.Diagnostics.Process]::Start($psi) | Out-Null
    } catch {
        [System.Windows.Forms.MessageBox]::Show("No se pudo elevar la aplicacion; no se aplicaron cambios.", $Script:AppName, "OK", "Error")
        exit 1
    }
    exit 0
}

# Read-only verification runs without elevation, a mutex, or local settings/log writes.
if ($VerifyOnly) {
    $audit = Get-CubaRuleAudit
    $report = Get-VerificationReport -Audit $audit
    Write-Host $report
    if ($audit.IsValid) { exit 0 }
    exit 1
}

if (-not (Test-SingleInstance)) { exit 1 }
Load-Settings
if (-not (Test-Path $Script:LogPath)) { New-Item -Path $Script:LogPath -ItemType File -Force | Out-Null }

# CLI modes
if ($Silent) {
    $ok = Apply-FullFix
    if ($ok) { Write-Host "OK: Correccion aplicada. Reinicia el equipo." } else { Write-Host "ERROR: Fallo la aplicacion." }
    exit $(if ($ok) { 0 } else { 1 })
}

Show-MainForm
