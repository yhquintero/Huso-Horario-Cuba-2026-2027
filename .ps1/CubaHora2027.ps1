#Requires -Version 5.1
#Requires -RunAsAdministrator

<#
.SYNOPSIS
    Cuba Hora 2027 - Ajuste profesional de zona horaria de Cuba
.DESCRIPTION
    Herramienta grafica profesional. Tema claro/oscuro, preferencias persistentes,
    exportar informe, singleton, icono en bandeja.
.NOTES
    Autor: PC Pro Tech
    Version: 1.6.0
#>

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ============================================================
# CONFIGURACION
# ============================================================

$Script:AppName        = "Cuba Hora 2027"
$Script:AppVersion     = "1.6.0"
$Script:LogPath        = "$env:TEMP\CubaHora2027.log"
$Script:SettingsPath   = "$env:APPDATA\CubaHora2027\settings.json"
$Script:RegistryPath   = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time"
$Script:DynamicDSTPath = "$Script:RegistryPath\Dynamic DST"
$Script:MutexName      = "Global\CubaHora2027_Singleton_Mutex"
$Script:Mutex          = $null
$Script:IsExiting      = $false
$Script:CurrentTheme   = "Dark"   # Dark | Light

$Script:TZI_2026 = [byte[]]@(0xf0,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_2027 = [byte[]]@(0x2c,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x0b,0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_Main = $Script:TZI_2027

# ============================================================
# SETTINGS (guardar / cargar tema)
# ============================================================

function Get-SettingsDir {
    $dir = Split-Path $Script:SettingsPath -Parent
    if (-not (Test-Path $dir)) {
        New-Item -Path $dir -ItemType Directory -Force | Out-Null
    }
    return $dir
}

function Load-Settings {
    try {
        if (Test-Path $Script:SettingsPath) {
            $json = Get-Content $Script:SettingsPath -Raw -Encoding UTF8
            $obj = $json | ConvertFrom-Json
            if ($obj.Theme -in @("Dark","Light")) {
                $Script:CurrentTheme = $obj.Theme
            }
            Write-Log "Preferencias cargadas (tema: $($Script:CurrentTheme))." "INFO"
        }
    } catch {
        Write-Log "No se pudieron cargar preferencias: $_" "WARN"
    }
}

function Save-Settings {
    try {
        Get-SettingsDir | Out-Null
        $obj = @{
            Theme     = $Script:CurrentTheme
            LastRun   = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
            Version   = $Script:AppVersion
        }
        $obj | ConvertTo-Json | Set-Content -Path $Script:SettingsPath -Encoding UTF8 -Force
        Write-Log "Preferencias guardadas (tema: $($Script:CurrentTheme))." "OK"
    } catch {
        Write-Log "Error guardando preferencias: $_" "WARN"
    }
}

# ============================================================
# SINGLETON + CIERRE
# ============================================================

function Test-SingleInstance {
    $createdNew = $false
    try {
        $Script:Mutex = New-Object System.Threading.Mutex($true, $Script:MutexName, [ref]$createdNew)
        if (-not $createdNew) {
            [System.Windows.Forms.MessageBox]::Show(
                "Cuba Hora 2027 ya esta en ejecucion.`n`nRevisa el icono en la bandeja del sistema.",
                "Cuba Hora 2027", "OK", "Information"
            )
            return $false
        }
        return $true
    } catch { return $true }
}

function Close-Application {
    if ($Script:IsExiting) { return }
    $Script:IsExiting = $true
    Save-Settings
    try { if ($Script:NotifyIcon) { $Script:NotifyIcon.Visible = $false; $Script:NotifyIcon.Dispose(); $Script:NotifyIcon = $null } } catch {}
    try { if ($Script:MainForm -and -not $Script:MainForm.IsDisposed) { $Script:MainForm.Close(); $Script:MainForm.Dispose() } } catch {}
    try { if ($Script:Mutex) { $Script:Mutex.ReleaseMutex(); $Script:Mutex.Dispose(); $Script:Mutex = $null } } catch {}
    [System.Windows.Forms.Application]::Exit()
    [Environment]::Exit(0)
}

# ============================================================
# LOGGING
# ============================================================

function Write-Log {
    param([string]$Message, [ValidateSet("INFO","OK","WARN","ERROR")][string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] [$Level] $Message"
    try { Add-Content -Path $Script:LogPath -Value $line -Encoding UTF8 -ErrorAction SilentlyContinue } catch {}
    if ($Script:txtLog -and -not $Script:txtLog.IsDisposed) {
        try {
            $color = if ($Script:CurrentTheme -eq "Dark") {
                switch ($Level) {
                    "OK"    { [System.Drawing.Color]::FromArgb(0, 200, 120) }
                    "WARN"  { [System.Drawing.Color]::FromArgb(255, 180, 40) }
                    "ERROR" { [System.Drawing.Color]::FromArgb(255, 80, 80) }
                    default { [System.Drawing.Color]::FromArgb(180, 200, 220) }
                }
            } else {
                switch ($Level) {
                    "OK"    { [System.Drawing.Color]::FromArgb(0, 140, 70) }
                    "WARN"  { [System.Drawing.Color]::FromArgb(180, 120, 0) }
                    "ERROR" { [System.Drawing.Color]::FromArgb(180, 30, 30) }
                    default { [System.Drawing.Color]::FromArgb(40, 50, 70) }
                }
            }
            $Script:txtLog.SelectionStart = $Script:txtLog.TextLength
            $Script:txtLog.SelectionColor = $color
            $Script:txtLog.AppendText("$line`r`n")
            $Script:txtLog.ScrollToCaret()
        } catch {}
    }
}

# ============================================================
# ZONA HORARIA
# ============================================================

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
            Id             = $tz.Id
            DisplayName    = $tz.DisplayName
            SupportsDST    = $tz.SupportsDaylightSavingTime
            IsDaylightTime = $isDst
            CurrentOffset  = if ($isDst) { $tz.BaseUtcOffset.Add([TimeSpan]::FromHours(1)) } else { $tz.BaseUtcOffset }
        }
    } catch {
        Write-Log "Error obteniendo info de zona: $_" "ERROR"
        return $null
    }
}

