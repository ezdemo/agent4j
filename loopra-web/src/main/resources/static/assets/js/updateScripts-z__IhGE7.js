var t="normal",s="mirror",a="loopra.updateSource";function m(){try{return localStorage.getItem(a)==="mirror"?s:t}catch{return t}}function l(o){try{o==="mirror"?localStorage.setItem(a,s):localStorage.removeItem(a)}catch{}}var i="https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release";function u(o,n,r){return`setup${n?"-gui":""}${o==="mirror"?"-mirror":""}.${r}`}function c(o,n){const r=u(o,n,"ps1"),e=u(o,n,"sh");return{windows:`irm ${i}/${r} | iex`,windowsLabel:`irm ...${r} | iex`,unix:`curl -fsSL ${i}/${e} | bash`,unixLabel:`curl ...${e} | bash`}}function p(o,n){const r=c(o,n);return n?`请帮我执行 Loopra 桌面端自动更新。请使用当前操作系统对应的桌面运行时更新脚本：

- Windows 系统：在 PowerShell 中运行 \`${r.windows}\`
- macOS / Linux 系统：在终端中运行 \`${r.unix}\`

桌面运行时必须安装到 ~/.loopra-gui，配置继续使用 ~/.loopra。执行完成后请报告结果。`:`请帮我执行 Loopra 自动更新。根据当前操作系统平台，选择并运行对应的更新脚本：

- Windows 系统：在 PowerShell 中运行 \`${r.windows}\`
- macOS / Linux 系统：在终端中运行 \`${r.unix}\`

请先判断当前系统平台，然后执行对应的脚本。执行完成后请报告结果。`}export{m as a,p as i,t as n,l as o,c as r,s as t};
