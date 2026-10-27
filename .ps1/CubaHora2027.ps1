#Requires -Version 5.1
#Requires -RunAsAdministrator

<#
.SYNOPSIS
    Cuba Hora 2027 - Ajuste profesional de zona horaria de Cuba
.DESCRIPTION
    v1.7.0 - Barra de menu, barra de estado, tema, acceso directo,
    comprobacion post-reinicio, exportar, singleton, preferencias.
.NOTES
    Autor: PC Pro Tech
    Version: 1.7.0
#>

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ============================================================
# CONFIG
# ============================================================

$Script:AppName        = "Cuba Hora 2027"
$Script:AppVersion     = "1.7.0"
$Script:LogPath        = "$env:TEMP\CubaHora2027.log"
$Script:SettingsPath   = "$env:APPDATA\CubaHora2027\settings.json"
$Script:RegistryPath   = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Time Zones\Cuba Standard Time"
$Script:DynamicDSTPath = "$Script:RegistryPath\Dynamic DST"
$Script:MutexName      = "Global\CubaHora2027_Singleton_Mutex"
$Script:Mutex          = $null
$Script:IsExiting      = $false
$Script:CurrentTheme   = "Dark"
$Script:LastApplyOk    = $false
$Script:StatusText     = "Listo"

$Script:TZI_2026 = [byte[]]@(0xf0,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_2027 = [byte[]]@(0x2c,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0xc4,0xff,0xff,0xff,0x00,0x00,0x0b,0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00)
$Script:TZI_Main = $Script:TZI_2027

# ============================================================
# SETTINGS
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
            Write-Log "Preferencias cargadas (tema: $($Script:CurrentTheme))." "INFO"
        }
    } catch { Write-Log "No se pudieron cargar preferencias: $_" "WARN" }
}

function Save-Settings {
    try {
        Get-SettingsDir | Out-Null
        @{
            Theme       = $Script:CurrentTheme
            LastRun     = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
            Version     = $Script:AppVersion
            LastApplyOk = $Script:LastApplyOk
        } | ConvertTo-Json | Set-Content -Path $Script:SettingsPath -Encoding UTF8 -Force
        Write-Log "Preferencias guardadas." "OK"
    } catch { Write-Log "Error guardando preferencias: $_" "WARN" }
}

# ============================================================
# SINGLETON + CLOSE
# ============================================================

