# AI 短剧制作系统

面向小团队的短剧生产工作台，覆盖剧本、角色与场景资产、分镜、镜头生成、人工选片、配音字幕、时间轴和成片导出。业务数据按工作空间隔离，后续可扩展为 SaaS。

后端采用 Java 25、Spring Boot 4.1.1、Maven 业务多模块、MyBatis-Plus 3.5.17、PostgreSQL 18、Flyway 和 Spring Security。接口文档采用 springdoc 3.1.1 / Swagger UI。

当前已实现后端底座：Session 登录、工作空间权限、项目管理、镜头与不可变修订、媒体上传与授权读取、租户 SQL 拦截、统一错误和请求/SQL 日志。媒体通过独立存储接口持久化到本地目录，应用与迁移使用不同数据库角色。

```bash
./scripts/dev-init.sh
./scripts/backend.sh up
```

打开 [Swagger UI](http://127.0.0.1:18080/swagger-ui/index.html)。初始管理员凭据在本机生成的 `.env`；先获取 CSRF，再登录，登录后刷新 CSRF。Docker 全容器启动、本地运行、数据目录和停止命令见 [运行说明](docs/running.md)。

前端已提供 Vue 3 创作工作台：项目与案例首页、分集剧本编辑、角色、分镜板、编排预览、素材库与独立灵感画布。项目、镜头与媒体对接真实后端；剧集、剧本、角色、编排和画布目前保存于本机浏览器。智能体尚未连接模型，成片渲染尚未实现。

```bash
./scripts/frontend.sh install
./scripts/frontend.sh dev
```

打开 [映序工作室](http://127.0.0.1:15173/)。默认代理后端 `127.0.0.1:18080`，账号使用根目录 `.env` 的初始管理员。无需登录即可打开首页案例体验编辑器。详见 [前端运行与实现说明](docs/frontend.md)。

`backend/` 包含 12 个普通 Maven 业务与组装模块。`kernel`、`workspace`、`content`、`media`、`boot` 已有实现；其他模块保留依赖边界。模型调用、任务调度、费用账本、云存储适配、剧集/剧本等服务端持久化及成片渲染属于后续阶段。

设计文档描述完整目标架构，当前可用能力与启动步骤以运行说明为准。

- [后端运行说明](docs/running.md)
- [前端运行与实现说明](docs/frontend.md)
- [总体架构与模块边界](docs/architecture.md)
- [接口、时区规范与前后端契约](docs/contracts.md)
- [Java 工程规范：日志、SQL、MyBatis-Plus 与运行保障](docs/java-engineering.md)
- [三角色分工与验收计划](docs/implementation-plan.md)
- [模块依赖清单](docs/module-dependencies.json)
