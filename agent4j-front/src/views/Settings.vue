<template>
  <div class="settings-page">
    <!-- 左侧导航 -->
    <nav class="settings-nav">
      <div class="nav-header">
        <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
          <circle cx="12" cy="12" r="3"/>
          <path
              d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        <span>设置</span>
      </div>

      <div class="nav-items">
        <button
            v-for="tab in tabs"
            :key="tab.id"
            :class="{ active: activeTab === tab.id }"
            class="nav-item"
            @click="activeTab = tab.id"
        >
          <span class="nav-icon" v-html="tab.icon"></span>
          <span class="nav-label">{{ tab.label }}</span>
          <span v-if="tab.badge" class="nav-badge">{{ tab.badge }}</span>
        </button>
      </div>
    </nav>

    <!-- 主内容区 -->
    <main class="settings-main">
      <!-- 顶部操作栏 -->
      <header class="settings-header">
        <div class="header-title">
          <h2>{{ currentTab?.label || '设置' }}</h2>
          <p class="header-desc">{{ currentTab?.description }}</p>
        </div>
        <div v-if="activeTab !== 'openapi'" class="header-actions">
          <button :disabled="loading" class="btn btn-ghost" @click="resetToDefaults">
            <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
              <path d="M21 3v5h-5"/>
              <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
              <path d="M8 16H3v5"/>
            </svg>
            重置默认
          </button>
          <button :disabled="loading || !hasChanges" class="btn btn-primary" @click="saveSettings">
            <svg v-if="!loading" fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"
                 width="14">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17 21 17 13 7 13 7 21"/>
              <polyline points="7 3 7 8 15 8"/>
            </svg>
            <svg v-else class="animate-spin" fill="none" height="14" stroke="currentColor" stroke-width="2"
                 viewBox="0 0 24 24"
                 width="14">
              <path d="M21 12a9 9 0 11-6.219-8.56"/>
            </svg>
            {{ loading ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </header>

      <!-- 设置内容区 -->
      <div class="settings-content">
        <!-- 基本设置 -->
        <section v-if="activeTab === 'general'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <h3>外观设置</h3>
              <p>自定义界面主题</p>
            </div>
            <div class="card-body">
              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">界面主题</label>
                  <p class="setting-hint">选择适合你的视觉风格</p>
                </div>
                <div class="setting-control">
                  <div class="theme-grid">
                    <button
                        v-for="theme in themes"
                        :key="theme.value"
                        :class="{ active: settings.theme === theme.value }"
                        class="theme-option"
                        @click="settings.theme = theme.value"
                    >
                      <span :class="theme.value" class="theme-preview"></span>
                      <span class="theme-name">{{ theme.label }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 服务器设置 -->
        <section v-if="activeTab === 'server'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <h3>连接设置</h3>
              <p>配置后端 Agent4j 服务的连接参数</p>
            </div>
            <div class="card-body">
              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">API 地址</label>
                  <p class="setting-hint">留空使用默认代理（localhost:8097）</p>
                </div>
                <div class="setting-control">
                  <div class="input-group">
                    <input
                        v-model="settings.server.apiBaseUrl"
                        class="form-input"
                        placeholder="http://localhost:8097"
                        type="text"
                    />
                    <button
                        :disabled="checkingConnection"
                        class="btn btn-secondary"
                        @click="checkServerConnection"
                    >
                      <svg v-if="checkingConnection" class="animate-spin" fill="none" height="14" stroke="currentColor"
                           stroke-width="2" viewBox="0 0 24 24" width="14">
                        <path d="M21 12a9 9 0 11-6.219-8.56"/>
                      </svg>
                      <svg v-else fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"
                           width="14">
                        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                        <polyline points="22 4 12 14.01 9 11.01"/>
                      </svg>
                      {{ checkingConnection ? '检测中...' : '测试连接' }}
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="connectionChecked" class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">连接状态</label>
                </div>
                <div class="setting-control">
                  <div :class="{ ok: connectionOk, error: !connectionOk }" class="connection-status">
                    <span class="status-dot"></span>
                    <span>{{ connectionOk ? '连接成功' : '连接失败' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- AI 设置 -->
        <section v-if="activeTab === 'ai'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <h3>模型配置</h3>
              <p>配置 LLM API 连接与模型参数</p>
            </div>
            <div class="card-body">
              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">API 地址</label>
                  <p class="setting-hint">OpenAI 兼容 API 的基础 URL</p>
                </div>
                <div class="setting-control">
                  <input
                      v-model="settings.ai.baseUrl"
                      class="form-input"
                      placeholder="https://api.openai.com/v1"
                      type="text"
                  />
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">API 密钥</label>
                  <p class="setting-hint">用于身份验证的密钥</p>
                </div>
                <div class="setting-control">
                  <div class="input-with-toggle">
                    <input
                        v-model="settings.ai.apiKey"
                        :type="showApiKey ? 'text' : 'password'"
                        class="form-input"
                        placeholder="sk-..."
                    />
                    <button
                        :title="showApiKey ? '隐藏' : '显示'"
                        class="toggle-visibility"
                        @click="showApiKey = !showApiKey"
                    >
                      <svg v-if="showApiKey" fill="none" height="14" stroke="currentColor" stroke-width="2"
                           viewBox="0 0 24 24" width="14">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                        <circle cx="12" cy="12" r="3"/>
                      </svg>
                      <svg v-else fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"
                           width="14">
                        <path
                            d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                        <line x1="1" x2="23" y1="1" y2="23"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">模型</label>
                  <p class="setting-hint">选择要使用的 AI 模型</p>
                </div>
                <div class="setting-control">
                  <div class="select-wrapper">
                    <select v-model="settings.ai.model" class="form-select">
                      <option v-for="model in availableModels" :key="model.name" :value="model.name">
                        {{ model.name }}
                      </option>
                    </select>
                    <svg class="select-arrow" fill="none" height="12" stroke="currentColor" stroke-width="2"
                         viewBox="0 0 24 24" width="12">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">推理强度</label>
                  <p class="setting-hint">AI 推理的详细程度</p>
                </div>
                <div class="setting-control">
                  <div class="select-wrapper">
                    <select v-model="settings.ai.reasoningEffort" class="form-select">
                      <option value="low">低 - 快速响应</option>
                      <option value="medium">中 - 平衡模式</option>
                      <option value="high">高 - 深度思考</option>
                      <option value="max">最大 - 极致推理</option>
                    </select>
                    <svg class="select-arrow" fill="none" height="12" stroke="currentColor" stroke-width="2"
                         viewBox="0 0 24 24" width="12">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">可用模型列表</label>
                  <p class="setting-hint">每行一个模型名称</p>
                </div>
                <div class="setting-control">
                  <textarea
                      v-model="settings.ai.availableModelsText"
                      class="form-textarea"
                      placeholder="deepseek-v4-flash&#10;gpt-4&#10;gpt-4-turbo"
                      rows="4"
                  ></textarea>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 工作区设置 -->
        <section v-if="activeTab === 'workspace'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <h3>工作目录</h3>
              <p>配置工作目录与编辑行为</p>
            </div>
            <div class="card-body">
              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">工作区路径</label>
                  <p class="setting-hint">默认工作目录</p>
                </div>
                <div class="setting-control">
                  <input
                      v-model="settings.workspace.dir"
                      class="form-input"
                      placeholder="."
                      type="text"
                  />
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">编辑模式</label>
                  <p class="setting-hint">手动模式需要审批写入操作</p>
                </div>
                <div class="setting-control">
                  <div class="radio-group">
                    <label :class="{ active: settings.workspace.mode === true }" class="radio-option">
                      <input v-model="settings.workspace.mode" :value="true" type="radio"/>
                      <span class="radio-label">
                        <span class="radio-title">手动模式</span>
                        <span class="radio-desc">写入操作需审批</span>
                      </span>
                    </label>
                    <label :class="{ active: settings.workspace.mode === false }" class="radio-option">
                      <input v-model="settings.workspace.mode" :value="false" type="radio"/>
                      <span class="radio-label">
                        <span class="radio-title">自由模式</span>
                        <span class="radio-desc">直接执行写入</span>
                      </span>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 安全设置 -->
        <section v-if="activeTab === 'security'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <h3>安全防护</h3>
              <p>配置安全策略和防护机制</p>
            </div>
            <div class="card-body">
              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">风暴断路器</label>
                  <p class="setting-hint">防止工具调用死循环（滑动窗口去重）</p>
                </div>
                <div class="setting-control">
                  <label class="toggle-switch">
                    <input v-model="settings.security.stormBreaker" type="checkbox"/>
                    <span class="toggle-slider"></span>
                  </label>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">路径穿越防护</label>
                  <p class="setting-hint">阻止访问工作区外的文件</p>
                </div>
                <div class="setting-control">
                  <label class="toggle-switch">
                    <input v-model="settings.security.pathTraversal" type="checkbox"/>
                    <span class="toggle-slider"></span>
                  </label>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">命令白名单</label>
                  <p class="setting-hint">只允许执行白名单中的命令</p>
                </div>
                <div class="setting-control">
                  <label class="toggle-switch">
                    <input v-model="settings.security.commandWhitelist" type="checkbox"/>
                    <span class="toggle-slider"></span>
                  </label>
                </div>
              </div>

              <div class="setting-row">
                <div class="setting-info">
                  <label class="setting-label">审计日志</label>
                  <p class="setting-hint">记录所有工具调用操作</p>
                </div>
                <div class="setting-control">
                  <label class="toggle-switch">
                    <input v-model="settings.security.auditLog" type="checkbox"/>
                    <span class="toggle-slider"></span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- OpenAPI 设置 -->
        <section v-if="activeTab === 'openapi'" class="settings-section">
          <div class="section-card">
            <div class="card-header">
              <div class="openapi-header">
                <div>
                  <h3>OpenAPI 接口源</h3>
                  <p>管理 OpenAPI 接口源</p>
                </div>
                <div class="openapi-actions">
                  <button class="btn btn-primary btn-sm" @click="openAdd">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <line x1="12" x2="12" y1="5" y2="19"/>
                      <line x1="5" x2="19" y1="12" y2="12"/>
                    </svg>
                    添加
                  </button>
                  <button :disabled="openapiLoading" class="btn btn-sm" @click="loadOpenApiData">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <path d="M23 4v6h-6"/>
                      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                    </svg>
                    刷新
                  </button>
                </div>
              </div>
            </div>
            <div class="card-body">
              <!-- 接口搜索 -->
              <div class="openapi-search">
                <div class="search-row">
                  <input v-model="searchKeyword" class="form-input" placeholder="搜索接口文档（多关键词用空格分隔）"
                         type="text" @keyup.enter="doSearch"/>
                  <button :disabled="searching || !searchKeyword.trim()" class="btn btn-primary btn-sm"
                          @click="doSearch">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <circle cx="11" cy="11" r="8"/>
                      <line x1="21" x2="16.65" y1="21" y2="16.65"/>
                    </svg>
                    搜索
                  </button>
                </div>

                <!-- 搜索结果 -->
                <div v-if="searchResult" class="search-result">
                  <div class="result-head">
                    <span>搜索结果</span>
                    <button class="btn-icon-xs" @click="clearSearch">×</button>
                  </div>
                  <div v-if="searching" class="result-loading">搜索中...</div>
                  <div v-else-if="Array.isArray(searchResult) && searchResult.length === 0" class="result-empty">
                    未找到匹配的接口
                  </div>
                  <div v-else-if="typeof searchResult === 'string'" class="result-empty">{{ searchResult }}</div>
                  <div v-else class="result-list">
                    <div v-for="(item, i) in searchResult" :key="i" class="result-item">
                      <div class="ri-head">
                        <code class="ri-name">{{ item.api_name }}</code>
                        <span class="ri-cat">{{ item.category }}</span>
                      </div>
                      <div class="ri-desc">{{ item.description }}</div>
                      <div class="ri-endpoint"><code>{{ item.endpoint }}</code></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 加载 / 错误 -->
              <div v-if="openapiLoading && openapiSources.length === 0" class="state-box">
                <div class="spinner"></div>
                <p>加载中...</p>
              </div>
              <div v-else-if="openapiError" class="state-box error">
                <p>{{ openapiError }}</p>
                <button class="btn btn-sm" @click="loadOpenApiData">重试</button>
              </div>


            </div>
          </div>
          <!-- 源卡片列表 -->
          <div v-if="openapiSources.length > 0" class="openapi-card-list">
            <div v-for="source in openapiSources" :key="source.docUrl" class="openapi-source-card">
              <div class="card-head">
                <div :class="source.status" class="status-tag">
                  <span class="dot"></span>
                  {{ statusLabel(source.status) }}
                </div>
                <div class="card-actions">
                  <button class="btn-icon-xs" title="编辑" @click="openEdit(source)">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button :disabled="openapiLoading" class="btn-icon-xs" title="刷新" @click="refreshSource(source)">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <path d="M23 4v6h-6"/>
                      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                    </svg>
                  </button>
                  <button class="btn-icon-xs danger" title="删除" @click="removeSource(source)">
                    <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </div>
              <div class="card-body">
                <div class="info-rows">
                  <div class="row">
                    <span class="label">文档</span>
                    <code class="val">{{ source.docUrl }}</code>
                  </div>
                  <div v-if="source.authType && source.authType !== 'none'" class="row">
                    <span class="label">认证</span>
                    <code class="val">{{ authLabel(source) }}</code>
                  </div>
                  <div v-if="source.headers && Object.keys(source.headers).length" class="row">
                    <span class="label">请求头</span>
                    <code class="val">{{ JSON.stringify(source.headers) }}</code>
                  </div>
                </div>
              </div>
              <div v-if="source.status === 'error' && source.errorMessage" class="card-error">
                <svg fill="none" height="11" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="11">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="15" x2="9" y1="9" y2="15"/>
                  <line x1="9" x2="15" y1="9" y2="15"/>
                </svg>
                {{ source.errorMessage }}
              </div>
            </div>
          </div>

          <div v-if="openapiSources.length === 0 && !openapiLoading" class="state-box">
            <p style="color:var(--fg-3)">暂无接口源，点击上方「添加」按钮注册一个 OpenAPI 文档</p>
          </div>
        </section>

      </div>
    </main>
  </div>

  <!-- 添加/编辑对话框 -->
  <Teleport to="body">
    <div v-if="showAddDialog" class="add-mask" @click.self="showAddDialog = false">
      <div class="add-dialog">
        <div class="add-head">
          <span>{{ editingSource ? '编辑 OpenAPI 接口源' : '添加 OpenAPI 接口源' }}</span>
          <button class="btn-icon-xs" @click="showAddDialog = false">×</button>
        </div>
        <div class="add-body">
          <div class="field">
            <label>文档地址 <span class="req">*</span></label>
            <input v-model="form.docUrl" class="form-input" placeholder="https://petstore.swagger.io/v2/swagger.json"
                   type="text"/>
            <p class="hint">支持 http://、https:// 和 classpath: 开头</p>
          </div>
          <div class="field">
            <label>请求头（可选）</label>
            <div class="header-list">
              <div v-for="(h, i) in form.headerList" :key="i" class="header-row">
                <input v-model="h.key" class="form-input" placeholder="名称" type="text"/>
                <input v-model="h.value" class="form-input" placeholder="值" type="text"/>
                <button class="btn-icon-xs" @click="form.headerList.splice(i, 1)">×</button>
              </div>
              <button class="btn btn-text btn-sm" @click="form.headerList.push({ key: '', value: '' })">+ 添加请求头
              </button>
            </div>
          </div>

          <!-- 认证方式 -->
          <div class="field">
            <label>认证方式</label>
            <div class="auth-tabs">
              <button :class="{ active: form.authType === 'none' }" class="auth-tab" @click="form.authType = 'none'">
                无
              </button>
              <button :class="{ active: form.authType === 'bearer' }" class="auth-tab"
                      @click="form.authType = 'bearer'">Bearer Token
              </button>
              <button :class="{ active: form.authType === 'apikey' }" class="auth-tab"
                      @click="form.authType = 'apikey'">API Key
              </button>
              <button :class="{ active: form.authType === 'basic' }" class="auth-tab" @click="form.authType = 'basic'">
                用户名/密码
              </button>
            </div>
          </div>

          <div v-if="form.authType === 'bearer'" class="field">
            <label>Token <span class="req">*</span></label>
            <input v-model="form.bearerToken" class="form-input" placeholder="sk-xxxxxx" type="text"/>
          </div>

          <div v-if="form.authType === 'apikey'" class="field">
            <label>Header 名称 <span class="req">*</span></label>
            <input v-model="form.apiKeyName" class="form-input" placeholder="X-API-Key" type="text"/>
            <label style="margin-top:8px">Header 值 <span class="req">*</span></label>
            <input v-model="form.apiKeyValue" class="form-input" placeholder="your-api-key" type="text"/>
          </div>

          <div v-if="form.authType === 'basic'" class="field">
            <label>用户名 <span class="req">*</span></label>
            <input v-model="form.basicUser" class="form-input" placeholder="admin" type="text"/>
            <label style="margin-top:8px">密码 <span class="req">*</span></label>
            <input v-model="form.basicPass" class="form-input" placeholder="password" type="password"/>
          </div>
        </div>
        <div class="add-foot">
          <button class="btn" @click="showAddDialog = false">取消</button>
          <button :disabled="openapiSubmitting" class="btn btn-primary" @click="submitAdd">
            {{ openapiSubmitting ? '提交中...' : (editingSource ? '保存' : '添加') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {useAppStore} from '../stores/app'
import {configAPI, openApiAPI} from '../services/api'

const store = useAppStore()

// 主题直接绑定 store
const settings = reactive({
  get theme() {
    return store.settings.theme
  },
  set theme(v) {
    store.settings.theme = v
  },
  server: {apiBaseUrl: '', autoConnect: true},
  ai: {baseUrl: '', apiKey: '', model: '', reasoningEffort: 'max', availableModelsText: ''},
  workspace: {dir: '', mode: false},
  security: {
    stormBreaker: true,
    pathTraversal: true,
    commandWhitelist: true,
    auditLog: true
  }
})

const activeTab = ref('general')
const showApiKey = ref(false)
const loading = ref(false)
const availableModels = ref([])
const checkingConnection = ref(false)
const connectionOk = ref(false)
const connectionChecked = ref(false)
const hasChanges = ref(false)

// 标签页配置
const tabs = [
  {
    id: 'general',
    label: '基本设置',
    description: '界面主题设置',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
    </svg>`
  },
  {
    id: 'server',
    label: '服务器',
    description: '后端服务连接配置',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>
    </svg>`
  },
  {
    id: 'ai',
    label: 'AI 模型',
    description: 'LLM API 和模型参数配置',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a2 2 0 0 1 0 4h-1.17A7 7 0 0 1 14 22h-4a7 7 0 0 1-6.83-4H2a2 2 0 0 1 0-4h1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2z"/>
      <circle cx="12" cy="14" r="3"/>
    </svg>`
  },
  {
    id: 'workspace',
    label: '工作区',
    description: '工作目录和编辑行为配置',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
    </svg>`
  },
  {
    id: 'security',
    label: '安全',
    description: '安全策略和防护机制',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>`
  },
  {
    id: 'openapi',
    label: 'OpenAPI',
    description: '管理 OpenAPI 接口源',
    icon: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M16 3h5v5M8 3H3v5M3 16v5h5M16 21h5v-5"/>
      <line x1="21" x2="12" y1="3" y2="12"/>
      <line x1="3" x2="12" y1="3" y2="12"/>
      <line x1="21" x2="12" y1="21" y2="12"/>
      <line x1="3" x2="12" y1="21" y2="12"/>
    </svg>`
  }
]

// 主题选项
const themes = [
  {value: 'light', label: '浅色'},
  {value: 'dark', label: '深色'},
  {value: 'retro', label: '复古绿'},
  {value: 'retro-yellow', label: '复古黄'}
]

// 计算属性
const currentTab = computed(() => tabs.find(t => t.id === activeTab.value))

// 监听设置变化
watch(settings, () => {
  hasChanges.value = true
}, {deep: true})

// 加载设置
const loadSettings = async () => {
  loading.value = true
  try {
    const [configResponse, modelsResponse] = await Promise.all([
      configAPI.getConfig(),
      configAPI.getModels()
    ])

    if (configResponse.success && configResponse.data) {
      const config = configResponse.data
      // 优先读 localStorage 中实际连接的地址
      settings.server.apiBaseUrl = localStorage.getItem('agent4j-api-base') || config.serverApiBaseUrl || ''
      settings.ai.baseUrl = config.baseUrl || ''
      settings.ai.model = config.model || ''
      settings.ai.reasoningEffort = config.reasoningEffort || 'max'

      if (config.availableModels && Array.isArray(config.availableModels)) {
        settings.ai.availableModelsText = config.availableModels.join('\n')
      }

      settings.workspace.dir = config.workspaceDir || config.workspace || '.'
      settings.workspace.mode = config.hitl === true

      // 加载安全设置
      if (config.security) {
        Object.assign(settings.security, config.security)
      }
    } else {
      message.error(configResponse.error || '加载配置失败')
    }

    if (modelsResponse.success && modelsResponse.data) {
      availableModels.value = modelsResponse.data.models || []
    }
  } catch (err) {
    console.error('加载配置失败:', err)
    message.error('加载配置失败: ' + err.message)

    // 设置默认值
    settings.ai.baseUrl = 'https://api.deepseek.com/v1'
    settings.ai.model = 'deepseek-v4-flash'
    settings.ai.reasoningEffort = 'max'
    settings.workspace.dir = '.'
    settings.workspace.mode = false
    availableModels.value = [
      {name: 'deepseek-v4-flash', active: true},
      {name: 'gpt-4', active: false},
      {name: 'gpt-4-turbo', active: false},
      {name: 'gpt-3.5-turbo', active: false}
    ]
  } finally {
    loading.value = false
    hasChanges.value = false
  }
}

// 保存设置
const saveSettings = async () => {
  loading.value = true
  try {
    // 同步更新 localStorage 中的实际连接地址
    if (settings.server.apiBaseUrl && settings.server.apiBaseUrl.trim()) {
      localStorage.setItem('agent4j-api-base', settings.server.apiBaseUrl.trim())
    }

    // 准备配置更新
    const configToUpdate = {
      baseUrl: settings.ai.baseUrl,
      apiKey: settings.ai.apiKey,
      model: settings.ai.model,
      reasoningEffort: settings.ai.reasoningEffort,
      availableModels: settings.ai.availableModelsText.split('\n').map(s => s.trim()).filter(s => s),
      hitl: settings.workspace.mode === true,
      security: {...settings.security}
    }

    const response = await configAPI.updateConfig(configToUpdate)

    if (response.success) {
      // 切换工作目录
      if (settings.workspace.dir && settings.workspace.dir.trim()) {
        try {
          await configAPI.switchWorkspace(settings.workspace.dir.trim())
        } catch (e) {
          console.warn('切换工作目录失败:', e)
        }
      }

      const serverMsg = typeof response.data === 'string'
          ? response.data
          : (response.data?.message || '设置已保存')
      message.success(serverMsg)
      hasChanges.value = false
    } else {
      message.error(response.error || '保存失败')
    }
  } catch (err) {
    console.error('保存配置失败:', err)
    message.error('保存失败: ' + err.message)
  } finally {
    loading.value = false
  }
}

// 测试服务器连接
const checkServerConnection = async () => {
  checkingConnection.value = true
  connectionChecked.value = false

  try {
    const baseUrl = settings.server.apiBaseUrl.trim() || '/api'
    const url = baseUrl.endsWith('/api')
        ? baseUrl + '/agent/status'
        : baseUrl.replace(/\/+$/, '') + '/api/agent/status'

    const resp = await fetch(url, {
      signal: AbortSignal.timeout(5000)
    })
    connectionOk.value = resp.ok
  } catch {
    connectionOk.value = false
  }

  connectionChecked.value = true
  checkingConnection.value = false
}

// 重置为默认设置
const resetToDefaults = () => {
  if (confirm('确定要重置所有设置为默认值吗？此操作不可撤销。')) {
    store.resetSettings()
    loadSettings()
    message.success('设置已重置为默认值')
  }
}

// ==================== OpenAPI ====================
const openapiLoading = ref(false)
const openapiError = ref('')
const openapiSubmitting = ref(false)
const showAddDialog = ref(false)
const editingSource = ref(null)
const openapiSources = ref([])

// 搜索
const searchKeyword = ref('')
const searchResult = ref(null)
const searching = ref(false)

const form = ref({
  docUrl: '', headerList: [],
  authType: 'none', authConfig: {},
  bearerToken: '', apiKeyName: '', apiKeyValue: '', basicUser: '', basicPass: ''
})

function statusLabel(s) {
  return {loaded: '已加载', error: '失败', disabled: '禁用'}[s] || s
}

function authLabel(source) {
  if (!source.authType || source.authType === 'none') return ''
  const c = source.authConfig || {}
  if (source.authType === 'bearer') return 'Bearer Token'
  if (source.authType === 'apikey') return `API Key (${c.name || ''})`
  if (source.authType === 'basic') return `Basic (${c.username || ''})`
  return source.authType
}

// 搜索
async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searching.value = true
  try {
    const res = await openApiAPI.searchApis(kw)
    searchResult.value = res.data
  } catch (e) {
    searchResult.value = '搜索失败: ' + (e.message || '')
  } finally {
    searching.value = false
  }
}

function clearSearch() {
  searchKeyword.value = ''
  searchResult.value = null
}

async function loadOpenApiData() {
  openapiLoading.value = true
  openapiError.value = ''
  try {
    const sr = await openApiAPI.getSources()
    openapiSources.value = (sr.data || []).filter(s => s.docUrl)
    message.success('刷新成功')
  } catch (e) {
    openapiError.value = e.message || '加载失败'
    message.error('刷新失败: ' + (e.message || ''))
  } finally {
    openapiLoading.value = false
  }
}

// 打开对话框
function openAdd() {
  editingSource.value = null
  resetForm()
  showAddDialog.value = true
}

function openEdit(source) {
  editingSource.value = source
  form.value.docUrl = source.docUrl || ''

  const hl = []
  if (source.headers) {
    for (const [k, v] of Object.entries(source.headers)) {
      hl.push({key: k, value: v})
    }
  }
  form.value.headerList = hl

  const authType = source.authType || 'none'
  form.value.authType = authType
  const ac = source.authConfig || {}
  form.value.bearerToken = ac.token || ''
  form.value.apiKeyName = ac.name || ''
  form.value.apiKeyValue = ac.value || ''
  form.value.basicUser = ac.username || ''
  form.value.basicPass = ac.password || ''

  showAddDialog.value = true
}

function resetForm() {
  form.value = {
    docUrl: '', headerList: [],
    authType: 'none', authConfig: {},
    bearerToken: '', apiKeyName: '', apiKeyValue: '', basicUser: '', basicPass: ''
  }
}

// 提交
async function submitAdd() {
  if (!form.value.docUrl) {
    message.warning('请填写文档地址')
    return
  }
  openapiSubmitting.value = true
  try {
    const headers = {}
    form.value.headerList.forEach(h => {
      if (h.key && h.value) headers[h.key] = h.value
    })

    let authType = form.value.authType || 'none'
    let authConfig = null
    if (authType === 'bearer' && form.value.bearerToken) {
      authConfig = {token: form.value.bearerToken}
    } else if (authType === 'apikey' && form.value.apiKeyName && form.value.apiKeyValue) {
      authConfig = {name: form.value.apiKeyName, value: form.value.apiKeyValue}
    } else if (authType === 'basic' && form.value.basicUser && form.value.basicPass) {
      authConfig = {username: form.value.basicUser, password: form.value.basicPass}
    } else {
      authType = 'none'
    }

    if (editingSource.value) {
      const oldDocUrl = editingSource.value.docUrl
      if (oldDocUrl === form.value.docUrl) {
        const res = await openApiAPI.refreshSource(form.value.docUrl, headers, authType, authConfig)
        if (res.success !== false) {
          message.success('修改成功')
        } else {
          message.error(res.error || '修改失败')
          openapiSubmitting.value = false
          return
        }
      } else {
        await openApiAPI.removeSource(oldDocUrl)
        const res = await openApiAPI.addSource(form.value.docUrl, headers, authType, authConfig)
        if (res.success !== false) {
          message.success('修改成功')
        } else {
          message.error(res.error || '修改失败')
          openapiSubmitting.value = false
          return
        }
      }
    } else {
      const res = await openApiAPI.addSource(form.value.docUrl, headers, authType, authConfig)
      if (res.success !== false) {
        message.success('添加成功')
      } else {
        message.error(res.error || '添加失败')
        openapiSubmitting.value = false
        return
      }
    }

    showAddDialog.value = false
    resetForm()
    editingSource.value = null
    await loadOpenApiData()
  } catch (e) {
    message.error('操作失败: ' + (e.message || ''))
  } finally {
    openapiSubmitting.value = false
  }
}

async function refreshSource(source) {
  openapiLoading.value = true
  try {
    const res = await openApiAPI.refreshSource(
        source.docUrl,
        source.headers || {},
        source.authType || 'none',
        source.authConfig || null
    )
    console.log('刷新响应:', res)
    if (res && res.success !== false) {
      message.success('刷新成功')
      await loadOpenApiData()
    } else {
      message.error(res?.error || '刷新失败')
    }
  } catch (e) {
    console.error('刷新失败:', e)
    message.error('刷新失败: ' + (e.message || ''))
  } finally {
    openapiLoading.value = false
  }
}

async function removeSource(source) {
  if (!confirm(`确定移除接口源「${source.docUrl}」？`)) return
  try {
    const res = await openApiAPI.removeSource(source.docUrl)
    if (res.success !== false) {
      message.success('已移除')
      await loadOpenApiData()
    } else {
      message.error(res.error || '移除失败')
    }
  } catch (e) {
    message.error('移除失败: ' + (e.message || ''))
  }
}

// 初始化
onMounted(() => {
  loadSettings()
  loadOpenApiData()
})
</script>

<style scoped>
/* 基础布局 */
.settings-page {
  display: flex;
  height: 600px;
  background: var(--bg);
  color: var(--fg);
  font-family: var(--sans);
}

/* 左侧导航 */
.settings-nav {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-2);
  border-right: 1px solid var(--border);
  padding: 16px 0;
}

.nav-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
  border-bottom: 1px solid var(--border);
  margin-bottom: 8px;
}

.nav-items {
  flex: 1;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--fg-2);
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--r);
  cursor: pointer;
  transition: all var(--t);
  text-align: left;
  width: 100%;
}

.nav-item:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.nav-item.active {
  background: var(--accent-bg);
  color: var(--accent);
  font-weight: 600;
}

.nav-icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.8;
}

.nav-item.active .nav-icon {
  opacity: 1;
}

.nav-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-badge {
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 600;
  background: var(--accent);
  color: white;
  border-radius: 10px;
  line-height: 1;
}

/* 主内容区 */
.settings-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
  flex-shrink: 0;
}