function Clear-CubaDynamicDST {
    Write-Log "Eliminando clave Dynamic DST existente..." "INFO"
    try {
        if (Test-Path $Script:DynamicDSTPath) {
            Remove-Item -Path $Script:DynamicDSTPath -Recurse -Force -ErrorAction Stop
            Write-Log "Clave Dynamic DST eliminada correctamente." "OK"
        } else {
            Write-Log "No existia clave Dynamic DST (OK)." "INFO"
        }
        return $true
    } catch {
        Write-Log "Error al limpiar Dynamic DST: $_" "ERROR"
        return $false
    }
}

function Set-CubaRegistry {
    Write-Log "Aplicando configuracion de registro para Cuba 2026-2027..." "INFO"
    try {
        if (-not (Test-Path $Script:RegistryPath)) { New-Item -Path $Script:RegistryPath -Force | Out-Null }
        Set-ItemProperty -Path $Script:RegistryPath -Name "Display" -Value "(UTC-05:00) La Habana" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "Dlt"     -Value "Cuba Daylight Time" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "Std"     -Value "Cuba Standard Time" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "MapID"   -Value "-1,84" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "TZI" -Value $Script:TZI_Main -Type Binary -Force
        Write-Log "TZI principal escrito (44 bytes)." "OK"
        if (-not (Test-Path $Script:DynamicDSTPath)) { New-Item -Path $Script:DynamicDSTPath -Force | Out-Null }
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "FirstEntry" -Value 0x7EA -Type DWord -Force
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "LastEntry"  -Value 0x7EB -Type DWord -Force
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "2026" -Value $Script:TZI_2026 -Type Binary -Force
        Write-Log "Entrada Dynamic DST 2026 (permanente) aplicada." "OK"
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "2027" -Value $Script:TZI_2027 -Type Binary -Force
        Write-Log "Entrada Dynamic DST 2027 (fin 7/nov) aplicada." "OK"
        return $true
    } catch {
        Write-Log "Error escribiendo registro: $_" "ERROR"
        return $false
    }
}

function Set-CubaTimeZone {
    Write-Log "Estableciendo zona horaria 'Cuba Standard Time'..." "INFO"
    try {
        $result = & tzutil.exe /s "Cuba Standard Time" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Zona horaria establecida correctamente." "OK"
            return $true
        } else {
            Write-Log "tzutil fallo (codigo $LASTEXITCODE): $result" "ERROR"
            return $false
        }
    } catch {
        Write-Log "Excepcion en tzutil: $_" "ERROR"
        return $false
    }
}

