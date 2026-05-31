; ============================================
; Agent4j Desktop — 自定义 NSIS 安装界面
; ============================================

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"

; ── Tauri 定义（由 Tauri 自动注入） ──
!define APP_NAME "${PRODUCT_NAME}"
!define APP_VERSION "${VERSION}"
!define APP_PUBLISHER "Agent4j"
!define APP_WEB_SITE "https://github.com/ezdemo/agent4j"
!define APP_DIR_REGKEY "Software\Microsoft\Windows\CurrentVersion\App Paths\${APP_NAME}.exe"
!define APP_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"

; ── 安装目录 ──
!define INSTALL_DIR "$LOCALAPPDATA\${APP_NAME}"

; ── 图标 ──
!define MUI_ICON "icons\icon.ico"
!define MUI_UNICON "icons\icon.ico"

; ── 欢迎页面 ──
!define MUI_WELCOMEFINISHPAGE_BITMAP "icons\128x128.png"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "icons\128x128.png"

; ── 标题 ──
!define MUI_ABORTWARNING
!define MUI_UNABORTWARNING

; ── 仅当前用户安装，不需要管理员权限 ──
RequestExecutionLevel user

; ============================================
; 界面
; ============================================

; 欢迎页
!define MUI_WELCOMEPAGE_TITLE "欢迎安装 Agent4j"
!define MUI_WELCOMEPAGE_TEXT "Agent4j 是一款基于 Java 的 AI 编码代理桌面客户端。$\r$\n$\r$\n点击下一步继续安装。"

; 安装完成页
!define MUI_FINISHPAGE_TITLE "安装完成"
!define MUI_FINISHPAGE_TEXT "Agent4j 已成功安装到您的计算机。$\r$\n$\r$\n点击完成启动应用。"
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_NAME}.exe"
!define MUI_FINISHPAGE_RUN_TEXT "启动 Agent4j"
!define MUI_FINISHPAGE_LINK "访问项目主页"
!define MUI_FINISHPAGE_LINK_LOCATION "${APP_WEB_SITE}"

; 卸载完成页
!define MUI_UNFINISHPAGE_TITLE "卸载完成"
!define MUI_UNFINISHPAGE_TEXT "Agent4j 已从您的计算机中移除。"

; ============================================
; 页面
; ============================================
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "license.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; ============================================
; 语言
; ============================================
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

; ============================================
; 安装区段
; ============================================

Section "Agent4j 主程序 (必选)" SecMain
  SectionIn RO

  ; 设置输出路径
  SetOutPath "$INSTDIR"

  ; 安装文件（由 Tauri 自动填充）
  ; File "release\*.*"

  ; 写入卸载程序
  WriteUninstaller "$INSTDIR\uninstall.exe"

  ; 注册卸载信息
  WriteRegStr HKCU "${APP_UNINST_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKCU "${APP_UNINST_KEY}" "UninstallString" "$INSTDIR\uninstall.exe"
  WriteRegStr HKCU "${APP_UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP_NAME}.exe"
  WriteRegStr HKCU "${APP_UNINST_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "${APP_UNINST_KEY}" "Publisher" "${APP_PUBLISHER}"
  WriteRegStr HKCU "${APP_UNINST_KEY}" "URLInfoAbout" "${APP_WEB_SITE}"
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "NoRepair" 1

  ; 计算安装大小
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKCU "${APP_UNINST_KEY}" "EstimatedSize" "$0"
SectionEnd

Section "创建桌面快捷方式" SecDesktop
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_NAME}.exe" "" "$INSTDIR\${APP_NAME}.exe" 0
SectionEnd

Section "创建开始菜单快捷方式" SecStartMenu
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_NAME}.exe" "" "$INSTDIR\${APP_NAME}.exe" 0
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk" "$INSTDIR\uninstall.exe"
SectionEnd

Section "开机自启动" SecAutoStart
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${APP_NAME}" "$INSTDIR\${APP_NAME.exe} --minimized"
SectionEnd

; ============================================
; 区段描述
; ============================================
!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMain}      "安装 Agent4j 主程序文件（必需）"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop}   "在桌面创建快捷方式"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStartMenu} "在开始菜单创建程序组"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecAutoStart} "Windows 启动时自动运行"
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ============================================
; 卸载区段
; ============================================

Section "Uninstall"
  ; 删除文件
  Delete "$INSTDIR\${APP_NAME}.exe"
  Delete "$INSTDIR\uninstall.exe"
  RMDir "$INSTDIR"

  ; 删除快捷方式
  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk"
  RMDir "$SMPROGRAMS\${APP_NAME}"

  ; 删除注册表项
  DeleteRegKey HKCU "${APP_UNINST_KEY}"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "${APP_NAME}"
SectionEnd
