# Money

一个完全离线的 Android 个人记账应用，基于 **Kotlin + Jetpack Compose** 实现。当前版本为 `2.4.4`（versionCode `109`）。

仓库地址: [https://github.com/shihuaidexianyu/piggy-bank](https://github.com/shihuaidexianyu/piggy-bank)

## ✨ 功能特性

- 多账户管理：账户支持排序、隐藏、关闭和重新开启；隐藏账户仍计入净资产。
- 现金流记录：记录收入与支出，备注可选，并支持最近备注建议。
- 账户间转账：支持从任意账户转入/转出。
- 对账调整：对账作为普通记账事件入账，按固定差额影响余额。
- 期初资产：账户新建的初始余额作为开户时的期初资产处理。
- 调整记录：支持手动修正余额（独立于自动计算）。
- 提醒：支持月度、年度、间隔天数等周期，提供应用内到期卡片与后台通知。
- 首页：展示净资产、本月收支、净现金流、月预算与净资产目标进度。
- 明细与搜索：按账户、类型、日期、金额和关键字筛选，并支持精确下钻。
- 隐私保护：支持生物识别锁、金额遮罩、最近任务隐藏及 Widget/通知独立遮罩。
- JSON 备份：导出明文 backup v4，支持旧 v1-v3 导入、导入前安全快照与撤销。
- 外部入口：支持桌面 Widget、快捷方式、分享文本记账和通知深链。

## 📱 界面截图

以下截图使用调试构建自动生成的随机演示数据。

<!-- markdownlint-disable MD033 -->

<table>
  <tr>
    <td align="center"><img src="_screenshots/readme/home.png" width="240" alt="首页" /><br />首页</td>
    <td align="center"><img src="_screenshots/readme/history.png" width="240" alt="历史" /><br />历史</td>
  </tr>
  <tr>
    <td align="center"><img src="_screenshots/readme/accounts.png" width="240" alt="账户" /><br />账户</td>
  </tr>
  <tr>
    <td align="center"><img src="_screenshots/readme/settings.png" width="240" alt="设置" /><br />设置</td>
    <td align="center"><img src="_screenshots/readme/reminders.png" width="240" alt="提醒中心" /><br />提醒中心</td>
  </tr>
  <tr>
    <td align="center"><img src="_screenshots/readme/account-detail.png" width="240" alt="账户详情" /><br />账户详情</td>
    <td align="center"><img src="_screenshots/readme/create-account.png" width="240" alt="新建账户" /><br />新建账户</td>
  </tr>
  <tr>
    <td align="center"><img src="_screenshots/readme/record-cash-flow.png" width="240" alt="记录出账" /><br />记录出账</td>
    <td align="center"><img src="_screenshots/readme/record-transfer.png" width="240" alt="记录转账" /><br />记录转账</td>
  </tr>
  <tr>
    <td align="center"><img src="_screenshots/readme/update-balance.png" width="240" alt="核对余额" /><br />核对余额</td>
    <td align="center"><img src="_screenshots/readme/create-reminder.png" width="240" alt="新建提醒" /><br />新建提醒</td>
  </tr>
</table>

<!-- markdownlint-enable MD033 -->

## 🧱 技术栈

- **语言**: Kotlin 2.2.20
- **UI**: Jetpack Compose BOM 2025.10.01 + Material 3
- **架构**: Clean Architecture（Domain / Data / UI）+ MVVM
- **数据库**: Room 2.8.0（SQLite），模式版本 14
- **状态存储**: DataStore Preferences 1.1.7
- **导航**: Navigation Compose 2.9.5
- **依赖注入**: 手动注入（`MoneyAppContainer`）
- **SDK 版本**: minSdk 31，targetSdk/compileSdk 36
- **包名**: `com.shihuaidexianyu.money`

## 🚀 构建与测试

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建
./gradlew assembleRelease

# 运行全部单元测试
./gradlew test

# 运行 Debug Lint
./gradlew lintDebug
```

也可使用发布脚本进行版本号管理、测试、签名校验与打包：

```bash
# 仅打包并自动 bump 版本
.\scripts\build-release.ps1

# 先测试再打包、提交、推送
.\scripts\build-release.ps1 -RunTests -Commit -Push
```

发布包必须使用本地 `signing/keystore.properties` 配置的正式证书签名。备份文件是未加密 JSON，请只保存到可信位置。

## 📌 当前发布范围

`2.4.2` 修复金额输入框同时启动系统键盘和自定义键盘造成的弹出卡顿，统一使用支持金额运算和实时预览的自定义键盘。性能基准自动化和更完整的设备矩阵验证保留到后续版本。详见 [2.4.2 发布说明](RELEASE_NOTES_2.4.2.md)。

## 🗂️ 目录结构

```text
app/src/main/java/com/shihuaidexianyu/money/
├── domain/          # 业务模型与接口、UseCase
├── data/            # Room 实体、DAO、仓库实现
├── ui/              # Compose 页面与各 feature ViewModel
├── navigation/      # 导航与 ViewModel 工厂
└── util/            # 工具方法与格式化逻辑
```

## 📄 协议

All rights reserved.
