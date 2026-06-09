@chcp 65001 > nul
cd /d C:\Users\sorghum\IdeaProjects\agent4j && git add -A && git commit -m "fix: null safety for workspace uninitialized - protect GoalEngine and GoalCommand with try-catch guards"