# 工作流系统设计文档

## 1. 概述

将现有的线性目标系统扩展为支持自由设置工作节点的工作流系统。

### 1.1 核心特性
- **混合模式**: LLM生成初始工作流 + 用户手动调整
- **DAG结构**: 支持有向无环图，节点间可有多依赖关系
- **多种节点类型**: 执行、条件、并行、人工审批、子工作流
- **可视化**: 支持查看工作流结构和执行状态

## 2. 数据模型

### 2.1 Workflow（工作流）
```java
public class Workflow {
    private String id;                    // UUID
    private String sessionId;             // 关联的会话ID
    private String workspaceHash;         // 工作区hash
    private String title;                 // 工作流标题
    private String description;           // 详细描述
    private WorkflowStatus status;        // 工作流状态
    private int maxRetries = 3;           // 节点最大重试次数
    
    private List<WorkflowNode> nodes;     // 节点列表
    private List<WorkflowEdge> edges;     // 边列表（依赖关系）
    
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
```

### 2.2 WorkflowNode（工作流节点）
```java
public class WorkflowNode {
    private String id;                    // 节点ID（如 "n1", "n2"）
    private String description;           // 节点描述
    private NodeType type;                // 节点类型
    private NodeStatus status;            // 节点状态
    private int retryCount = 0;           // 已重试次数
    private String lastError;             // 最后一次失败的错误信息
    private String result;                // 执行结果
    private Instant completedAt;          // 完成时间
    
    // 条件节点专用
    private String condition;             // 条件表达式（如 "test.passed == true"）
    
    // 并行节点专用
    private List<String> parallelBranches; // 并行分支的节点ID列表
    
    // 人工审批节点专用
    private String approvalPrompt;        // 审批提示
    private String approvalResult;        // 审批结果（approved/rejected）
}
```

### 2.3 WorkflowEdge（工作流边）
```java
public class WorkflowEdge {
    private String id;                    // 边ID
    private String from;                  // 源节点ID
    private String to;                    // 目标节点ID
    private String condition;             // 条件表达式（可选）
    private EdgeType type;                // 边类型
}
```

### 2.4 枚举类型
```java
public enum NodeType {
    ACTION,       // 执行动作
    CONDITION,    // 条件判断
    PARALLEL,     // 并行执行
    HITL,         // 人工审批
    SUBFLOW,      // 子工作流
    START,        // 开始节点
    END           // 结束节点
}

public enum NodeStatus {
    PENDING,      // 待执行
    READY,        // 就绪（所有依赖已满足）
    RUNNING,      // 执行中
    DONE,         // 已完成
    FAILED,       // 失败
    SKIPPED,      // 跳过
    WAITING,      // 等待中（如等待人工审批）
    BLOCKED       // 被阻塞
}

public enum WorkflowStatus {
    DRAFT,        // 草稿（用户正在编辑）
    ACTIVE,       // 活跃（正在执行）
    PAUSED,       // 暂停
    COMPLETED,    // 已完成
    FAILED        // 失败
}

public enum EdgeType {
    NORMAL,       // 普通依赖
    CONDITION_TRUE,  // 条件为真时
    CONDITION_FALSE, // 条件为假时
    DEFAULT       // 默认路径
}
```

## 3. 命令设计

### 3.1 工作流管理命令
```
/workflow create <描述>              创建新工作流（LLM生成初始结构）
/workflow status                    查看当前工作流状态
/workflow show                      显示工作流结构（节点和依赖）
/workflow pause                     暂停工作流
/workflow resume                    恢复工作流
/workflow clear                     清除当前工作流
```

### 3.2 节点管理命令
```
/workflow node add <描述>           添加新节点
/workflow node remove <节点ID>      删除节点
/workflow node edit <节点ID> <描述>  编辑节点描述
/workflow node type <节点ID> <类型>  设置节点类型
```

### 3.3 依赖管理命令
```
/workflow link <from> <to>          建立依赖关系
/workflow unlink <from> <to>        移除依赖关系
/workflow branch <节点ID> --if <条件> --then <节点ID> --else <节点ID>
                                    添加条件分支
```

