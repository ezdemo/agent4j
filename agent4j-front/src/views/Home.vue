<template>
  <div class="home-view">
    <!-- 欢迎区域 -->
    <div class="welcome-section">
      <div class="welcome-content">
        <div class="logo-container">
          <div class="logo-icon">
            <img src="../assets/logo.png" alt="Agent4j Logo" class="logo-img" />
          </div>
          <div class="logo-glow"></div>
        </div>
        
        <h1 class="welcome-title">
          <span class="gradient-text">Agent4j</span>
        </h1>
        <p class="welcome-subtitle">智能 AI 代码助手</p>
        <p class="welcome-description">
          基于 Java 的 AI 代理框架，提供推理循环、工具调用、会话管理等完整能力
        </p>
        
        <div class="welcome-actions">
          <button class="btn btn-primary btn-lg" @click="startChat">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            开始对话
          </button>
          <button class="btn btn-secondary btn-lg" @click="viewTools">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
            </svg>
            查看工具
          </button>
        </div>
      </div>
    </div>
    
    <!-- 功能特性 -->
    <div class="features-section">
      <div class="section-header">
        <h2>核心功能</h2>
        <p>强大的 AI 代理能力，助力高效开发</p>
      </div>
      
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <h3>智能对话</h3>
          <p>基于大语言模型的自然语言交互，支持多轮上下文对话</p>
        </div>
        
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
            </svg>
          </div>
          <h3>工具调用</h3>
          <p>丰富的工具集，支持文件操作、代码分析、网络搜索等</p>
        </div>
        
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <h3>流式输出</h3>
          <p>实时流式响应，提供流畅的交互体验</p>
        </div>
        
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
              <line x1="8" y1="21" x2="16" y2="21"/>
              <line x1="12" y1="17" x2="12" y2="21"/>
            </svg>
          </div>
          <h3>会话管理</h3>
          <p>持久化会话历史，支持多会话切换和管理</p>
        </div>
        
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
          <h3>安全可靠</h3>
          <p>路径穿越防护、风暴断路器等安全机制</p>
        </div>
        
        <div class="feature-card">
          <div class="feature-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </div>
          <h3>高度可配置</h3>
          <p>灵活的配置选项，支持多种 AI 模型和参数调整</p>
        </div>
      </div>
    </div>
    
    <!-- 快速开始 -->
    <div class="quickstart-section">
      <div class="section-header">
        <h2>快速开始</h2>
        <p>三步开始使用 Agent4j</p>
      </div>
      
      <div class="steps-container">
        <div class="step-card">
          <div class="step-number">1</div>
          <div class="step-content">
            <h3>配置 API</h3>
            <p>在设置中配置您的 AI API 密钥和端点</p>
            <div class="step-command">
              <code>~/.agent4j/config.json</code>
            </div>
          </div>
        </div>
        
        <div class="step-connector">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </div>
        
        <div class="step-card">
          <div class="step-number">2</div>
          <div class="step-content">
            <h3>开始对话</h3>
            <p>在聊天界面输入您的问题或指令</p>
            <div class="step-command">
              <code>输入您的问题...</code>
            </div>
          </div>
        </div>
        
        <div class="step-connector">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </div>
        
        <div class="step-card">
          <div class="step-number">3</div>
          <div class="step-content">
            <h3>获取帮助</h3>
            <p>AI 助手会自动调用工具完成任务</p>
            <div class="step-command">
              <code>工具会自动执行</code>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 技术栈 -->
    <div class="tech-section">
      <div class="section-header">
        <h2>技术栈</h2>
        <p>基于成熟的技术构建</p>
      </div>
      
      <div class="tech-grid">
        <div class="tech-card">
          <div class="tech-icon">☕</div>
          <div class="tech-name">Java 8</div>
          <div class="tech-desc">核心运行时</div>
        </div>
        
        <div class="tech-card">
          <div class="tech-icon">🚀</div>
          <div class="tech-name">Vue 3</div>
          <div class="tech-desc">前端框架</div>
        </div>
        
        <div class="tech-card">
          <div class="tech-icon">⚡</div>
          <div class="tech-name">Vite</div>
          <div class="tech-desc">构建工具</div>
        </div>
        
        <div class="tech-card">
          <div class="tech-icon">🎨</div>
          <div class="tech-name">Pinia</div>
          <div class="tech-desc">状态管理</div>
        </div>
        
        <div class="tech-card">
          <div class="tech-icon">🔗</div>
          <div class="tech-name">OpenAI API</div>
          <div class="tech-desc">AI 模型接口</div>
        </div>
        
        <div class="tech-card">
          <div class="tech-icon">🛠</div>
          <div class="tech-name">Solon</div>
          <div class="tech-desc">IoC 容器</div>
        </div>
      </div>
    </div>
    
    <!-- 底部操作 -->
    <div class="cta-section">
      <div class="cta-content">
        <h2>准备好开始了吗？</h2>
        <p>立即体验 Agent4j 的强大功能</p>
        <div class="cta-actions">
          <button class="btn btn-primary btn-lg" @click="startChat">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            开始对话
          </button>
          <button class="btn btn-outline btn-lg" @click="viewHelp">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            查看文档
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const startChat = () => {
  router.push('/chat')
}