.header-title h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
}

.header-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--fg-3);
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* 设置内容区 */
.settings-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  min-height: 0;
}

.settings-section {
  max-width: 800px;
}

/* OpenAPI 样式 */
.openapi-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.openapi-actions {
  display: flex;
  gap: 8px;
}

.openapi-search {
  margin: 16px;
}

.search-row {
  display: flex;
  gap: 8px;
}

.search-row .form-input {
  flex: 1;
}

.search-result {
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 13px;
}

.result-loading, .result-empty {
  padding: 12px 0;
  color: var(--fg-3);
  font-size: 13px;
  text-align: center;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.result-item {
  padding: 8px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
}

.ri-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.ri-name {
  font-weight: 600;
  font-size: 13px;
}

.ri-cat {
  font-size: 11px;
  color: var(--fg-3);
}

.ri-desc {
  font-size: 12px;
  color: var(--fg-2);
  margin-bottom: 4px;
}

.ri-endpoint {
  font-size: 11px;
  color: var(--fg-3);
}

.ri-endpoint code {
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
}

.state-box {
  padding: 24px;
  text-align: center;
  color: var(--fg-3);
}

.state-box.error {
  color: var(--red);
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 8px;
}

.openapi-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.openapi-source-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  overflow: hidden;
}

.openapi-source-card .card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}