function Restart-TimeServices {
    Write-Log "Reiniciando servicio de hora de Windows (W32Time)..." "INFO"
    try {
        $svc = Get-Service -Name w32time -ErrorAction SilentlyContinue
        if ($null -eq $svc) { Write-Log "Servicio w32time no encontrado (se omite)." "WARN"; return $true }
        if ($svc.Status -eq "Running") {
            Stop-Service -Name w32time -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 1000
        }
        if ($svc.StartType -ne "Disabled") {
            try {
                Start-Service -Name w32time -ErrorAction Stop
                Write-Log "Servicio W32Time reiniciado correctamente." "OK"
            } catch {
                Write-Log "No se pudo iniciar w32time. Se continua igual." "WARN"
            }
        } else {
            Write-Log "Servicio w32time esta deshabilitado. Se omite reinicio." "WARN"
        }
        try { $null = & w32tm.exe /resync /force 2>&1; Write-Log "Sincronizacion de hora forzada." "OK" } catch { Write-Log "No se pudo forzar resync (no critico)." "WARN" }
        return $true
    } catch {
        Write-Log "Advertencia al reiniciar servicios (no critico): $_" "WARN"
        return $true
    }
}

function Apply-FullFix {
    Write-Log "========== INICIANDO APLICACION COMPLETA ==========" "INFO"
    $ok = $true
    $ok = (Clear-CubaDynamicDST) -and $ok
    $ok = (Set-CubaRegistry) -and $ok
    $ok = (Set-CubaTimeZone) -and $ok
    $ok = (Restart-TimeServices) -and $ok
    if ($ok) {
        Write-Log "========== APLICACION COMPLETADA CON EXITO ==========" "OK"
        Write-Log "Reinicia el equipo para que el cambio se refleje completamente." "WARN"
        if ($Script:btnRestart) {
            $Script:btnRestart.Enabled = $true
            $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(220, 80, 40)
            $Script:btnRestart.ForeColor = [System.Drawing.Color]::White
        }
    } else {
        Write-Log "========== APLICACION FINALIZADA CON ERRORES ==========" "ERROR"
    }
    Update-StatusPanel
    return $ok
}

function Get-VerificationReport {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("=== VERIFICACION DE ZONA HORARIA CUBA ===")
    [void]$sb.AppendLine("Fecha/Hora del sistema: $(Get-Date -Format 'dddd, dd MMMM yyyy HH:mm:ss')")
    [void]$sb.AppendLine("Tema actual: $($Script:CurrentTheme)")
    [void]$sb.AppendLine("")
    $info = Get-CurrentTimeZoneInfo
    if ($info) {
        [void]$sb.AppendLine("Zona actual ID      : $($info.Id)")
        [void]$sb.AppendLine("Nombre mostrado     : $($info.DisplayName)")
        [void]$sb.AppendLine("Soporta DST         : $($info.SupportsDST)")
        [void]$sb.AppendLine("Esta en horario de verano ahora? : $($info.IsDaylightTime)")
        [void]$sb.AppendLine("Offset actual       : $($info.CurrentOffset)")
    }
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("--- Registro (Dynamic DST) ---")
    if (Test-Path $Script:DynamicDSTPath) {
        $first = (Get-ItemProperty $Script:DynamicDSTPath -Name FirstEntry -ErrorAction SilentlyContinue).FirstEntry
        $last  = (Get-ItemProperty $Script:DynamicDSTPath -Name LastEntry  -ErrorAction SilentlyContinue).LastEntry
        [void]$sb.AppendLine("FirstEntry : $first (2026 = 0x7EA)")
        [void]$sb.AppendLine("LastEntry  : $last  (2027 = 0x7EB)")
        $has2026 = $null -ne (Get-ItemProperty $Script:DynamicDSTPath -Name "2026" -ErrorAction SilentlyContinue)
        $has2027 = $null -ne (Get-ItemProperty $Script:DynamicDSTPath -Name "2027" -ErrorAction SilentlyContinue)
        [void]$sb.AppendLine("Entrada 2026 presente : $has2026")
        [void]$sb.AppendLine("Entrada 2027 presente : $has2027")
        if ($has2026) {
            $b = (Get-ItemProperty $Script:DynamicDSTPath -Name "2026")."2026"
            [void]$sb.AppendLine("Tamano TZI 2026       : $($b.Length) bytes (debe ser 44)")
        }
        if ($has2027) {
            $b = (Get-ItemProperty $Script:DynamicDSTPath -Name "2027")."2027"
            [void]$sb.AppendLine("Tamano TZI 2027       : $($b.Length) bytes (debe ser 44)")
        }
    } else {
        [void]$sb.AppendLine("No existe la clave Dynamic DST!")
    }
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("--- Resultado esperado ---")
    [void]$sb.AppendLine("Despues de reiniciar, la ventana de Fecha y hora debe decir:")
    [void]$sb.AppendLine('"El horario de verano finaliza el domingo, 7 de noviembre de 2027 a la 1:00."')
    return $sb.ToString()
}