const viewTools = () => {
  router.push('/tools')
}

const viewHelp = () => {
  router.push('/help')
}
</script>

<style scoped>
.home-view {
  min-height: 100%;
  background: var(--bg);
  overflow-y: auto;
}

/* 欢迎区域 */
.welcome-section {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: var(--space-12) var(--space-6);
  background: linear-gradient(135deg, var(--bg) 0%, var(--bg-secondary) 100%);
}

.welcome-content {
  text-align: center;
  max-width: 600px;
}

.logo-container {
  position: relative;
  display: inline-block;
  margin-bottom: var(--space-6);
}

.logo-icon {
  width: 80px;
  height: 80px;
  background: transparent;
  border-radius: var(--radius-2xl);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.3);
  position: relative;
  z-index: 1;
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-glow {
  position: absolute;
  inset: -10px;
  background: var(--gradient-primary);
  border-radius: var(--radius-2xl);
  opacity: 0.3;
  filter: blur(20px);
  z-index: 0;
}

.welcome-title {
  font-size: var(--text-4xl);
  font-weight: var(--font-bold);
  margin-bottom: var(--space-2);
}

.gradient-text {
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: var(--text-xl);
  color: var(--fg-secondary);
  margin-bottom: var(--space-4);
}

.welcome-description {
  font-size: var(--text-base);
  color: var(--fg-muted);
  line-height: 1.6;
  margin-bottom: var(--space-8);
}

.welcome-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

/* 功能特性 */
.features-section {
  padding: var(--space-12) var(--space-6);
  background: var(--surface);
}

.section-header {
  text-align: center;
  margin-bottom: var(--space-8);
}

.section-header h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.section-header p {
  font-size: var(--text-base);
  color: var(--fg-muted);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}

.feature-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  transition: all var(--transition-fast);
}

.feature-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-4px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  background: var(--accent-soft);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  margin-bottom: var(--space-4);
}

.feature-card h3 {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.feature-card p {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  line-height: 1.6;
}

/* 快速开始 */
.quickstart-section {
  padding: var(--space-12) var(--space-6);
  background: var(--bg-secondary);
}

.steps-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  max-width: 1000px;
  margin: 0 auto;
  flex-wrap: wrap;
}

.step-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  text-align: center;
  flex: 1;
  min-width: 200px;
  max-width: 300px;
  transition: all var(--transition-fast);
}

.step-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-4px);
}

.step-number {
  width: 40px;
  height: 40px;
  background: var(--gradient-primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-lg);
  font-weight: var(--font-bold);
  color: white;
  margin: 0 auto var(--space-4);
}

.step-content h3 {
  font-size: var(--text-base);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.step-content p {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  margin-bottom: var(--space-3);
}

.step-command {
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-2) var(--space-3);
}

.step-command code {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--brand-primary);
}

.step-connector {
  color: var(--fg-muted);
  flex-shrink: 0;
}

/* 技术栈 */
.tech-section {
  padding: var(--space-12) var(--space-6);
  background: var(--surface);
}

.tech-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-4);
  max-width: 800px;
  margin: 0 auto;
}

.tech-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  text-align: center;
  transition: all var(--transition-fast);
}

.tech-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.tech-icon {
  font-size: 2rem;
  margin-bottom: var(--space-2);
}

.tech-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: 0.25rem;
}

.tech-desc {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

/* 底部操作 */
.cta-section {
  padding: var(--space-12) var(--space-6);
  background: linear-gradient(135deg, var(--bg) 0%, var(--bg-secondary) 100%);
}

.cta-content {
  text-align: center;
  max-width: 600px;
  margin: 0 auto;
}

.cta-content h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.cta-content p {
  font-size: var(--text-base);
  color: var(--fg-muted);
  margin-bottom: var(--space-6);
}

.cta-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .welcome-section {
    min-height: 50vh;
    padding: var(--space-8) var(--space-4);
  }
  
  .welcome-title {
    font-size: var(--text-3xl);
  }
  
  .welcome-subtitle {
    font-size: var(--text-lg);
  }
  
  .welcome-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .welcome-actions .btn {
    width: 100%;
    max-width: 300px;
    justify-content: center;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
  }
  
  .steps-container {
    flex-direction: column;
  }
  
  .step-connector {
    transform: rotate(90deg);
  }
  
  .step-card {
    max-width: 100%;
  }
  
  .tech-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .cta-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .cta-actions .btn {
    width: 100%;
    max-width: 300px;
    justify-content: center;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .welcome-section {
  background: linear-gradient(135deg, var(--bg) 0%, var(--bg-tertiary) 100%);
}

[data-theme="dark"] .features-section,
[data-theme="dark"] .tech-section {
  background: var(--bg-secondary);
}

[data-theme="dark"] .feature-card,
[data-theme="dark"] .step-card,
[data-theme="dark"] .tech-card {
  background: var(--bg-tertiary);
  border-color: var(--border);
}

[data-theme="dark"] .feature-card:hover,
[data-theme="dark"] .step-card:hover,
[data-theme="dark"] .tech-card:hover {
  border-color: var(--brand-primary-light);
}

[data-theme="dark"] .cta-section {
  background: linear-gradient(135deg, var(--bg) 0%, var(--bg-tertiary) 100%);
}
</style>