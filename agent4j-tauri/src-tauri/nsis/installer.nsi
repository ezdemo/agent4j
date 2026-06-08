; ============================================
; Agent4j Desktop — NSIS Installer
; ============================================
;  Overview:
;    Per-user install (no admin required), MUI2-based,
;    with optional desktop/start-menu shortcuts and auto-start.
; ============================================

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"

; ── Tauri-injected defines ──
!define APP_NAME      "${PRODUCT_NAME}"
!define APP_VERSION   "${VERSION}"
!define APP_PUBLISHER "Agent4j"
!define APP_WEB_SITE  "https://gitee.com/ezdemo/agent4j"
!define APP_DIR_REGKEY  "Software\Microsoft\Windows\CurrentVersion\App Paths\${APP_NAME}.exe"
!define APP_UNINST_KEY  "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"

; ── Install directory ──
!define INSTALL_DIR "$LOCALAPPDATA\${APP_NAME}"

; ── Icons ──
!define MUI_ICON    "icons\icon.ico"
!define MUI_UNICON  "icons\icon.ico"

; ── Welcome / finish bitmaps ──
!define MUI_WELCOMEFINISHPAGE_BITMAP    "icons\128x128.png"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP  "icons\128x128.png"

; ── Abort warning ──
!define MUI_ABORTWARNING
!define MUI_UNABORTWARNING

; ── Per-user install (no admin rights needed) ──
RequestExecutionLevel user

; ============================================
; UI Strings
; ============================================

; Welcome page
!define MUI_WELCOMEPAGE_TITLE "Welcome to Agent4j Setup"
!define MUI_WELCOMEPAGE_TEXT  "Agent4j is a Java-based AI coding agent desktop client.$\r$\n$\r$\nThis setup will install Agent4j for the current user (no administrator privileges required).$\r$\n$\r$\nClick Next to continue."

; Finish page
!define MUI_FINISHPAGE_TITLE      "Installation Complete"
!define MUI_FINISHPAGE_TEXT       "Agent4j has been installed successfully.$\r$\n$\r$\nClick Finish to launch the application."
!define MUI_FINISHPAGE_RUN        "$INSTDIR\${APP_NAME}.exe"
!define MUI_FINISHPAGE_RUN_TEXT   "Launch Agent4j"
!define MUI_FINISHPAGE_LINK       "Visit Project Homepage"
!define MUI_FINISHPAGE_LINK_LOCATION "${APP_WEB_SITE}"

; Uninstall finish page
!define MUI_UNFINISHPAGE_TITLE "Uninstall Complete"
!define MUI_UNFINISHPAGE_TEXT  "Agent4j has been removed from your computer."

; ============================================
; Pages
; ============================================
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE    "license.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; ============================================
; Languages
; ============================================
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"

; ============================================
; Install Sections
; ============================================

Section "Agent4j (required)" SecMain
  SectionIn RO

  SetOutPath "$INSTDIR"

  ; Files are injected by Tauri at build time.
  ; File /r "release\*.*"

  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; ── Register uninstall info ──
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "DisplayName"     "${APP_NAME}"
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "UninstallString" "$INSTDIR\uninstall.exe"
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "DisplayIcon"     "$INSTDIR\${APP_NAME}.exe"
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "DisplayVersion"  "${APP_VERSION}"
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "Publisher"       "${APP_PUBLISHER}"
  WriteRegStr   HKCU "${APP_UNINST_KEY}" "URLInfoAbout"    "${APP_WEB_SITE}"
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "NoModify"        1
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "NoRepair"        1

  ; ── Estimated size ──
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "EstimatedSize" "$0"
SectionEnd

Section "Desktop Shortcut" SecDesktop
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_NAME}.exe" "" "$INSTDIR\${APP_NAME}.exe" 0
SectionEnd

Section "Start Menu Shortcuts" SecStartMenu
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"           "$INSTDIR\${APP_NAME}.exe"  "" "$INSTDIR\${APP_NAME}.exe" 0
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe"
SectionEnd

Section "Auto-start with Windows" SecAutoStart
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${APP_NAME}" "$INSTDIR\${APP_NAME}.exe --minimized"
SectionEnd

; ============================================
; Section Descriptions
; ============================================
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMain}      "Install Agent4j core files (required)."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop}   "Create a shortcut on the desktop."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStartMenu} "Create shortcuts in the Start Menu."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecAutoStart} "Launch Agent4j automatically when Windows starts."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ============================================
; Uninstall Section
; ============================================

Section "Uninstall"
  ; ── Remove installed files ──
  Delete "$INSTDIR\${APP_NAME}.exe"
  Delete "$INSTDIR\uninstall.exe"
  ; Remove extra resources if present
  RMDir /r "$INSTDIR\resources"
  RMDir "$INSTDIR"

  ; ── Remove shortcuts ──
  Delete "$DESKTOP\${APP_NAME}.lnk"
  RMDir /r "$SMPROGRAMS\${APP_NAME}"

  ; ── Remove registry entries ──
  DeleteRegKey   HKCU "${APP_UNINST_KEY}"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${APP_NAME}"
SectionEnd