function Export-Report {
    $report = Get-VerificationReport
    $logContent = if ($Script:txtLog -and -not $Script:txtLog.IsDisposed) { $Script:txtLog.Text } else { "" }
    $full = @"
========================================
CUBA HORA 2027 - INFORME DE VERIFICACION
Version: $($Script:AppVersion)
Generado: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
========================================

$report

========================================
LOG DE OPERACIONES
========================================
$logContent
"@
    $sfd = New-Object System.Windows.Forms.SaveFileDialog
    $sfd.Filter = "Texto (*.txt)|*.txt|Todos (*.*)|*.*"
    $sfd.FileName = "CubaHora2027_Informe_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
    $sfd.Title = "Exportar informe"
    if ($sfd.ShowDialog() -eq "OK") {
        $full | Set-Content -Path $sfd.FileName -Encoding UTF8
        Write-Log "Informe exportado a: $($sfd.FileName)" "OK"
        [System.Windows.Forms.MessageBox]::Show("Informe guardado en:`n$($sfd.FileName)", "Exportar", "OK", "Information")
    }
}

# ============================================================
# TEMAS
# ============================================================

function Get-ThemeColors {
    if ($Script:CurrentTheme -eq "Light") {
        return @{
            BgDark     = [System.Drawing.Color]::FromArgb(245, 247, 250)
            BgCard     = [System.Drawing.Color]::FromArgb(255, 255, 255)
            BgHeader   = [System.Drawing.Color]::FromArgb(0, 90, 160)
            AccentBlue = [System.Drawing.Color]::FromArgb(0, 120, 215)
            AccentCyan = [System.Drawing.Color]::White
            TextMain   = [System.Drawing.Color]::FromArgb(30, 35, 45)
            TextMuted  = [System.Drawing.Color]::FromArgb(100, 110, 130)
            LogBg      = [System.Drawing.Color]::FromArgb(248, 249, 252)
            BtnSecondary = [System.Drawing.Color]::FromArgb(230, 235, 240)
        }
    } else {
        return @{
            BgDark     = [System.Drawing.Color]::FromArgb(18, 22, 32)
            BgCard     = [System.Drawing.Color]::FromArgb(28, 34, 48)
            BgHeader   = [System.Drawing.Color]::FromArgb(12, 16, 28)
            AccentBlue = [System.Drawing.Color]::FromArgb(0, 140, 255)
            AccentCyan = [System.Drawing.Color]::FromArgb(0, 200, 220)
            TextMain   = [System.Drawing.Color]::FromArgb(230, 235, 245)
            TextMuted  = [System.Drawing.Color]::FromArgb(140, 155, 180)
            LogBg      = [System.Drawing.Color]::FromArgb(14, 18, 28)
            BtnSecondary = [System.Drawing.Color]::FromArgb(45, 55, 75)
        }
    }
}

function Apply-Theme {
    $c = Get-ThemeColors
    if (-not $Script:MainForm -or $Script:MainForm.IsDisposed) { return }

    $Script:MainForm.BackColor = $c.BgDark
    if ($Script:pnlHeader) { $Script:pnlHeader.BackColor = $c.BgHeader }
    if ($Script:lblTitle)  { $Script:lblTitle.ForeColor  = $c.AccentCyan }
    if ($Script:lblSub)    { $Script:lblSub.ForeColor    = if ($Script:CurrentTheme -eq "Light") { [System.Drawing.Color]::FromArgb(200,220,240) } else { $c.TextMuted } }
    if ($Script:lblVer)    { $Script:lblVer.ForeColor    = $c.TextMuted }
    if ($Script:pnlStatus) { $Script:pnlStatus.BackColor = $c.BgCard }
    if ($Script:lblStatusTitle) { $Script:lblStatusTitle.ForeColor = $c.AccentBlue }
    if ($Script:lblZona)   { $Script:lblZona.ForeColor   = $c.TextMain }
    if ($Script:lblOffset) { $Script:lblOffset.ForeColor = $c.TextMuted }
    if ($Script:lblId)     { $Script:lblId.ForeColor     = $c.TextMuted }
    if ($Script:lblLog)    { $Script:lblLog.ForeColor    = $c.AccentBlue }
    if ($Script:txtLog)    { $Script:txtLog.BackColor = $c.LogBg; $Script:txtLog.ForeColor = $c.TextMain }
    if ($Script:lblFooter) { $Script:lblFooter.ForeColor = $c.TextMuted }
    if ($Script:btnTheme)  {
        $Script:btnTheme.Text = if ($Script:CurrentTheme -eq "Dark") { "  Tema claro" } else { "  Tema oscuro" }
        $Script:btnTheme.BackColor = $c.BtnSecondary
        $Script:btnTheme.ForeColor = $c.TextMain
    }
    # Botones secundarios
    foreach ($b in @($Script:btnVerify, $Script:btnRefresh, $Script:btnOpenDate, $Script:btnCopyLog, $Script:btnExport)) {
        if ($b) {
            $b.BackColor = $c.BtnSecondary
            $b.ForeColor = $c.TextMain
        }
    }
    Update-StatusPanel
}

