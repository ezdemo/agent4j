@chcp 65001 > nul
cd /d C:\Users\sorghum\IdeaProjects\agent4j && git add -A && git commit -m "fix: remove patrol sub-agent spawn from prompt - task blocks main flow, rely on auto-retry loop instead"