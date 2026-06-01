# Skill 系统测试

## 测试文件

| 测试类                | 测试内容                     | 测试数量 |
|--------------------|--------------------------|------|
| `SkillStoreV2Test` | SkillStoreV2 的加载、解析、索引生成 | 10   |
| `SkillV2ImplTest`  | SkillV2Impl 的属性、方法、相等性   | 13   |

## 运行测试

```bash
# 运行 skill 测试
mvn test -pl agent4j-bin -Dtest="SkillStoreV2Test,SkillV2ImplTest"

# 运行所有测试
mvn test -pl agent4j-bin
```

## 测试覆盖

### SkillStoreV2Test

- ✅ 空存储的初始化
- ✅ 读取不存在的 skill
- ✅ 无效的 skill 名称
- ✅ 从文件加载 skill
- ✅ 从目录加载 skill
- ✅ 列出多个 skill
- ✅ 项目级覆盖全局级
- ✅ 生成 skill 索引
- ✅ 多个根目录支持

### SkillV2ImplTest

- ✅ 基本属性
- ✅ null 处理
- ✅ RunAs 类型
- ✅ 生成索引行
- ✅ 生成完整内容
- ✅ 参数处理
- ✅ 相等性和 hashCode
- ✅ toString

## 测试数据

测试使用 `@TempDir` 创建临时目录，不会影响实际的 skill 目录。

## 注意事项

1. 测试不依赖外部文件系统
2. 每个测试用例独立运行
3. 使用 JUnit 5 注解