.status-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}

.status-tag .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-tag.loaded {
  color: var(--green);
}

.status-tag.loaded .dot {
  background: var(--green);
}

.status-tag.error {
  color: var(--red);
}

.status-tag.error .dot {
  background: var(--red);
}

.status-tag.disabled {
  color: var(--fg-3);
}

.status-tag.disabled .dot {
  background: var(--fg-3);
}

.card-actions {
  display: flex;
  gap: 4px;
}

.btn-icon-xs {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--fg-2);
  cursor: pointer;
  border-radius: var(--r-sm);
  transition: all var(--t);
}

.btn-icon-xs:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.btn-icon-xs.danger:hover {
  background: var(--red-bg);
  color: var(--red);
}

.openapi-source-card .card-body {
  padding: 12px 16px;
}

.info-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-rows .row {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.info-rows .label {
  color: var(--fg-3);
  min-width: 40px;
}

.info-rows .val {
  color: var(--fg-2);
  word-break: break-all;
}

.info-rows .val code {
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  font-size: 11px;
}

.card-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--red-bg);
  color: var(--red);
  font-size: 12px;
  border-top: 1px solid var(--border);
}

/* 添加对话框 */
.add-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.add-dialog {
  width: 480px;
  max-width: 90vw;
  max-height: 80vh;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.add-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
}

