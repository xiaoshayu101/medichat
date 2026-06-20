# MediChat · 医疗机构智慧管理系统

> 互联网+大赛校级参赛项目 · Java 后端

## 项目背景

针对某医疗机构面临多门店统一排班管理困难、候诊体验差、医生接诊效率低、患者爽约管理缺失等问题。本项目通过引入智能排班引擎、实时叫号系统、AI 预问诊、预约信用分四个核心模块，实现了从"号源管理"到"智能问诊"再到"就诊行为约束"的全流程数字化改造，面向医疗机构多门店接入统一管理场景设计。

## 技术栈

| 分类 | 技术 |
|------|------|
| 核心框架 | Spring Boot 3.3.4 · MyBatis-Plus |
| 数据存储 | MySQL · Redis · Elasticsearch |
| 消息队列 | Kafka |
| AI 框架 | LangChain4j |
| 定时调度 | XXL-Job · Spring Task |
| 限流熔断 | Sentinel |
| 实时通信 | Spring WebSocket |
| 认证鉴权 | Sa-Token |
| 搜索引擎 | Elasticsearch（全文检索 + 向量检索） |

## 团队分工

5人团队（1前端 + 4后端），本人负责以下模块：

- 排班引擎（核心模块）
- 实时叫号系统（核心模块）
- AI 预问诊 · RAG 增强
- AI 预问诊 · 长短期记忆

---

## 模块一：排班引擎

### 设计思路

采用规则 + 例外两表分离的排班模型：

- schedule_rule：医生的长期出诊规则（按周/双周配置，支持指定星期和时段）
- schedule_exception：临时调班记录，支持 CANCEL（整天取消）、HALF_DAY（改成半天）、ADD_SLOT（加号）三种类型，覆盖基础规则

XXL-Job 每日凌晨 01:00 读取规则与例外合并计算，预生成未来 7 天号源写入 MySQL，同步至 Redis。

### 核心设计亮点


#### 唯一约束的重新设计

最初将例外表的唯一约束设计为 (doctor_id, exception_date)，隐含"一天只有一种例外"的假设。实际业务中，医生可能同一天既改成半天出诊（HALF_DAY）又临时加号（ADD_SLOT），这是两个独立变更。将约束改为 (doctor_id, exception_date, type)，允许同一天不同类型的例外共存，代码层面从"查单条例外"改为"查所有例外并按类型分别取出处理"。

#### 差值调整保护已有预约

号源生成任务每天滚动执行，同一天的号源会被处理多次（第一次新建，后续发现已存在）。已存在时不简单跳过，而是比较 totalCount 是否变化——变化则用差值调整 remainCount，保护已被患者预约扣减的余量不被重置。

#### 批处理与实时操作的职责边界

CANCEL / HALF_DAY 在号源已生成且已有预约后才插入的情况，不在批处理任务范围内处理，而是交给专门的"医生临时调整出诊"接口触发——因为这涉及已有预约患者的通知与改约，不是简单调整数字，体现了单一职责原则。

#### Lua 脚本原子加号

加号操作通过 Lua 脚本原子写入 Redis 号源计数：GET 当前余量 → 判断加号后是否超过原始号源的 20% → INCRBY，避免并发加号的竞态条件。同步插入 schedule_exception 留审计，超出上限由 Sentinel 拦截。

---

## 模块二：实时叫号系统

### 设计思路

Redis ZSet 维护每个医生的候诊队列（score = 取号序号，member = appointmentId），医生点击叫号时触发 WebSocket 广播大屏消息 + 推送患者个人消息；患者 WebSocket 不可达时降级为 Kafka 异步微信服务通知兜底。

### 核心设计亮点

#### 候诊在场人数的判断维度

候诊等待时间推送需要知道"前面还有几个人"，最初考虑用 WebSocket 在线状态判断，但患者锁屏或切后台会断开连接，人还在候诊区但连接已断。修正为用 appointment.status = CHECKED_IN 这个业务状态判断实际在场人数，与 WebSocket 连接状态解耦。

#### 就诊时长预测模型

等待时间推送不使用全局平均就诊时长，而是按医生 × 星期 × 时段维度建模：

- Kafka 采集每次就诊时长事件，Consumer 按 duration:raw:{doctorId}:{weekday}:{period} 存入 Redis List
- XXL-Job 每周统计，计算本周平均值，与历史值按 0.3 / 0.7 加权融合，写入 Redis Hash（duration:model:{doctorId}）
- Spring Task 每 2 分钟读取模型，结合队列实际在场人数动态计算等待时间，WebSocket 推送给候诊患者

