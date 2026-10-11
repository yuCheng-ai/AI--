# 模块接口与前后端契约

本文件约定首版接口边界与关键行为。字段示例用于冻结设计；实际 OpenAPI 从 Java DTO 生成并纳入版本管理，P0 完成依赖与契约验证。

## 接口与时间规范

项目制作与智能画布共用这套规范；画布尚未实现，不另建一套日期或错误协议。当前可调用接口以运行中的 Swagger 为准，后文生成、费用、事件及时间轴内容属于目标契约。

| 项目 | 统一约定 | 当前状态 |
|---|---|---|
| 路径与字段 | `/api/v1`；租户业务路径带 `workspaces/{workspaceId}`；JSON 字段 lowerCamelCase | 已实现 |
| 编码 | JSON 使用 UTF-8；上传为 multipart，下载按实际媒体类型返回 | 已实现 |
| 资源 ID | UUID 字符串；禁止使用文件路径作为业务 ID | 已实现 |
| 修订号 | revision、expectedRevision 对外按十进制正整数字符串处理；服务端范围为正 Java long | Swagger 声明字符串；入参兼容已有 JSON 整数，前端统一原样回传字符串 |
| 成功响应 | 直接返回业务 DTO；创建 201、普通读取/修改 200、无响应体操作 204 | 已实现；未来异步接纳使用 202 |
| 失败响应 | 正确 HTTP 状态加 `{code,message,traceId,fieldErrors}`；客户端按 code 分支，message 用于展示 | 已实现；不把错误伪装成 HTTP 200 |
| 分页 | 工作空间、项目和镜头使用 `{items,nextCursor}`；默认 30、最大 100；游标原样传回 | 已实现；媒体当前是有界最近列表，默认 50、最大 100，尚不提供历史分页 |
| 并发更新 | 原样提交 expectedRevision，冲突返回 409，重读后由用户决定是否修改 | 已实现；当前错误体不附最新修订内容 |
| 认证和权限 | Session、写请求 CSRF、每次校验当前成员权限；workspaceId 路径本身不是权限证明 | 已实现 |
| 金额 | 十进制字符串与币种；Java BigDecimal、SQL NUMERIC | 费用业务待实现 |
| 创建请求幂等 | 生成、导出等有成本操作使用 Idempotency-Key 并持久化去重 | 待实现；当前 CRUD 不保证重复 POST 去重 |

### 时间与时区

1. **发生时刻**：createdAt、updatedAt、完成时间等使用 Java `Instant`、数据库 `TIMESTAMPTZ`，接口输出 ISO 8601/RFC 3339 UTC 字符串，例如 `2026-10-11T12:30:00.123456Z`。不返回无偏移的 `2026-10-11 20:30:00`。
2. **运行环境**：项目提供的本地启动脚本和 Docker 入口为 JVM 显式指定 UTC；PostgreSQL 服务和每条 Hikari 新连接显式设置 UTC。Jackson 默认时区也设为 UTC，但该配置不替代正确的 Java 时间类型。
3. **时间来源和精度**：应用生成时间使用 `Instant.now(clock)`；统一注入的 UTC Clock 已在底座截到微秒，业务不再重复截断，保持创建响应与后续查询一致。数据库默认时间使用 CURRENT_TIMESTAMP。接口小数秒长度可以变化，不强制为 3 位；分页游标不能由前端解析或重新格式化时间来重建。
4. **展示时区**：前端默认使用 IANA 名称 `Asia/Shanghai` 展示，可在后续支持用户时区。`2026-10-11T12:30:00Z` 对应上海 `2026-10-11 20:30:00`。前端目前未实现，展示转换属于前端契约。不要使用含义不明确的 `CST`，也不要手工把字符串加 8 小时后再解析。
5. **纯日期**：生日、业务日等使用 LocalDate、SQL DATE 和 `yyyy-MM-dd`；它们不是某个 UTC 午夜，不做时区平移。底座已统一支持此类型的输入输出，当前尚无此类业务接口。
6. **输入时间**：JSON 与 URL 参数共用严格解析规则。发生时刻使用 `yyyy-MM-dd'T'HH:mm:ss` 加 `Z` 或显式偏移（如 `+08:00`），可带 1～6 位小数秒，解析成同一个 Instant；URL 中的 `+` 应编码为 `%2B`。无偏移、空字符串、数字时间戳、非法日期及超过微秒精度的输入返回 400，不猜测用户时区。缺少字段和 JSON null 仍由 `@NotNull` 等业务校验决定是否允许。当前公开业务创建/修改接口不接收 createdAt/updatedAt，也没有排期输入接口。未来本地排期还需保存 IANA 时区，并明确夏令时的不存在/重复时刻处理。
7. **按日统计**：后续日报、费用统计按明确业务时区计算 `[当天起点,次日起点)` 后转换为 UTC 查询；不依赖服务器默认日期，也不假定每个时区的一天始终是 24 小时。
8. **时长**：时长不是日期时间。现有镜头使用整数 durationSeconds；未来媒体精确时长、任务耗时用明确的 `*Ms` 字段，时间轴使用帧数及 fps，不混用单位。
9. **日志**：文本日志打印完整 UTC 日期时间和 `Z`，生产 ECS 日志保留标准时间字段；请求耗时通过单调时钟计算。不同服务器时钟同步由部署环境负责。

