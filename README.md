# RIDO

统一管理任务、日程与实际记录的 Android 时间管理应用。RIDO 将计划与已经发生的活动分开保存，并通过时间轴和统计帮助回顾一天。

## 核心功能

| 模块 | 当前能力 |
| --- | --- |
| Today | 查看实际时间轴、活动记录与睡眠边界，进行日常记录。 |
| Task | 管理任务及子任务，跟踪计划和完成状态。 |
| Event | 安排日期、时间点或时间段日程，并设置提醒。 |
| Record | 记录实际活动，包括时间点、已结束区间和进行中的区间。 |
| Plan | 提供日视图、周视图、2×4 周计划、Todo & Done、月视图和任务列表。 |
| Statistics | 按分类、标签和睡眠查看时间数据。 |
| Category / Tag | 管理分类、标签及相关关键词规则。 |
| Sleep | 记录夜间睡眠和小憩；通过横向或纵向桌面小组件打卡。 |
| Voice | 使用语音输入和执行已有操作；相关在线服务由用户自行配置。 |
| 数据管理 | 本地提醒、ZIP 备份与恢复。 |

## 设计思路

界面以黑、白、灰为主，强调计划与实际分离。日常记录保存在本机数据库中；备份由用户自行管理。语音相关的在线能力需要单独配置，不是使用时间轴与记录功能的前提。

## 技术栈

Kotlin、Jetpack Compose、Room、DataStore、Glance、WorkManager 与 Gradle。应用源码位于 [app/src/main](app/src/main)，当前 Room Schema 见 [Schema JSON](app/schemas/io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/7.json)。

## 本地构建

- JDK 21（项目配置了 Gradle Daemon Java 21 工具链）。
- Android SDK Platform 36；项目的 `compileSdk` 和 `targetSdk` 均为 36，最低支持 API 26。
- Gradle 9.3.1 由仓库中的 Wrapper 管理；首次使用需下载发行包，后续可使用本地缓存。
- 通过 `ANDROID_HOME` 或未跟踪的 `local.properties` 配置 Android SDK 路径。不要提交密钥、签名文件或用户数据。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

macOS / Linux 可将命令中的 `.\gradlew.bat` 换为 `./gradlew`。依赖和 Gradle 发行包已缓存后，可按需添加 `--offline`。

## 项目状态与使用许可

项目仍在开发中；仓库提供源码和测试，不代表已经发布稳定版本。

本项目源码采用 [PolyForm Noncommercial License 1.0.0](LICENSE) 授权，允许符合协议要求的非商业使用、学习、研究、修改与分发。未经另行授权，不允许商业用途。具体权利与限制以 LICENSE 原文为准。