#### Redis keys 命令替换为 scan

周统计任务扫描所有 duration:raw:* 的 key，本项目同一 Redis 实例上同时承载排班号源、叫号队列等多类业务数据，使用 keys 命令会扫描全实例 key 空间并阻塞 Redis。改用游标式 scan 命令，每次只扫一小批，不长时间独占 Redis。

#### WebSocket 三角色连接管理

@ServerEndpoint 标注的类每个连接会创建新实例，用 static ConcurrentHashMap 分别管理 PATIENT / SCREEN / DOCTOR 三种角色的连接，线程安全地支持按角色精确推送。

---

## 模块三：AI 预问诊

### 设计思路

患者候诊期间在小程序完成 LangChain4j 驱动的多轮预问诊；ES 向量检索历史病情摘要与 Redis 短期记忆共同注入 Prompt；被叫号瞬间将结构化摘要推送至医生工作台；Sentinel 配置慢调用熔断防止大模型超时拖垮线程池。

### 核心设计亮点

#### 短期记忆：摘要压缩而非滑动窗口

每次问诊的对话历史存于 Redis List（chat:memory:{patientId}:{sessionId}）。对话达到 20 轮时触发摘要压缩——将前 20 轮发给 LLM 生成 100 字以内的病情摘要，替换原有记录，以 role=system 消息形式保留作为后续对话背景。

滑动窗口的问题在于会丢弃早期关键信息（如第 2 轮提到的药物过敏史在第 30 轮后消失）；摘要压缩能将所有关键病情信息浓缩保留，不受对话长度影响。

#### 长期记忆：MySQL + ES 双写

每次问诊结束后，结构化摘要通过 Kafka 异步写入：

- MySQL patient_summary 表存储摘要原文
- Elasticsearch medical-summaries 索引存储对应向量（通义千问 text-embedding-v2 模型生成）

检索时用患者当前症状描述生成查询向量，KNN 相似度检索召回 Top3 历史摘要（相似度阈值 0.7），检索时强制加 patientId 过滤，避免召回其他患者数据造成隐私泄露。

#### 健康画像合并

XXL-Job 每周检查，对历史摘要超过 20 条的患者触发二次压缩，将所有历史摘要合并为一份结构化健康画像（基本健康状况 / 慢性病史 / 用药过敏史 / 近期主要症状），同步更新 MySQL 和 ES，提升后续检索的信息密度。

#### Sentinel 慢调用熔断

大模型接口的失败模式是"响应很慢但不抛异常"，用异常比例熔断无法覆盖这种情况。配置慢调用比例熔断：响应 > 5s 算慢调用，10s 统计窗口内慢调用比例超 50% 触发熔断（最少 5 次请求才开始统计），熔断 30 秒后自动恢复，降级返回"服务繁忙请稍后重试"。

#### 叫号瞬间摘要推送

叫号接口执行成功后，额外触发 finishConsult 生成结构化摘要，通过 WebSocket 推送至医生工作台（role=doctor 连接）。推送逻辑用 try-catch 包裹，失败不影响叫号主流程——叫号是核心业务，摘要推送是体验优化，两者不应互相阻塞。

---

## 本地运行说明

### 前置依赖

| 服务 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 推荐 Microsoft OpenJDK 21 |
| MySQL | 8.x | 建库 medichat，执行 schema.sql |
| Redis | 7.x | 默认 6379 端口 |
| Kafka | 4.x | KRaft 模式，默认 9092 端口 |
| Elasticsearch | 8.13.x | 关闭安全认证，默认 9200 端口 |
| XXL-Job Admin | 2.4.1 | 建库 xxl_job，单独启动 |

### 配置说明

复制 application.yaml，替换以下占位符：

```yaml
spring:
  datasource:
    password: 请替换为真实MySQL密码

dashscope:
  api-key: 请替换为阿里云百炼API Key

xxl:
  job:
    accessToken: 请替换为XXL-Job访问令牌
```

### 启动顺序

1. 启动 MySQL / Redis / Kafka / Elasticsearch
2. 启动 XXL-Job Admin（端口 8088）
3. 启动 MedichatApplication（端口 8080）
4. 登录 XXL-Job 控制台注册执行器，配置三个定时任务

---

## 项目说明

本项目为参赛性质，未上线（因为医疗系统涉及等保认证，我们学生团队无法完成完整备案流程）。敏感配置已脱敏，请参考上方说明自行替换。