# ============================================================
# UI
# ============================================================

function Update-StatusPanel {
    $info = Get-CurrentTimeZoneInfo
    if ($info) {
        $Script:lblZona.Text   = $info.DisplayName
        $Script:lblOffset.Text = "Offset: $($info.CurrentOffset)   |   DST activo: $($info.IsDaylightTime)"
        $Script:lblId.Text     = "ID: $($info.Id)"
        $isCuba = ($info.Id -like "*Cuba*" -or $info.DisplayName -like "*Cuba*" -or $info.DisplayName -like "*Habana*")
        if ($isCuba) {
            $Script:lblEstado.Text = "Zona Cuba OK"
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(0, 200, 120)
        } else {
            $Script:lblEstado.Text = "Zona NO es Cuba"
            $Script:lblEstado.ForeColor = [System.Drawing.Color]::FromArgb(255, 140, 40)
        }
    }
}

function Show-MainForm {
    $c = Get-ThemeColors

    $form = New-Object System.Windows.Forms.Form
    $form.Text = "$Script:AppName  v$Script:AppVersion"
    $form.Size = New-Object System.Drawing.Size(780, 680)
    $form.StartPosition = "CenterScreen"
    $form.FormBorderStyle = "FixedSingle"
    $form.MaximizeBox = $false
    $form.BackColor = $c.BgDark
    $form.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $form.ShowInTaskbar = $true

    # Header
    $Script:pnlHeader = New-Object System.Windows.Forms.Panel
    $Script:pnlHeader.Dock = "Top"
    $Script:pnlHeader.Height = 78
    $Script:pnlHeader.BackColor = $c.BgHeader
    $form.Controls.Add($Script:pnlHeader)

    $Script:lblTitle = New-Object System.Windows.Forms.Label
    $Script:lblTitle.Text = "CUBA HORA 2027"
    $Script:lblTitle.Font = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    $Script:lblTitle.ForeColor = $c.AccentCyan
    $Script:lblTitle.Location = New-Object System.Drawing.Point(22, 12)
    $Script:lblTitle.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblTitle)

    $Script:lblSub = New-Object System.Windows.Forms.Label
    $Script:lblSub.Text = "Mantener horario de verano hasta el 7 de noviembre de 2027"
    $Script:lblSub.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $Script:lblSub.ForeColor = $c.TextMuted
    $Script:lblSub.Location = New-Object System.Drawing.Point(24, 46)
    $Script:lblSub.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblSub)

    $Script:lblVer = New-Object System.Windows.Forms.Label
    $Script:lblVer.Text = "v$Script:AppVersion"
    $Script:lblVer.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:lblVer.ForeColor = $c.TextMuted
    $Script:lblVer.Location = New-Object System.Drawing.Point(700, 18)
    $Script:lblVer.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblVer)

    # Status card
    $Script:pnlStatus = New-Object System.Windows.Forms.Panel
    $Script:pnlStatus.Location = New-Object System.Drawing.Point(20, 95)
    $Script:pnlStatus.Size = New-Object System.Drawing.Size(720, 95)
    $Script:pnlStatus.BackColor = $c.BgCard
    $form.Controls.Add($Script:pnlStatus)

    $Script:lblStatusTitle = New-Object System.Windows.Forms.Label
    $Script:lblStatusTitle.Text = "ESTADO DEL SISTEMA"
    $Script:lblStatusTitle.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblStatusTitle.ForeColor = $c.AccentBlue
    $Script:lblStatusTitle.Location = New-Object System.Drawing.Point(16, 10)
    $Script:lblStatusTitle.AutoSize = $true
    $Script:pnlStatus.Controls.Add($Script:lblStatusTitle)

    $Script:lblZona = New-Object System.Windows.Forms.Label
    $Script:lblZona.Location = New-Object System.Drawing.Point(16, 32)
    $Script:lblZona.Size = New-Object System.Drawing.Size(520, 20)
    $Script:lblZona.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
    $Script:lblZona.ForeColor = $c.TextMain
    $Script:pnlStatus.Controls.Add($Script:lblZona)

    $Script:lblOffset = New-Object System.Windows.Forms.Label
    $Script:lblOffset.Location = New-Object System.Drawing.Point(16, 55)
    $Script:lblOffset.Size = New-Object System.Drawing.Size(520, 18)
    $Script:lblOffset.ForeColor = $c.TextMuted
    $Script:pnlStatus.Controls.Add($Script:lblOffset)

    $Script:lblId = New-Object System.Windows.Forms.Label
    $Script:lblId.Location = New-Object System.Drawing.Point(16, 72)
    $Script:lblId.Size = New-Object System.Drawing.Size(420, 16)
    $Script:lblId.ForeColor = $c.TextMuted
    $Script:lblId.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:pnlStatus.Controls.Add($Script:lblId)

    $Script:lblEstado = New-Object System.Windows.Forms.Label
    $Script:lblEstado.Location = New-Object System.Drawing.Point(540, 40)
    $Script:lblEstado.Size = New-Object System.Drawing.Size(160, 24)
    $Script:lblEstado.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
    $Script:lblEstado.TextAlign = "MiddleRight"
    $Script:pnlStatus.Controls.Add($Script:lblEstado)

    # Botones fila 1
    $btnApply = New-Object System.Windows.Forms.Button
    $btnApply.Text = "  Aplicar correccion"
    $btnApply.Location = New-Object System.Drawing.Point(20, 205)
    $btnApply.Size = New-Object System.Drawing.Size(160, 38)
    $btnApply.BackColor = $c.AccentBlue
    $btnApply.ForeColor = [System.Drawing.Color]::White
    $btnApply.FlatStyle = "Flat"
    $btnApply.FlatAppearance.BorderSize = 0
    $btnApply.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $btnApply.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($btnApply)

    $Script:btnVerify = New-Object System.Windows.Forms.Button
    $Script:btnVerify.Text = "  Verificar"
    $Script:btnVerify.Location = New-Object System.Drawing.Point(190, 205)
    $Script:btnVerify.Size = New-Object System.Drawing.Size(105, 38)
    $Script:btnVerify.BackColor = $c.BtnSecondary
    $Script:btnVerify.ForeColor = $c.TextMain
    $Script:btnVerify.FlatStyle = "Flat"
    $Script:btnVerify.FlatAppearance.BorderSize = 0
    $Script:btnVerify.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnVerify)

    $Script:btnRefresh = New-Object System.Windows.Forms.Button
    $Script:btnRefresh.Text = "  Actualizar"
    $Script:btnRefresh.Location = New-Object System.Drawing.Point(305, 205)
    $Script:btnRefresh.Size = New-Object System.Drawing.Size(105, 38)
    $Script:btnRefresh.BackColor = $c.BtnSecondary
    $Script:btnRefresh.ForeColor = $c.TextMain
    $Script:btnRefresh.FlatStyle = "Flat"
    $Script:btnRefresh.FlatAppearance.BorderSize = 0
    $Script:btnRefresh.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnRefresh)

    $Script:btnOpenDate = New-Object System.Windows.Forms.Button
    $Script:btnOpenDate.Text = "  Fecha y hora"
    $Script:btnOpenDate.Location = New-Object System.Drawing.Point(420, 205)
    $Script:btnOpenDate.Size = New-Object System.Drawing.Size(110, 38)
    $Script:btnOpenDate.BackColor = $c.BtnSecondary
    $Script:btnOpenDate.ForeColor = $c.TextMain
    $Script:btnOpenDate.FlatStyle = "Flat"
    $Script:btnOpenDate.FlatAppearance.BorderSize = 0
    $Script:btnOpenDate.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnOpenDate)

    $Script:btnRestart = New-Object System.Windows.Forms.Button
    $Script:btnRestart.Text = "  Reiniciar PC"
    $Script:btnRestart.Location = New-Object System.Drawing.Point(540, 205)
    $Script:btnRestart.Size = New-Object System.Drawing.Size(110, 38)
    $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(50, 50, 60)
    $Script:btnRestart.ForeColor = $c.TextMuted
    $Script:btnRestart.FlatStyle = "Flat"
    $Script:btnRestart.FlatAppearance.BorderSize = 0
    $Script:btnRestart.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $Script:btnRestart.Cursor = [System.Windows.Forms.Cursors]::Hand
    $Script:btnRestart.Enabled = $false
    $form.Controls.Add($Script:btnRestart)

    $Script:btnTheme = New-Object System.Windows.Forms.Button
    $Script:btnTheme.Text = if ($Script:CurrentTheme -eq "Dark") { "  Tema claro" } else { "  Tema oscuro" }
    $Script:btnTheme.Location = New-Object System.Drawing.Point(660, 205)
    $Script:btnTheme.Size = New-Object System.Drawing.Size(100, 38)
    $Script:btnTheme.BackColor = $c.BtnSecondary
    $Script:btnTheme.ForeColor = $c.TextMain
    $Script:btnTheme.FlatStyle = "Flat"
    $Script:btnTheme.FlatAppearance.BorderSize = 0
    $Script:btnTheme.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnTheme)

    # Log
    $Script:lblLog = New-Object System.Windows.Forms.Label
    $Script:lblLog.Text = "LOG DE OPERACIONES"
    $Script:lblLog.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblLog.ForeColor = $c.AccentBlue
    $Script:lblLog.Location = New-Object System.Drawing.Point(22, 258)
    $Script:lblLog.AutoSize = $true
    $form.Controls.Add($Script:lblLog)

    $Script:btnCopyLog = New-Object System.Windows.Forms.Button
    $Script:btnCopyLog.Text = "Copiar log"
    $Script:btnCopyLog.Location = New-Object System.Drawing.Point(520, 253)
    $Script:btnCopyLog.Size = New-Object System.Drawing.Size(100, 26)
    $Script:btnCopyLog.BackColor = $c.BtnSecondary
    $Script:btnCopyLog.ForeColor = $c.TextMain
    $Script:btnCopyLog.FlatStyle = "Flat"
    $Script:btnCopyLog.FlatAppearance.BorderSize = 0
    $Script:btnCopyLog.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnCopyLog.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnCopyLog)

    $Script:btnExport = New-Object System.Windows.Forms.Button
    $Script:btnExport.Text = "Exportar informe"
    $Script:btnExport.Location = New-Object System.Drawing.Point(630, 253)
    $Script:btnExport.Size = New-Object System.Drawing.Size(110, 26)
    $Script:btnExport.BackColor = $c.BtnSecondary
    $Script:btnExport.ForeColor = $c.TextMain
    $Script:btnExport.FlatStyle = "Flat"
    $Script:btnExport.FlatAppearance.BorderSize = 0
    $Script:btnExport.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnExport.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnExport)

    $Script:txtLog = New-Object System.Windows.Forms.RichTextBox
    $Script:txtLog.Location = New-Object System.Drawing.Point(20, 285)
    $Script:txtLog.Size = New-Object System.Drawing.Size(720, 280)
    $Script:txtLog.Font = New-Object System.Drawing.Font("Cascadia Mono", 8.5)
    $Script:txtLog.ReadOnly = $true
    $Script:txtLog.BackColor = $c.LogBg
    $Script:txtLog.ForeColor = $c.TextMain
    $Script:txtLog.BorderStyle = "None"
    $form.Controls.Add($Script:txtLog)

    $Script:lblFooter = New-Object System.Windows.Forms.Label
    $Script:lblFooter.Text = "Preferencias: $Script:SettingsPath  |  Log: $Script:LogPath"
    $Script:lblFooter.Location = New-Object System.Drawing.Point(22, 575)
    $Script:lblFooter.Size = New-Object System.Drawing.Size(720, 18)
    $Script:lblFooter.ForeColor = $c.TextMuted
    $Script:lblFooter.Font = New-Object System.Drawing.Font("Segoe UI", 7.5)
    $form.Controls.Add($Script:lblFooter)

    # Eventos
    $btnApply.Add_Click({
        $btnApply.Enabled = $false
        $btnApply.Text = "Aplicando..."
        [System.Windows.Forms.Application]::DoEvents()
        $result = Apply-FullFix
        $btnApply.Enabled = $true
        $btnApply.Text = "  Aplicar correccion"
        if ($result) {
            $r = [System.Windows.Forms.MessageBox]::Show(
                "Correccion aplicada correctamente.`n`nPara que Windows muestre el cambio es necesario reiniciar.`n`nDeseas reiniciar ahora?",
                "Exito - Cuba Hora 2027", "YesNo", "Information"
            )
            if ($r -eq "Yes") {
                Write-Log "Usuario acepto reiniciar el equipo." "INFO"
                Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027: reiniciando..."
                Close-Application
            }
        } else {
            [System.Windows.Forms.MessageBox]::Show("Hubo errores. Revisa el log.", "Error", "OK", "Error")
        }
    })

    $Script:btnVerify.Add_Click({
        $report = Get-VerificationReport
        Write-Log "--- Verificacion solicitada ---" "INFO"
        $Script:txtLog.AppendText("`r`n$report`r`n")
        $Script:txtLog.ScrollToCaret()
        [System.Windows.Forms.MessageBox]::Show($report, "Informe de verificacion", "OK", "Information")
    })

    $Script:btnRefresh.Add_Click({ Update-StatusPanel; Write-Log "Estado actualizado." "INFO" })
    $Script:btnOpenDate.Add_Click({ Start-Process "timedate.cpl" })

    $Script:btnRestart.Add_Click({
        $r = [System.Windows.Forms.MessageBox]::Show("Se reiniciara el equipo en 5 segundos.`n`nContinuar?", "Reiniciar PC", "YesNo", "Warning")
        if ($r -eq "Yes") {
            Write-Log "Reinicio solicitado por el usuario." "INFO"
            Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027: reiniciando..."
            Close-Application
        }
    })

    $Script:btnTheme.Add_Click({
        $Script:CurrentTheme = if ($Script:CurrentTheme -eq "Dark") { "Light" } else { "Dark" }
        Apply-Theme
        Write-Log "Tema cambiado a: $($Script:CurrentTheme)" "INFO"
        Save-Settings
    })

    $Script:btnCopyLog.Add_Click({
        try {
            if ($Script:txtLog.Text.Length -gt 0) {
                [System.Windows.Forms.Clipboard]::SetText($Script:txtLog.Text)
                Write-Log "Log copiado al portapapeles." "OK"
                if ($Script:NotifyIcon) {
                    $Script:NotifyIcon.ShowBalloonTip(1200, $Script:AppName, "Log copiado.", [System.Windows.Forms.ToolTipIcon]::Info)
                }
            }
        } catch { Write-Log "Error al copiar log: $_" "ERROR" }
    })

    $Script:btnExport.Add_Click({ Export-Report })

    # Tray
    $notify = New-Object System.Windows.Forms.NotifyIcon
    $notify.Icon = [System.Drawing.SystemIcons]::Application
    $notify.Text = $Script:AppName
    $notify.Visible = $true
    $menu = New-Object System.Windows.Forms.ContextMenuStrip
    $itemShow   = $menu.Items.Add("Mostrar ventana")
    $itemApply  = $menu.Items.Add("Aplicar correccion")
    $itemTheme  = $menu.Items.Add("Cambiar tema")
    [void]$menu.Items.Add("-")
    $itemExit   = $menu.Items.Add("Salir")
    $notify.ContextMenuStrip = $menu
    $itemShow.Add_Click({ $form.Show(); $form.WindowState = "Normal"; $form.Activate(); $form.BringToFront() })
    $itemApply.Add_Click({ $btnApply.PerformClick() })
    $itemTheme.Add_Click({ $Script:btnTheme.PerformClick() })
    $itemExit.Add_Click({ Close-Application })
    $notify.Add_DoubleClick({ $form.Show(); $form.WindowState = "Normal"; $form.Activate(); $form.BringToFront() })

    $form.Add_Resize({
        if ($form.WindowState -eq "Minimized") {
            $form.Hide()
            $notify.ShowBalloonTip(1600, $Script:AppName, "En bandeja. Clic derecho -> Salir para cerrar.", [System.Windows.Forms.ToolTipIcon]::Info)
        }
    })
    $form.Add_FormClosing({
        param($s, $e)
        if ($Script:IsExiting) { $e.Cancel = $false; return }
        $e.Cancel = $true
        $form.Hide()
        $notify.ShowBalloonTip(1200, $Script:AppName, "Minimizado. Clic derecho -> Salir para cerrar.", [System.Windows.Forms.ToolTipIcon]::Info)
    })
    $form.Add_Shown({
        Update-StatusPanel
        Write-Log "$Script:AppName v$Script:AppVersion iniciado (tema: $($Script:CurrentTheme))." "INFO"
        Write-Log "Ejecutando como Administrador: $(Test-IsAdmin)" "INFO"
    })

    $Script:MainForm = $form
    $Script:NotifyIcon = $notify
    [System.Windows.Forms.Application]::Run($form)
    Close-Application
}

# ============================================================
# ENTRY
# ============================================================

if (-not (Test-IsAdmin)) {
    [System.Windows.Forms.MessageBox]::Show("Necesita privilegios de Administrador.`nSe reiniciara solicitando elevacion.", $Script:AppName, "OK", "Warning")
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "powershell.exe"
    $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    $psi.Verb = "runas"
    try { [System.Diagnostics.Process]::Start($psi) | Out-Null } catch {
        [System.Windows.Forms.MessageBox]::Show("No se pudo elevar privilegios.", "Error", "OK", "Error")
    }
    exit
}

if (-not (Test-SingleInstance)) { exit }

Load-Settings

if (-not (Test-Path $Script:LogPath)) {
    New-Item -Path $Script:LogPath -ItemType File -Force | Out-Null
}

Show-MainForm