PostgreSQL 的 TIMESTAMPTZ 保存的是时刻，不保留原始时区名称；精度为微秒。因此用户时区或排期时区需要单独保存，不能从该列恢复。[PostgreSQL 日期时间类型说明](https://www.postgresql.org/docs/18/datatype-datetime.html)

### 新接口如何复用

- DTO 中的发生时刻直接声明为 `Instant`，纯日期声明为 `LocalDate`；请求参数也直接使用这两种类型，不逐字段添加 `@JsonFormat`、`@DateTimeFormat` 或手写格式转换。不要用 String、Date 或 LocalDateTime 表示发生时刻。
- `ApiTimeConfiguration` 注册全局 Jackson 模块处理 JSON（含嵌套 DTO 和集合），并注册 Spring MVC Converter 处理查询参数、路径参数及表单绑定；两者复用 `ApiTimeFormats`。错误沿用统一异常处理器，业务 Controller 不需要捕获解析异常。
- `TimeConfiguration` 负责 UTC Clock 和应用生成时间的精度；应用服务通过构造器注入 Clock。数据库连接、运行环境和日志继续使用 UTC。
- 采用框架序列化与参数转换扩展点，不通过 AOP 遍历并改写返回对象。该规范针对 HTTP DTO 和参数，不改写媒体下载内容或业务字符串。框架依据：[Spring Boot JSON 扩展](https://docs.spring.io/spring-boot/reference/features/json.html)、[Spring MVC 类型转换](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/conversion.html)。

格式和传输时区由底座处理；业务仍需定义“自然日”的所属时区及排期规则。前端按用户时区展示，后端接口不预先转换成北京时间字符串。

## 模块公开接口

每个模块暴露少量面向用例的接口和不可变 DTO。Controller 只处理 HTTP 转换、认证上下文和参数校验；规则与事务由应用服务维护。

| 模块 | 公开接口 | 核心操作及约束 |
|---|---|---|
| workspace | AuthorizationQueries、WorkspaceQueries | 校验工作空间成员、项目访问范围及命令权限；后台调用携带明确的执行来源 |
| content | ContentCommands、ContentQueries | 读取固定内容修订、保存剧本和镜头修订、确认内容、附加候选、人工选片；更新带 expectedRevision |
| assets | AssetCommands、AssetQueries | 创建资产、读取定稿版本、附加生成候选、确认资产修订；资产属于工作空间 |
| media | MediaCommands、MediaQueries | 创建上传、完成校验、取得固定媒体版本、创建及释放引用保留、取得短时访问授权 |
| models | ModelCatalog、ModelGateway | 读取本地能力及价格配置快照；在事务外提交、查单和请求取消供应商任务 |
| jobs | JobScheduler、JobQueries、DomainEventPublisher | 同事务入队、调度查询、取消尚未开始的任务、记录并安排领域事件投递 |
| generation | GenerationCommands、GenerationQueries | 创建固定输入请求、查询、记录取消意图、归并供应商状态与产物 |
| editing | EditingCommands、EditingQueries | 保存时间轴修订、校验渲染清单、创建审片或导出记录 |
| usage | BudgetCommands、UsageQueries | reserve、settle、release、adjust；基于 operationId 和外部账单事实幂等记账 |
| workbench | 按用例提供应用服务 | GenerateShot、GenerateAsset、GenerateScript、SelectCandidate、SubmitExport；组合公开接口，不访问其他模块 SQL |

公开 DTO 不携带 Mapper、数据库 Entity、供应商 SDK 类型或任意 Bean 名。ID 可采用强类型 Java 值对象；HTTP 层全部转换为字符串。生成输入按文本、图像、视频、语音等能力建立明确类型，供应商特有参数放版本化且经过校验的扩展结构。

内部模块调用携带 `ExecutionContext(workspaceId, actorId, source, correlationId)`。它描述已验证身份与来源，各领域模块仍校验资源归属和领域条件；不能把任意构造的上下文当作权限证明。后台服务身份使用限定能力，不冒充用户。

## 生成用例与数据归属

以镜头视频生成为例，workbench 执行以下操作：

1. 验证用户拥有当前工作空间及项目的生成权限。
2. 读取镜头的固定修订、资产定稿版本和可用媒体版本；能力和报价读取本地配置快照。
3. 在同一短事务内完成命令去重、预算预占、生成请求、输入快照、生成目标绑定、媒体引用保留和首条任务。
4. 事务提交后返回 operationId，模型 Worker 后续领取提交任务。
5. generation 归并供应商回执；文本结果经过结构校验，媒体结果经过转存和探测后形成可用产物。
6. generation 发布 GenerationReady。workbench 按原目标绑定，通过 content 或 assets 公开命令附加候选。
7. 用户确认候选；相应领域模块保存选中关系或创建新的内容修订。

`workbench.generation_binding` 至少保存：workspaceId、requestId、targetType、targetId、targetRevision、purpose、inputHash。目标可以是剧本、镜头或资产。generation 只认识输入、产物和请求，不识别镜头或角色业务规则。

文本产物保存经版本化 JSON Schema 校验的结构结果及原始响应引用；失败的解析不直接写入正式剧本。用户采纳文本候选时，以当前 expectedRevision 创建新内容修订。图像、视频和声音产物引用不可变 mediaVersionId。

候选绑定唯一键包含目标修订、requestId、outputId 和 purpose。迟到结果保留原归属；目标已归档时记录已归档绑定并结束事件处理，不重建目标，也不无限重试。候选附加不会自动变更选片。

媒体引用保留由 media 拥有。生成输入、生成产物、已选候选和时间轴分别建立带 ownerType/ownerId 的保留记录。建立引用与垃圾回收在 media 内串行校验状态，防止“检查可用后文件被回收”的竞态；释放采用幂等命令，实际删除经过延迟窗口。

mediaVersionId 表示不可变内容版本，底层 storageBackendId/bucket/objectKey 属于 media 的物理位置记录。搬迁到另一个存储实例只增加并验证物理位置，不改变内容版本；已创建上传会话固定原后端。前端按服务器返回的上传计划执行 URL、方法与请求头，不自行拼接供应商域名。临时访问授权每次根据当前已验证位置生成，详见 [云厂商替换与素材迁移](architecture.md#云厂商替换与素材迁移)。

## 公共 HTTP 约定

- 路径统一以 `/api/v1/workspaces/{workspaceId}` 开头；登录和供应商回调使用专用路径。
- 同源 HttpOnly Cookie 会话，写请求校验 CSRF。前端隐藏按钮只用于体验，后端执行权限判定。
- ID 为字符串；金额为十进制字符串与 currency；日期时间为 UTC ISO 8601；媒体时长字段明确帧或毫秒单位。
- 编辑携带 expectedRevision；发生版本冲突统一返回 409。当前客户端需重新读取当前 revision，并保留脏草稿；后续可增加结构化 conflict 信息。
- 创建付费请求、导出和上传会话等有副作用的创建命令携带 Idempotency-Key。相同键与相同请求摘要返回原操作；相同键不同内容返回 409。
- 工作台组合命令的去重记录归 workbench，直接模块创建命令由该模块拥有去重记录。唯一作用域包含工作空间和命令类型；鉴权先于去重结果返回，原始操作者被记录用于审计。
- 去重记录与操作使用相同事务。响应丢失仍可通过重试查询原 operationId；已过热数据保留期的付费请求保留去重墓碑，避免旧键重新触发付费。
- 分页使用游标和稳定排序。错误包含 code、message、traceId、fieldErrors 和可选 conflict；禁止把供应商密钥、原始异常堆栈返回浏览器。

| 方法与路径后缀 | 行为 |
|---|---|
| `GET /projects/{projectId}/episodes/{episodeId}/editor` | 返回编辑所需概要及修订；媒体和历史候选按需加载 |
| `PATCH /shots/{shotId}` | 保存镜头新修订 |
| `POST /shots/{shotId}/generations` | 提交固定修订生成，返回 202 |
| `POST /assets/{assetId}/generations` | 提交资产候选生成，返回 202 |
| `POST /episodes/{episodeId}/script-generations` | 提交结构化剧本草案生成，返回 202 |
| `POST /generation-batches` | 显式选择镜头及修订，预校验并预占整批预算；原子接纳后各子请求独立执行 |
| `GET /generations/{operationId}` | 返回请求、阶段、费用状态、输出及 allowedActions |
| `POST /generations/{operationId}/cancel` | 保存取消意图；是否可取消以供应商能力及当前状态为准 |
| `POST /shots/{shotId}/selection` | 选择候选，携带 expectedRevision；不修改生成产物 |
| `POST /uploads` | 创建上传会话，返回服务端指定对象标识及上传约定 |
| `POST /uploads/{uploadId}/parts` | 为已授权的分片签名 |
| `POST /uploads/{uploadId}/complete` | 校验上传归属及对象完成情况，进入媒体处理 |
| `GET /media/{mediaId}` | 返回元数据、处理状态和可用版本 |
| `POST /media/{mediaId}/access` | 返回授权范围内的短时预览或下载地址 |
| `PUT /timelines/{timelineId}` | 带 expectedRevision 保存新的不可变时间轴修订 |
| `POST /timelines/{timelineId}/renders` | 指定修订与 preview/final 模式，返回 202 |
| `GET /renders/{operationId}` | 查询渲染状态及输出 |
| `GET /activity` | 获取工作空间任务及活动快照 |
| `GET /events` | SSE 资源变化通知及游标重放 |

首版批次设定最大镜头数，避免无限大的接纳事务。usage.reserveBatch(batchId, childReservations) 一次校验总额度，创建按子 operationId 归属的预占，不另叠加父批次预占。整批接纳不代表所有子请求均成功；每个子请求独立结算或释放。取消批次只是逐一登记取消意图，不能释放已提交或结果未知子请求的预占。文本失败修复或重新生成也产生有预算的独立请求，不无限自动调用模型。

镜头生成请求示例：

```json
{
  "expectedRevision": "12",
  "purpose": "SHOT_VIDEO",
  "modelId": "video-model-default",
  "modelConfigRevision": "3",
  "parameters": { "durationSeconds": 5, "aspectRatio": "9:16" }
}
```

客户端提交对象引用与生成意图；服务端解析并保存实际输入快照。客户端不能直接指定供应商账号密钥、报价金额、存储对象路径或所属工作空间。

202 响应示例：

```json
{
  "operationId": "gen_01_example",
  "status": "QUEUED",
  "inputRevision": "12",
  "cost": { "reservedAmount": "2.50", "currency": "CNY", "basis": "ESTIMATE" },
  "allowedActions": ["CANCEL"]
}
```

该金额仅演示数据格式，不是任何模型的实际报价。价格版本和估算性质由服务器确定。

## 状态、恢复与计费契约

生成请求状态、技术 job 状态、供应商状态、输出处理状态、取消状态和费用状态分别保存，通过服务端投影给前端。前端不把这些字段合成为自己的业务状态机。

| 情况 | 服务器处理 | 前端行为 |
|---|---|---|
| 提交 HTTP 响应丢失 | 同键返回已有请求 | 复用 Idempotency-Key 核对，不显示已失败 |
| 调用供应商超时，不知是否接单 | SUBMISSION_UNKNOWN，按稳定请求号查单；无法确认时 NEEDS_ATTENTION | 显示“正在核对”，以 allowedActions 决定按钮 |
| 收到供应商作业编号 | 保存收据并继续查询；轮询释放线程 | 显示生成阶段，未提供进度时不显示百分比 |
| 供应商完成但文件未入库 | INGESTING，仅重试转存及校验 | 显示“正在保存素材” |
| 回调重复、乱序或晚到 | 收件箱去重和单一状态归并器；不回退终态 | 刷新对应资源快照 |
| 用户已改镜头，旧任务完成 | 附加到旧修订候选 | 标明输入版本，可比较或明确采纳 |
| 用户取消，供应商仍完成 | 归档结果并核对费用，停止可阻止的付费下游步骤 | 分别显示取消处理结果与费用状态 |
| Worker 失联后恢复 | 过期租约重新领取；供应商提交先查单；所有回写校验执行资格 | 查询同一 operationId |

`usage.reserve(operationId, quoteSnapshot)` 用条件更新或行锁原子预占，避免并发透支。结算依据已确认供应商用量或账单标识；结算幂等键包含供应商账号和收费事实标识。无法获得外部唯一标识时，按本地 providerSubmissionId 和结算修订控制。

供应商已收费而素材转存失败，仍然记录实际费用。实际费用超过预占时允许记入真实差额，同时触发额度不足提示并阻止后续不符合预算的提交；不能为了保持预算数字好看而拒绝记账。取消与失败的退费通过追加调整记录体现。

提交意图在发起 HTTP 前持久化。供应商幂等键和查单能力在 models 的能力表中明确声明。提交重试、切换供应商和自动修复策略必须服从这些能力；未知提交不得自动生成第二份付费任务。

## 任务 SPI 与事件

jobs.spi 的最小语义如下，实际 Java 签名在 P0 冻结：

```text
JobHandler:
  type() -> 稳定任务类型
  payloadVersion() -> 支持的 payload 版本
  execute(JobExecution) -> 完成 / 指定时间重调度 / 需人工处理

JobExecution:
  jobId、operationId、workspaceId、attempt、leaseToken、correlationId
  取消与执行资格查询、续租能力、经校验的 payload

DomainEventHandler:
  consumerKey() -> 稳定消费者名称
  eventType()、schemaVersion()
  handle(event) -> 仅短事务数据库操作或入队
```

jobs 不定义供应商状态和业务补偿。每个 handler 决定哪些错误能安全重试；jobs 执行有限失败重试、退避和到期领取。execution_count 与 failure_count 分别计数，供应商正常 RUNNING 轮询只重调度，不消耗失败重试额度。业务等待超期进入核对状态，不能自动判失败或释放预算。付费提交不得因“网络错误可重试”的通用分类而再次发起。

完成业务状态、创建下一步 job 和记录事件须在同一短事务内校验当前租约 token 并提交。不能先把 job 标记成功再更新业务，或者只给 job 表加 token 条件却允许旧 Worker 无条件写业务表。旧 Worker 获得的外部收据只能进入可幂等核对的收件箱，由当前状态归并入口处理。

关键事件初始订阅表：

| 事件 | 最初消费者 | 行为 |
|---|---|---|
| generation.GenerationReady.v1 | workbench.attach-generation-result | 根据固定 binding 附加文本或媒体候选，并写活动投影 |
| generation.GenerationFailed.v1 | workbench.record-generation-failure | 更新任务读投影和活动 |
| media.MediaReady.v1 | generation.accept-ready-media | 按本模块产物映射更新状态，全部必需产物就绪后幂等转换 READY 并发布 GenerationReady |
| media.MediaReady.v1 | workbench.record-media-ready | 更新工作台活动，提示查询 |
| editing.RenderReady.v1 | workbench.record-render-ready | 更新导出活动 |
| content.ContentRevisionChanged.v1 | workbench.record-content-change | 刷新内容投影和复核提示；不触发付费生成 |
| assets.AssetRevisionChanged.v1 | workbench.record-asset-change | 刷新资产投影并标记关联内容需要复核，不重写原输入 |

事件 outbox 与消费者任务同时落地。消费收据、附加候选、活动投影及投递任务完成在同一事务提交。业务上的候选唯一约束仍保留，防止人工补投或历史迁移绕开消费去重。

GenerationReady 可能产生多个 output，事件引用 requestId 和产物 ID 集合，消费者通过 generation 公开查询读取固定结果。事件包含 workspaceId、eventId、schemaVersion 和 correlationId，不携带大媒体内容。

## SSE 与草稿同步

SSE 使用同源 Cookie 会话。原生 EventSource 的重连可携带 Last-Event-ID；首次恢复页面可显式提供不含凭证的游标。每条活动分配工作空间内有提交顺序保证的 cursor，并保存资源版本。

```text
id: 1842
event: resource.changed
data: {"schemaVersion":1,"workspaceId":"ws_demo","resourceType":"generation","resourceId":"gen_01_example","resourceRevision":"8"}
```

服务端重连先核对当前权限；成员被移除时中断会话或连接，不能让已建立的连接持续泄漏新活动。游标超出保留窗口时发送 resync.required，客户端重建任务和当前对象快照。

为避免快照与订阅之间漏消息，客户端先取得起始 cursor 并建立可重放订阅，再读取权威资源快照；期间通知使对应查询失效，资源 revision 防止旧响应覆盖新响应。SSE 仅触发查询，不直接覆盖编辑器草稿。

同一对象的自动保存串行执行，未发送改动可以合并。返回 revision 只推进对应已提交基线；在途保存期间的新输入继续保留为脏草稿。409 提供当前版本，用户可比较或重新应用，不能自动覆盖冲突。

## 时间轴与渲染格式

时间轴 schemaVersion 与业务 revision 分开。首版固定 30 fps，轨道位置和视频裁剪使用整数帧，所有片段引用固定 mediaVersionId。

```json
{
  "schemaVersion": 1,
  "expectedRevision": "4",
  "canvas": { "width": 1080, "height": 1920 },
  "fps": { "numerator": 30, "denominator": 1 },
  "videoClips": [
    { "id": "clip_a", "mediaVersionId": "mv_a", "startFrame": 0, "sourceInFrame": 30, "durationFrames": 90 }
  ],
  "audioClips": [
    { "id": "audio_a", "mediaVersionId": "mv_voice", "startFrame": 0, "sourceInFrame": 0, "durationFrames": 90, "gainDb": 0 }
  ],
  "captions": [
    { "id": "caption_a", "startFrame": 0, "durationFrames": 90, "text": "示例台词", "styleId": "default-v1" }
  ]
}
```

视频区间使用半开范围 `[startFrame, startFrame + durationFrames)`；首版单视频轨连续无重叠，排序与裁剪后重新计算起始帧。服务端校验素材可用、裁剪不越界、画幅匹配策略、轨道数量、总时长和音量范围。音频统一 48 kHz，30 fps 时一帧对应 1600 个采样，输出尾部编码填充按媒体验证规则处理。

渲染清单额外冻结媒体校验值、代理规范、字体文件版本、字幕布局坐标空间、画幅适配策略、音频混合规则、编码参数和渲染器版本。低清审片复用同一逻辑画布、裁剪与字幕布局，再缩放输出，避免缩小分辨率改变换行或位置。

服务端渲染与浏览器即时预览使用同一份描述；正式审看使用服务端低清文件。任务运行中修改时间轴会产生新修订，不能改变既有导出清单。

FFmpeg 使用受控参数数组和固定输入清单，字体与字幕文件由服务端生成。远程资源先经 media 受限下载并转为本地文件；渲染进程不接受任意用户 URL、路径或表达式。

## 契约变更纪律

OpenAPI、事件 schema、job payload 和时间轴 schema 随代码审查。HTTP 增加可选字段可兼容演进；删除字段、改变含义和新增不能被旧 Worker 读取的枚举必须先部署兼容读取。

运行期间数据库中存在旧 payload 和事件，升级不能只验证空库启动。P0 建立 schemaVersion 检查与明确的不支持版本错误；后续发布使用停领新任务、等待或回收租约、兼容迁移和同版本角色部署流程。客户端未知状态显示可核对的中性状态并刷新，不能自行按成功解释。
