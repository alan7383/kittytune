param([switch]$SelfTest)
$ErrorActionPreference = 'Stop'
# Load before entering the WinForms message loop; lazy module loading in a Click callback is unsafe.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$runtime = Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$form = New-Object Windows.Forms.Form
$form.Text = 'KittyTune — постоянный интернет-адрес'
$form.Size = New-Object Drawing.Size(570,300)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$intro = New-Object Windows.Forms.Label
$intro.Text = "Войдите в ngrok и скопируйте Your Authtoken.`nТокен сохранится только на этом ПК, зашифрованным для вашего пользователя."
$intro.Location = New-Object Drawing.Point(20,20)
$intro.Size = New-Object Drawing.Size(520,55)
$form.Controls.Add($intro)
$link = New-Object Windows.Forms.LinkLabel
$link.Text = 'Открыть страницу Your Authtoken'
$link.Location = New-Object Drawing.Point(20,85)
$link.Size = New-Object Drawing.Size(480,25)
$link.Add_LinkClicked({ Start-Process 'https://dashboard.ngrok.com/get-started/your-authtoken' })
$form.Controls.Add($link)
$tokenBox = New-Object Windows.Forms.TextBox
$tokenBox.UseSystemPasswordChar = $true
$tokenBox.Location = New-Object Drawing.Point(20,125)
$tokenBox.Size = New-Object Drawing.Size(510,30)
$form.Controls.Add($tokenBox)
$save = New-Object Windows.Forms.Button
$save.Text = 'Сохранить на этом ПК'
$save.Location = New-Object Drawing.Point(290,190)
$save.Size = New-Object Drawing.Size(240,35)
$saveAction = {
    $token = $tokenBox.Text.Trim()
    if ($token -notmatch '^[A-Za-z0-9_\-]{20,200}$') {
        [Windows.Forms.MessageBox]::Show('Вставьте только Authtoken, без команды ngrok config add-authtoken.') | Out-Null
        return
    }
    $path = Join-Path $runtime $(if ($SelfTest) { 'ngrok-token-selftest.dpapi' } else { 'ngrok-token.dpapi' })
    New-Item -ItemType File -Force -Path $path | Out-Null
    $acl = Get-Acl -LiteralPath $path
    $acl.SetAccessRuleProtection($true,$false)
    $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.WindowsIdentity]::GetCurrent().User,'FullControl','Allow'))
    Set-Acl -LiteralPath $path -AclObject $acl
    ConvertTo-SecureString $token -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $path
    $tokenBox.Clear()
    $token = $null
    $form.DialogResult = 'OK'
    $form.Close()
}
$save.Add_Click($saveAction)
$form.Controls.Add($save)
$form.AcceptButton = $save
if ($SelfTest) {
    $tokenBox.Text = 'synthetic-test-token-123456789'
    $form.ShowInTaskbar = $false
    $form.Opacity = 0
    $form.Add_Shown({ $save.PerformClick() })
    $form.ShowDialog() | Out-Null
    $path = Join-Path $runtime 'ngrok-token-selftest.dpapi'
    try {
        $value = [PSCredential]::new('test',(ConvertTo-SecureString (Get-Content -LiteralPath $path -Raw).Trim())).GetNetworkCredential().Password
        if ($value -ne 'synthetic-test-token-123456789') { throw 'Credential roundtrip failed' }
        Write-Output 'Credential save/decrypt test passed; no real token used.'
    } finally { Remove-Item -LiteralPath $path -ErrorAction SilentlyContinue }
} elseif ($form.ShowDialog() -eq 'OK') { Write-Output 'ngrok credential saved locally.' }
$form.Dispose()