### 3.4 执行控制命令
```
/workflow run                       开始执行工作流
/workflow step                      单步执行
/workflow retry <节点ID>            重试某个节点
/workflow skip <节点ID>             跳过某个节点
```

## 4. 执行引擎设计

### 4.1 执行流程
1. 解析工作流DAG结构
2. 找到所有就绪节点（所有依赖已满足）
3. 执行就绪节点
4. 更新节点状态
5. 检查是否有新的就绪节点
6. 重复步骤2-5，直到所有节点完成或失败

### 4.2 节点执行逻辑
- **ACTION节点**: 调用LLM执行动作，等待完成
- **CONDITION节点**: 评估条件表达式，根据结果选择路径
- **PARALLEL节点**: 并行执行所有子节点
- **HITL节点**: 等待用户输入/审批
- **SUBFLOW节点**: 调用子工作流

### 4.3 状态管理
- 每个节点的状态独立管理
- 支持节点重试和跳过
- 支持从失败节点恢复

## 5. LLM集成

### 5.1 工作流生成
- 用户描述目标后，LLM生成初始工作流结构
- 包含节点列表和依赖关系
- 用户可以手动调整

### 5.2 节点执行
- 每个ACTION节点执行时，LLM接收：
  - 节点描述
  - 前置节点的结果
  - 工作流上下文
- LLM执行动作并返回结果

### 5.3 条件评估
- CONDITION节点使用LLM评估条件表达式
- 返回true/false决定执行路径

## 6. 可视化

### 6.1 文本表示
```
工作流: 用户注册流程
状态: ACTIVE (2/5, 40%)

[START] --> [n1: 创建用户表] --> [n2: 实现注册API] --> [n3: 验证邮箱]
                                                      |
                                                      v
                                            [n4: 发送欢迎邮件] --> [END]
```

### 6.2 状态标记
- ⬜ PENDING
- 🟢 READY
- 🔵 RUNNING
- ✅ DONE
- ❌ FAILED
- ⏭️ SKIPPED
- ⏸️ WAITING
- 🔒 BLOCKED

## 7. 存储设计

### 7.1 文件结构
```
workspace/{hash}/workflows/
  {sessionId}.jsonl    # 工作流定义和状态
```

### 7.2 JSON格式
```json
{
  "id": "wf-123",
  "sessionId": "session-456",
  "workspaceHash": "abc123",
  "title": "用户注册流程",
  "description": "实现完整的用户注册功能",
  "status": "ACTIVE",
  "maxRetries": 3,
  "nodes": [
    {
      "id": "n1",
      "description": "创建用户表",
      "type": "ACTION",
      "status": "DONE",
      "retryCount": 0,
      "result": "CREATE TABLE users (id INT PRIMARY KEY, ...)"
    }
  ],
  "edges": [
    {
      "id": "e1",
      "from": "START",
      "to": "n1",
      "type": "NORMAL"
    }
  ],
  "createdAt": 1234567890,
  "updatedAt": 1234567890
}
```

## 8. 与现有系统的兼容性

### 8.1 向后兼容
- 保留现有`/goal`命令作为快捷方式
- `/goal set <描述>` 自动转换为简单线性工作流
- 现有目标数据可自动迁移

### 8.2 渐进式升级
- 第一阶段: 实现基础工作流结构
- 第二阶段: 添加条件和并行支持
- 第三阶段: 添加子工作流和高级特性

## 9. 实现计划

### 9.1 第一阶段（基础）
1. 创建Workflow数据模型
2. 实现WorkflowStore持久化
3. 扩展WorkflowCommand命令
4. 实现基础执行引擎

### 9.2 第二阶段（增强）
1. 添加条件节点支持
2. 添加并行节点支持
3. 实现工作流可视化
4. 添加节点管理命令

### 9.3 第三阶段（高级）
1. 添加子工作流支持
2. 实现工作流模板
3. 添加工作流版本管理
4. 优化性能和错误处理