.add-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.add-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.field .req {
  color: var(--red);
}

.field .hint {
  font-size: 11px;
  color: var(--fg-3);
  margin-top: 4px;
}

.header-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-row .form-input {
  flex: 1;
}

.auth-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.auth-tab {
  padding: 6px 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--t);
}

.auth-tab:hover {
  border-color: var(--accent);
}

.auth-tab.active {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--accent);
}

/* 设置卡片 */
.section-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  margin-bottom: 24px;
  overflow: hidden;
}

.section-card.danger-zone {
  border-color: var(--red);
}

.card-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-2);
}

.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.card-header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--fg-3);
}

.card-body {
  padding: 0;
}

/* 设置行 */
.setting-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-info {
  flex: 1;
  min-width: 0;
}

.setting-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 4px;
}

.setting-hint {
  margin: 0;
  font-size: 12px;
  color: var(--fg-3);
  line-height: 1.4;
}

.setting-control {
  flex-shrink: 0;
  min-width: 200px;
  display: flex;
  align-items: center;
}

/* 表单控件 */
.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  font-family: var(--sans);
  color: var(--fg);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  outline: none;
  transition: all var(--t);
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-bg);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: var(--fg-4);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

/* 输入组 */
.input-group {
  display: flex;
  gap: 8px;
  width: 100%;
}

