# Money 2.3.1 发布说明

- 版本号：`2.3.1`
- versionCode：`104`
- 数据库：Room v14（结构未变化）
- 备份格式：Money backup v4（明文 JSON）

## 修复内容

- 修复部分 Android 设备从 DB13 升级到 DB14 时，SQLite 保留旧式 `ALTER TABLE` 行为，导致流水和提醒表的外键仍指向临时表 `accounts_v14` 的问题。
- 迁移开始时显式关闭 `legacy_alter_table`，确保重命名 `accounts_v14` 时所有子表外键统一更新为最终 `accounts`。
- 新增包含账户和周期提醒的真实迁移回归场景，并模拟连接预先启用旧式 `ALTER TABLE` 行为。

## 用户处理方式

看到“账本迁移需要处理”错误的用户无需清除应用数据。DB13→DB14 迁移在失败时会由 Room 事务回滚；安装 `2.3.1` 后点击“重试”即可重新执行迁移。

## 验证重点

- v14 临时账本表仍保持完整外键约束。
- 在 `legacy_alter_table=ON` 的初始连接状态下执行迁移后，`recurring_reminders` 外键目标为 `accounts`。
- 迁移完成后的 `PRAGMA foreign_key_check` 为空。
