; Inno Setup script for Gorilla
; Creates a Windows installer (GorillaSetup.exe) with Start Menu shortcut and uninstaller.
;
; Build steps:
;   1. Install Inno Setup from https://jrsoftware.org/isinfo.php
;   2. Run build.bat first to create dist\Gorilla.exe
;   3. Open this .iss file in Inno Setup Compiler — click "Build" -> "Compile"
;   4. Result: Output\GorillaSetup.exe — distribute this single file

#define MyAppName "Gorilla"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Gorilla"
#define MyAppExeName "Gorilla.exe"

[Setup]
AppId={{A7E8F1A2-3C5D-4F6E-9B8A-1234567890AB}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputBaseFilename=GorillaSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked
Name: "startupicon"; Description: "Run on Windows startup"; GroupDescription: "Additional options:"; Flags: unchecked

[Files]
Source: "dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