.input-group .form-input {
  flex: 1;
}

/* 带切换的输入 */
.input-with-toggle {
  position: relative;
  width: 100%;
}

.input-with-toggle .form-input {
  padding-right: 40px;
}

.toggle-visibility {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  padding: 4px;
  background: transparent;
  border: none;
  color: var(--fg-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color var(--t);
}

.toggle-visibility:hover {
  color: var(--fg);
}

/* 选择框 */
.select-wrapper {
  position: relative;
  width: 100%;
}

.form-select {
  appearance: none;
  padding-right: 32px;
  cursor: pointer;
}

.select-arrow {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--fg-3);
  pointer-events: none;
}

/* 主题选择 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  width: 100%;
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  cursor: pointer;
  transition: all var(--t);
}

.theme-option:hover {
  border-color: var(--accent);
}

.theme-option.active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.theme-preview {
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
  flex-shrink: 0;
}

.theme-preview.light {
  background: linear-gradient(135deg, #ffffff 50%, #f3f4f6 50%);
}

.theme-preview.dark {
  background: linear-gradient(135deg, #0c0c0c 50%, #1f1f1f 50%);
}

.theme-preview.retro {
  background: linear-gradient(135deg, #0a1f0a 50%, #1a3a1a 50%);
}

.theme-preview.retro-yellow {
  background: linear-gradient(135deg, #1a1a0a 50%, #2a2a1a 50%);
}

.theme-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-2);
}

.theme-option.active .theme-name {
  color: var(--accent);
  font-weight: 600;
}

/* 范围滑块 */
.range-control {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.form-range {
  flex: 1;
  height: 4px;
  background: var(--border);
  border-radius: 2px;
  outline: none;
  appearance: none;
  cursor: pointer;
}

.form-range::-webkit-slider-thumb {
  appearance: none;
  width: 16px;
  height: 16px;
  background: var(--accent);
  border-radius: 50%;
  cursor: pointer;
  transition: transform var(--t);
}

.form-range::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

.range-value {
  min-width: 40px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  text-align: right;
}

/* 开关 */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--border);
  border-radius: 12px;
  transition: all var(--t);
}