function Test-SingleInstance {
    $createdNew = $false
    try {
        $Script:Mutex = New-Object System.Threading.Mutex($true, $Script:MutexName, [ref]$createdNew)
        if (-not $createdNew) {
            [System.Windows.Forms.MessageBox]::Show(
                "Cuba Hora 2027 ya esta en ejecucion.`nRevisa el icono en la bandeja del sistema.",
                "Cuba Hora 2027", "OK", "Information")
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
# LOGGING + STATUS
# ============================================================

function Set-Status {
    param([string]$Text, [string]$Level = "INFO")
    $Script:StatusText = $Text
    if ($Script:lblStatusBar -and -not $Script:lblStatusBar.IsDisposed) {
        $Script:lblStatusBar.Text = "  $Text"
        $Script:lblStatusBar.ForeColor = switch ($Level) {
            "OK"    { [System.Drawing.Color]::FromArgb(0, 200, 120) }
            "WARN"  { [System.Drawing.Color]::FromArgb(255, 180, 40) }
            "ERROR" { [System.Drawing.Color]::FromArgb(255, 80, 80) }
            default { if ($Script:CurrentTheme -eq "Dark") { [System.Drawing.Color]::FromArgb(160,175,200) } else { [System.Drawing.Color]::FromArgb(60,70,90) } }
        }
    }
}

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
    Set-Status $Message $Level
}

# ============================================================
# TIMEZONE CORE
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
    Write-Log "Eliminando clave Dynamic DST..." "INFO"
    try {
        if (Test-Path $Script:DynamicDSTPath) {
            Remove-Item -Path $Script:DynamicDSTPath -Recurse -Force -ErrorAction Stop
            Write-Log "Dynamic DST eliminada." "OK"
        } else { Write-Log "No existia Dynamic DST (OK)." "INFO" }
        return $true
    } catch { Write-Log "Error limpiando Dynamic DST: $_" "ERROR"; return $false }
}

function Set-CubaRegistry {
    Write-Log "Escribiendo registro Cuba 2026-2027..." "INFO"
    try {
        if (-not (Test-Path $Script:RegistryPath)) { New-Item -Path $Script:RegistryPath -Force | Out-Null }
        Set-ItemProperty -Path $Script:RegistryPath -Name "Display" -Value "(UTC-05:00) La Habana" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "Dlt"     -Value "Cuba Daylight Time" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "Std"     -Value "Cuba Standard Time" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "MapID"   -Value "-1,84" -Type String -Force
        Set-ItemProperty -Path $Script:RegistryPath -Name "TZI" -Value $Script:TZI_Main -Type Binary -Force
        Write-Log "TZI principal OK (44 bytes)." "OK"
        if (-not (Test-Path $Script:DynamicDSTPath)) { New-Item -Path $Script:DynamicDSTPath -Force | Out-Null }
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "FirstEntry" -Value 0x7EA -Type DWord -Force
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "LastEntry"  -Value 0x7EB -Type DWord -Force
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "2026" -Value $Script:TZI_2026 -Type Binary -Force
        Write-Log "Dynamic DST 2026 OK." "OK"
        Set-ItemProperty -Path $Script:DynamicDSTPath -Name "2027" -Value $Script:TZI_2027 -Type Binary -Force
        Write-Log "Dynamic DST 2027 OK." "OK"
        return $true
    } catch { Write-Log "Error registro: $_" "ERROR"; return $false }
}

function Set-CubaTimeZone {
    Write-Log "Estableciendo 'Cuba Standard Time'..." "INFO"
    try {
        $result = & tzutil.exe /s "Cuba Standard Time" 2>&1
        if ($LASTEXITCODE -eq 0) { Write-Log "Zona establecida." "OK"; return $true }
        Write-Log "tzutil fallo ($LASTEXITCODE): $result" "ERROR"; return $false
    } catch { Write-Log "Excepcion tzutil: $_" "ERROR"; return $false }
}

function Restart-TimeServices {
    Write-Log "Reiniciando W32Time..." "INFO"
    try {
        $svc = Get-Service -Name w32time -ErrorAction SilentlyContinue
        if ($null -eq $svc) { Write-Log "w32time no encontrado." "WARN"; return $true }
        if ($svc.Status -eq "Running") {
            Stop-Service -Name w32time -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 800
        }
        if ($svc.StartType -ne "Disabled") {
            try { Start-Service -Name w32time -ErrorAction Stop; Write-Log "W32Time reiniciado." "OK" }
            catch { Write-Log "No se pudo iniciar w32time." "WARN" }
        } else { Write-Log "w32time deshabilitado." "WARN" }
        try { $null = & w32tm.exe /resync /force 2>&1; Write-Log "Resync OK." "OK" } catch {}
        return $true
    } catch { Write-Log "Advertencia servicios: $_" "WARN"; return $true }
}

function Apply-FullFix {
    Write-Log "===== APLICACION COMPLETA =====" "INFO"
    $ok = $true
    $ok = (Clear-CubaDynamicDST) -and $ok
    $ok = (Set-CubaRegistry) -and $ok
    $ok = (Set-CubaTimeZone) -and $ok
    $ok = (Restart-TimeServices) -and $ok
    $Script:LastApplyOk = $ok
    if ($ok) {
        Write-Log "===== EXITO =====" "OK"
        Write-Log "Reinicia el equipo para reflejar el cambio." "WARN"
        if ($Script:btnRestart) {
            $Script:btnRestart.Enabled = $true
            $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(220, 80, 40)
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
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("=== VERIFICACION CUBA HORA 2027 ===")
    [void]$sb.AppendLine("Fecha: $(Get-Date -Format 'dddd, dd MMMM yyyy HH:mm:ss')")
    [void]$sb.AppendLine("Tema: $($Script:CurrentTheme) | Version: $($Script:AppVersion)")
    [void]$sb.AppendLine("")
    $info = Get-CurrentTimeZoneInfo
    if ($info) {
        [void]$sb.AppendLine("Zona ID        : $($info.Id)")
        [void]$sb.AppendLine("Nombre         : $($info.DisplayName)")
        [void]$sb.AppendLine("Soporta DST    : $($info.SupportsDST)")
        [void]$sb.AppendLine("DST activo     : $($info.IsDaylightTime)")
        [void]$sb.AppendLine("Offset actual  : $($info.CurrentOffset)")
    }
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("--- Dynamic DST ---")
    if (Test-Path $Script:DynamicDSTPath) {
        $first = (Get-ItemProperty $Script:DynamicDSTPath -Name FirstEntry -EA SilentlyContinue).FirstEntry
        $last  = (Get-ItemProperty $Script:DynamicDSTPath -Name LastEntry  -EA SilentlyContinue).LastEntry
        [void]$sb.AppendLine("FirstEntry : $first | LastEntry : $last")
        $h26 = $null -ne (Get-ItemProperty $Script:DynamicDSTPath -Name "2026" -EA SilentlyContinue)
        $h27 = $null -ne (Get-ItemProperty $Script:DynamicDSTPath -Name "2027" -EA SilentlyContinue)
        [void]$sb.AppendLine("2026 presente: $h26 | 2027 presente: $h27")
        if ($h26) { $b = (Get-ItemProperty $Script:DynamicDSTPath -Name "2026")."2026"; [void]$sb.AppendLine("TZI 2026: $($b.Length) bytes") }
        if ($h27) { $b = (Get-ItemProperty $Script:DynamicDSTPath -Name "2027")."2027"; [void]$sb.AppendLine("TZI 2027: $($b.Length) bytes") }
    } else { [void]$sb.AppendLine("No existe Dynamic DST") }
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("Esperado tras reinicio:")
    [void]$sb.AppendLine('"El horario de verano finaliza el domingo, 7 de noviembre de 2027 a la 1:00."')
    return $sb.ToString()
}

function Test-PostRebootHealth {
    Write-Log "Comprobacion post-reinicio..." "INFO"
    $info = Get-CurrentTimeZoneInfo
    $okReg = (Test-Path $Script:DynamicDSTPath)
    $okTz  = $false
    if ($info) {
        $okTz = ($info.Id -like "*Cuba*" -or $info.DisplayName -like "*Cuba*" -or $info.DisplayName -like "*Habana*")
    }
    $offsetOk = $false
    if ($info -and $info.CurrentOffset.TotalHours -eq -4) { $offsetOk = $true }

    $msg = @"
Comprobacion de salud:

Zona Cuba detectada : $okTz
Registro Dynamic DST: $okReg
Offset -04:00       : $offsetOk
DST activo          : $($info.IsDaylightTime)

"@
    if ($okTz -and $okReg -and $offsetOk) {
        $msg += "Estado: CONFIGURACION CORRECTA"
        Write-Log "Post-reinicio: configuracion correcta." "OK"
        [System.Windows.Forms.MessageBox]::Show($msg, "Comprobacion OK", "OK", "Information")
    } else {
        $msg += "Estado: REVISAR - puede faltar reinicio o reaplicar"
        Write-Log "Post-reinicio: hay puntos a revisar." "WARN"
        [System.Windows.Forms.MessageBox]::Show($msg, "Comprobacion", "OK", "Warning")
    }
    Update-StatusPanel
}

function Export-Report {
    $report = Get-VerificationReport
    $logContent = if ($Script:txtLog) { $Script:txtLog.Text } else { "" }
    $full = "CUBA HORA 2027 - INFORME`nVersion: $($Script:AppVersion)`nGenerado: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`n`n$report`n`n=== LOG ===`n$logContent"
    $sfd = New-Object System.Windows.Forms.SaveFileDialog
    $sfd.Filter = "Texto (*.txt)|*.txt"
    $sfd.FileName = "CubaHora2027_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
    if ($sfd.ShowDialog() -eq "OK") {
        $full | Set-Content -Path $sfd.FileName -Encoding UTF8
        Write-Log "Informe exportado: $($sfd.FileName)" "OK"
        [System.Windows.Forms.MessageBox]::Show("Guardado en:`n$($sfd.FileName)", "Exportar", "OK", "Information")
    }
}

function New-DesktopShortcut {
    try {
        $desktop = [Environment]::GetFolderPath("Desktop")
        $lnkPath = Join-Path $desktop "Cuba Hora 2027.lnk"
        $wsh = New-Object -ComObject WScript.Shell
        $sc = $wsh.CreateShortcut($lnkPath)
        $sc.TargetPath = "powershell.exe"
        $sc.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSCommandPath`""
        $sc.WorkingDirectory = Split-Path $PSCommandPath
        $sc.Description = "Cuba Hora 2027 - Ajuste zona horaria Cuba"
        $sc.WindowStyle = 1
        $sc.Save()
        Write-Log "Acceso directo creado en Escritorio." "OK"
        [System.Windows.Forms.MessageBox]::Show("Acceso directo creado en el Escritorio.", "Acceso directo", "OK", "Information")
    } catch {
        Write-Log "Error creando acceso directo: $_" "ERROR"
        [System.Windows.Forms.MessageBox]::Show("No se pudo crear el acceso directo.`n$_", "Error", "OK", "Error")
    }
}

function Show-About {
    $msg = @"
$Script:AppName
Version: $Script:AppVersion

Ajuste profesional de zona horaria de Cuba
para mantener horario de verano hasta el
7 de noviembre de 2027.

Autor: PC Pro Tech
Preferencias: $Script:SettingsPath
Log: $Script:LogPath
"@
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
    if ($Script:pnlStatusBar) { $Script:pnlStatusBar.BackColor = $c.StatusBg }
    if ($Script:btnTheme) {
        $Script:btnTheme.Text = if ($Script:CurrentTheme -eq "Dark") { "  Tema claro" } else { "  Tema oscuro" }
        $Script:btnTheme.BackColor = $c.BtnSec; $Script:btnTheme.ForeColor = $c.TextMain
    }
    foreach ($b in @($Script:btnVerify,$Script:btnRefresh,$Script:btnOpenDate,$Script:btnCopyLog,$Script:btnExport,$Script:btnHealth)) {
        if ($b) { $b.BackColor = $c.BtnSec; $b.ForeColor = $c.TextMain }
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
    $form.Size = New-Object System.Drawing.Size(800, 720)
    $form.StartPosition = "CenterScreen"
    $form.FormBorderStyle = "FixedSingle"
    $form.MaximizeBox = $false
    $form.BackColor = $c.BgDark
    $form.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $form.ShowInTaskbar = $true

    # ===== MENU BAR =====
    $menuStrip = New-Object System.Windows.Forms.MenuStrip
    $menuStrip.BackColor = $c.BgHeader
    $menuStrip.ForeColor = $c.TextMain
    $menuStrip.Renderer = New-Object System.Windows.Forms.ToolStripProfessionalRenderer

    $mArchivo = New-Object System.Windows.Forms.ToolStripMenuItem("Archivo")
    $miApply  = $mArchivo.DropDownItems.Add("Aplicar correccion")
    $miVerify = $mArchivo.DropDownItems.Add("Verificar")
    $miExport = $mArchivo.DropDownItems.Add("Exportar informe...")
    [void]$mArchivo.DropDownItems.Add("-")
    $Script:menuRestart = $mArchivo.DropDownItems.Add("Reiniciar PC")
    $Script:menuRestart.Enabled = $false
    [void]$mArchivo.DropDownItems.Add("-")
    $miExit = $mArchivo.DropDownItems.Add("Salir")

    $mHerr = New-Object System.Windows.Forms.ToolStripMenuItem("Herramientas")
    $miHealth  = $mHerr.DropDownItems.Add("Comprobacion post-reinicio")
    $miShortcut= $mHerr.DropDownItems.Add("Crear acceso directo en Escritorio")
    $miOpenLog = $mHerr.DropDownItems.Add("Abrir carpeta de log")
    $miOpenSet = $mHerr.DropDownItems.Add("Abrir carpeta de preferencias")
    [void]$mHerr.DropDownItems.Add("-")
    $miTheme = $mHerr.DropDownItems.Add("Cambiar tema")
    $miDate  = $mHerr.DropDownItems.Add("Abrir Fecha y hora")

    $mAyuda = New-Object System.Windows.Forms.ToolStripMenuItem("Ayuda")
    $miAbout = $mAyuda.DropDownItems.Add("Acerca de...")

    [void]$menuStrip.Items.Add($mArchivo)
    [void]$menuStrip.Items.Add($mHerr)
    [void]$menuStrip.Items.Add($mAyuda)
    $form.MainMenuStrip = $menuStrip
    $form.Controls.Add($menuStrip)

    # Header (below menu)
    $Script:pnlHeader = New-Object System.Windows.Forms.Panel
    $Script:pnlHeader.Location = New-Object System.Drawing.Point(0, 28)
    $Script:pnlHeader.Size = New-Object System.Drawing.Size(800, 70)
    $Script:pnlHeader.BackColor = $c.BgHeader
    $form.Controls.Add($Script:pnlHeader)

    $Script:lblTitle = New-Object System.Windows.Forms.Label
    $Script:lblTitle.Text = "CUBA HORA 2027"
    $Script:lblTitle.Font = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
    $Script:lblTitle.ForeColor = $c.AccentCyan
    $Script:lblTitle.Location = New-Object System.Drawing.Point(20, 8)
    $Script:lblTitle.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblTitle)

    $Script:lblSub = New-Object System.Windows.Forms.Label
    $Script:lblSub.Text = "Mantener horario de verano hasta el 7 de noviembre de 2027"
    $Script:lblSub.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $Script:lblSub.ForeColor = $c.TextMuted
    $Script:lblSub.Location = New-Object System.Drawing.Point(22, 40)
    $Script:lblSub.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblSub)

    $Script:lblVer = New-Object System.Windows.Forms.Label
    $Script:lblVer.Text = "v$Script:AppVersion"
    $Script:lblVer.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:lblVer.ForeColor = $c.TextMuted
    $Script:lblVer.Location = New-Object System.Drawing.Point(720, 12)
    $Script:lblVer.AutoSize = $true
    $Script:pnlHeader.Controls.Add($Script:lblVer)

    # Status card
    $Script:pnlStatus = New-Object System.Windows.Forms.Panel
    $Script:pnlStatus.Location = New-Object System.Drawing.Point(16, 108)
    $Script:pnlStatus.Size = New-Object System.Drawing.Size(750, 90)
    $Script:pnlStatus.BackColor = $c.BgCard
    $form.Controls.Add($Script:pnlStatus)

    $Script:lblStatusTitle = New-Object System.Windows.Forms.Label
    $Script:lblStatusTitle.Text = "ESTADO DEL SISTEMA"
    $Script:lblStatusTitle.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblStatusTitle.ForeColor = $c.AccentBlue
    $Script:lblStatusTitle.Location = New-Object System.Drawing.Point(14, 8)
    $Script:lblStatusTitle.AutoSize = $true
    $Script:pnlStatus.Controls.Add($Script:lblStatusTitle)

    $Script:lblZona = New-Object System.Windows.Forms.Label
    $Script:lblZona.Location = New-Object System.Drawing.Point(14, 28)
    $Script:lblZona.Size = New-Object System.Drawing.Size(540, 20)
    $Script:lblZona.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
    $Script:lblZona.ForeColor = $c.TextMain
    $Script:pnlStatus.Controls.Add($Script:lblZona)

    $Script:lblOffset = New-Object System.Windows.Forms.Label
    $Script:lblOffset.Location = New-Object System.Drawing.Point(14, 50)
    $Script:lblOffset.Size = New-Object System.Drawing.Size(540, 16)
    $Script:lblOffset.ForeColor = $c.TextMuted
    $Script:pnlStatus.Controls.Add($Script:lblOffset)

    $Script:lblId = New-Object System.Windows.Forms.Label
    $Script:lblId.Location = New-Object System.Drawing.Point(14, 68)
    $Script:lblId.Size = New-Object System.Drawing.Size(440, 16)
    $Script:lblId.ForeColor = $c.TextMuted
    $Script:lblId.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:pnlStatus.Controls.Add($Script:lblId)

    $Script:lblEstado = New-Object System.Windows.Forms.Label
    $Script:lblEstado.Location = New-Object System.Drawing.Point(560, 35)
    $Script:lblEstado.Size = New-Object System.Drawing.Size(170, 24)
    $Script:lblEstado.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
    $Script:lblEstado.TextAlign = "MiddleRight"
    $Script:pnlStatus.Controls.Add($Script:lblEstado)

    # Buttons
    $btnApply = New-Object System.Windows.Forms.Button
    $btnApply.Text = "  Aplicar correccion"
    $btnApply.Location = New-Object System.Drawing.Point(16, 210)
    $btnApply.Size = New-Object System.Drawing.Size(150, 36)
    $btnApply.BackColor = $c.AccentBlue
    $btnApply.ForeColor = [System.Drawing.Color]::White
    $btnApply.FlatStyle = "Flat"
    $btnApply.FlatAppearance.BorderSize = 0
    $btnApply.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $btnApply.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($btnApply)

    $Script:btnVerify = New-Object System.Windows.Forms.Button
    $Script:btnVerify.Text = "  Verificar"
    $Script:btnVerify.Location = New-Object System.Drawing.Point(176, 210)
    $Script:btnVerify.Size = New-Object System.Drawing.Size(95, 36)
    $Script:btnVerify.BackColor = $c.BtnSec
    $Script:btnVerify.ForeColor = $c.TextMain
    $Script:btnVerify.FlatStyle = "Flat"
    $Script:btnVerify.FlatAppearance.BorderSize = 0
    $Script:btnVerify.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnVerify)

    $Script:btnRefresh = New-Object System.Windows.Forms.Button
    $Script:btnRefresh.Text = "  Actualizar"
    $Script:btnRefresh.Location = New-Object System.Drawing.Point(281, 210)
    $Script:btnRefresh.Size = New-Object System.Drawing.Size(95, 36)
    $Script:btnRefresh.BackColor = $c.BtnSec
    $Script:btnRefresh.ForeColor = $c.TextMain
    $Script:btnRefresh.FlatStyle = "Flat"
    $Script:btnRefresh.FlatAppearance.BorderSize = 0
    $Script:btnRefresh.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnRefresh)

    $Script:btnHealth = New-Object System.Windows.Forms.Button
    $Script:btnHealth.Text = "  Salud"
    $Script:btnHealth.Location = New-Object System.Drawing.Point(386, 210)
    $Script:btnHealth.Size = New-Object System.Drawing.Size(85, 36)
    $Script:btnHealth.BackColor = $c.BtnSec
    $Script:btnHealth.ForeColor = $c.TextMain
    $Script:btnHealth.FlatStyle = "Flat"
    $Script:btnHealth.FlatAppearance.BorderSize = 0
    $Script:btnHealth.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnHealth)

    $Script:btnOpenDate = New-Object System.Windows.Forms.Button
    $Script:btnOpenDate.Text = "  Fecha/hora"
    $Script:btnOpenDate.Location = New-Object System.Drawing.Point(481, 210)
    $Script:btnOpenDate.Size = New-Object System.Drawing.Size(95, 36)
    $Script:btnOpenDate.BackColor = $c.BtnSec
    $Script:btnOpenDate.ForeColor = $c.TextMain
    $Script:btnOpenDate.FlatStyle = "Flat"
    $Script:btnOpenDate.FlatAppearance.BorderSize = 0
    $Script:btnOpenDate.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnOpenDate)

    $Script:btnRestart = New-Object System.Windows.Forms.Button
    $Script:btnRestart.Text = "  Reiniciar PC"
    $Script:btnRestart.Location = New-Object System.Drawing.Point(586, 210)
    $Script:btnRestart.Size = New-Object System.Drawing.Size(100, 36)
    $Script:btnRestart.BackColor = [System.Drawing.Color]::FromArgb(50,50,60)
    $Script:btnRestart.ForeColor = $c.TextMuted
    $Script:btnRestart.FlatStyle = "Flat"
    $Script:btnRestart.FlatAppearance.BorderSize = 0
    $Script:btnRestart.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $Script:btnRestart.Cursor = [System.Windows.Forms.Cursors]::Hand
    $Script:btnRestart.Enabled = $false
    $form.Controls.Add($Script:btnRestart)

    $Script:btnTheme = New-Object System.Windows.Forms.Button
    $Script:btnTheme.Text = if ($Script:CurrentTheme -eq "Dark") { "  Tema claro" } else { "  Tema oscuro" }
    $Script:btnTheme.Location = New-Object System.Drawing.Point(696, 210)
    $Script:btnTheme.Size = New-Object System.Drawing.Size(90, 36)
    $Script:btnTheme.BackColor = $c.BtnSec
    $Script:btnTheme.ForeColor = $c.TextMain
    $Script:btnTheme.FlatStyle = "Flat"
    $Script:btnTheme.FlatAppearance.BorderSize = 0
    $Script:btnTheme.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnTheme)

    # Log header
    $Script:lblLog = New-Object System.Windows.Forms.Label
    $Script:lblLog.Text = "LOG DE OPERACIONES"
    $Script:lblLog.Font = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
    $Script:lblLog.ForeColor = $c.AccentBlue
    $Script:lblLog.Location = New-Object System.Drawing.Point(18, 258)
    $Script:lblLog.AutoSize = $true
    $form.Controls.Add($Script:lblLog)

    $Script:btnCopyLog = New-Object System.Windows.Forms.Button
    $Script:btnCopyLog.Text = "Copiar log"
    $Script:btnCopyLog.Location = New-Object System.Drawing.Point(540, 254)
    $Script:btnCopyLog.Size = New-Object System.Drawing.Size(100, 24)
    $Script:btnCopyLog.BackColor = $c.BtnSec
    $Script:btnCopyLog.ForeColor = $c.TextMain
    $Script:btnCopyLog.FlatStyle = "Flat"
    $Script:btnCopyLog.FlatAppearance.BorderSize = 0
    $Script:btnCopyLog.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnCopyLog.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnCopyLog)

    $Script:btnExport = New-Object System.Windows.Forms.Button
    $Script:btnExport.Text = "Exportar informe"
    $Script:btnExport.Location = New-Object System.Drawing.Point(650, 254)
    $Script:btnExport.Size = New-Object System.Drawing.Size(115, 24)
    $Script:btnExport.BackColor = $c.BtnSec
    $Script:btnExport.ForeColor = $c.TextMain
    $Script:btnExport.FlatStyle = "Flat"
    $Script:btnExport.FlatAppearance.BorderSize = 0
    $Script:btnExport.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:btnExport.Cursor = [System.Windows.Forms.Cursors]::Hand
    $form.Controls.Add($Script:btnExport)

    $Script:txtLog = New-Object System.Windows.Forms.RichTextBox
    $Script:txtLog.Location = New-Object System.Drawing.Point(16, 284)
    $Script:txtLog.Size = New-Object System.Drawing.Size(750, 320)
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
    $Script:lblStatusBar.Text = "  Listo"
    $Script:lblStatusBar.TextAlign = "MiddleLeft"
    $Script:lblStatusBar.Font = New-Object System.Drawing.Font("Segoe UI", 8)
    $Script:pnlStatusBar.Controls.Add($Script:lblStatusBar)

    # ===== EVENTS =====
    $btnApply.Add_Click({
        $btnApply.Enabled = $false; $btnApply.Text = "Aplicando..."
        [System.Windows.Forms.Application]::DoEvents()
        $result = Apply-FullFix
        $btnApply.Enabled = $true; $btnApply.Text = "  Aplicar correccion"
        if ($result) {
            $r = [System.Windows.Forms.MessageBox]::Show(
                "Correccion aplicada.`n`nReinicia el equipo para aplicar el cambio.`n`nDeseas reiniciar ahora?",
                "Exito", "YesNo", "Information")
            if ($r -eq "Yes") {
                Write-Log "Reinicio aceptado." "INFO"
                Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027"
                Close-Application
            }
        } else {
            [System.Windows.Forms.MessageBox]::Show("Hubo errores. Revisa el log.", "Error", "OK", "Error")
        }
    })

    $Script:btnVerify.Add_Click({
        $report = Get-VerificationReport
        Write-Log "Verificacion solicitada." "INFO"
        $Script:txtLog.AppendText("`r`n$report`r`n")
        $Script:txtLog.ScrollToCaret()
        [System.Windows.Forms.MessageBox]::Show($report, "Verificacion", "OK", "Information")
    })
    $Script:btnRefresh.Add_Click({ Update-StatusPanel; Write-Log "Estado actualizado." "INFO" })
    $Script:btnHealth.Add_Click({ Test-PostRebootHealth })
    $Script:btnOpenDate.Add_Click({ Start-Process "timedate.cpl" })
    $Script:btnRestart.Add_Click({
        $r = [System.Windows.Forms.MessageBox]::Show("Reiniciar en 5 segundos?", "Reiniciar", "YesNo", "Warning")
        if ($r -eq "Yes") {
            Write-Log "Reinicio solicitado." "INFO"
            Start-Process "shutdown.exe" -ArgumentList "/r","/t","5","/c","Cuba Hora 2027"
            Close-Application
        }
    })
    $Script:btnTheme.Add_Click({
        $Script:CurrentTheme = if ($Script:CurrentTheme -eq "Dark") { "Light" } else { "Dark" }
        Apply-Theme
        Write-Log "Tema: $($Script:CurrentTheme)" "INFO"
        Save-Settings
    })
    $Script:btnCopyLog.Add_Click({
        try {
            if ($Script:txtLog.Text.Length -gt 0) {
                [System.Windows.Forms.Clipboard]::SetText($Script:txtLog.Text)
                Write-Log "Log copiado." "OK"
            }
        } catch { Write-Log "Error copiar: $_" "ERROR" }
    })
    $Script:btnExport.Add_Click({ Export-Report })

    # Menu events
    $miApply.Add_Click({ $btnApply.PerformClick() })
    $miVerify.Add_Click({ $Script:btnVerify.PerformClick() })
    $miExport.Add_Click({ Export-Report })
    $Script:menuRestart.Add_Click({ $Script:btnRestart.PerformClick() })
    $miExit.Add_Click({ Close-Application })
    $miHealth.Add_Click({ Test-PostRebootHealth })
    $miShortcut.Add_Click({ New-DesktopShortcut })
    $miOpenLog.Add_Click({ Start-Process "explorer.exe" -ArgumentList "/select,`"$Script:LogPath`"" })
    $miOpenSet.Add_Click({ Start-Process "explorer.exe" -ArgumentList "$(Split-Path $Script:SettingsPath)" })
    $miTheme.Add_Click({ $Script:btnTheme.PerformClick() })
    $miDate.Add_Click({ Start-Process "timedate.cpl" })
    $miAbout.Add_Click({ Show-About })

    # Tray
    $notify = New-Object System.Windows.Forms.NotifyIcon
    $notify.Icon = [System.Drawing.SystemIcons]::Application
    $notify.Text = $Script:AppName
    $notify.Visible = $true
    $ctx = New-Object System.Windows.Forms.ContextMenuStrip
    $tShow = $ctx.Items.Add("Mostrar ventana")
    $tApply = $ctx.Items.Add("Aplicar correccion")
    $tTheme = $ctx.Items.Add("Cambiar tema")
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
        $e.Cancel = $true
        $form.Hide()
        $notify.ShowBalloonTip(1200, $Script:AppName, "Minimizado. Salir desde bandeja.", [System.Windows.Forms.ToolTipIcon]::Info)
    })
    $form.Add_Shown({
        Update-StatusPanel
        Write-Log "$Script:AppName v$Script:AppVersion iniciado (tema: $($Script:CurrentTheme))." "INFO"
        Write-Log "Admin: $(Test-IsAdmin)" "INFO"
        Set-Status "Listo" "INFO"
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
    [System.Windows.Forms.MessageBox]::Show("Necesita Administrador. Se elevare.", $Script:AppName, "OK", "Warning")
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "powershell.exe"
    $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    $psi.Verb = "runas"
    try { [System.Diagnostics.Process]::Start($psi) | Out-Null } catch {
        [System.Windows.Forms.MessageBox]::Show("No se pudo elevar.", "Error", "OK", "Error")
    }
    exit
}

if (-not (Test-SingleInstance)) { exit }
Load-Settings
if (-not (Test-Path $Script:LogPath)) { New-Item -Path $Script:LogPath -ItemType File -Force | Out-Null }
Show-MainForm