.toggle-slider:before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: all var(--t);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--accent);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(20px);
}

/* 单选按钮组 */
.radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.radio-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  cursor: pointer;
  transition: all var(--t);
}

.radio-option:hover {
  border-color: var(--accent);
}

.radio-option.active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.radio-option input[type="radio"] {
  margin-top: 2px;
  accent-color: var(--accent);
}

.radio-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.radio-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.radio-desc {
  font-size: 11px;
  color: var(--fg-3);
}

.radio-option.active .radio-title {
  color: var(--accent);
}

/* 连接状态 */
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: var(--r);
  font-size: 12px;
  font-weight: 600;
}

.connection-status.ok {
  background: var(--green-bg);
  color: var(--green);
}

.connection-status.error {
  background: var(--red-bg);
  color: var(--red);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--sans);
  border: 1px solid transparent;
  border-radius: var(--r);
  cursor: pointer;
  transition: all var(--t);
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.btn-primary:hover:not(:disabled) {
  background: var(--blue-dark);
  border-color: var(--blue-dark);
}

.btn-secondary {
  background: var(--bg);
  color: var(--fg-2);
  border-color: var(--border);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--bg-2);
  border-color: var(--accent);
  color: var(--accent);
}

.btn-ghost {
  background: transparent;
  color: var(--fg-2);
  border-color: transparent;
}

.btn-ghost:hover:not(:disabled) {
  background: var(--bg-2);
  color: var(--fg);
}

.btn-danger {
  background: var(--red);
  color: white;
  border-color: var(--red);
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
  border-color: #b91c1c;
}

/* 动画 */
.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-page {
    flex-direction: column;
  }

  .settings-nav {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding: 12px 0;
  }

  .nav-items {
    flex-direction: row;
    overflow-x: auto;
    padding: 0 12px;
    gap: 4px;
  }

  .nav-item {
    padding: 8px 12px;
    white-space: nowrap;
  }

  .settings-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .setting-row {
    flex-direction: column;
    gap: 12px;
  }

  .setting-control {
    min-width: 100%;
  }

  .theme-grid {
    grid-template-columns: repeat(4, 1fr);
  }

  .input-group {
    flex-direction: column;
  }
}
</style>