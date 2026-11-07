# Changelog

本项目的版本号写在 `gradle.properties` 的 `mod_version`，每次发布前手动递增，
并同步 `fabric.mod.json`（构建时由 `${version}` 注入）与 `build/libs/chaoshelper-<version>.jar` 的文件名。

## 1.0.1 — 2026-11-07

### 变更

- 自动化不再被普通移动/攻击输入或打开界面打断。新增 `V` 键停止旅行/寻路；
  `P` 仍是 panic（关闭全部自动行为）。
- 旧配置一次性迁移（`configVersion` 2 → 3）：`travel.cancelOnInput` / `cancelOnScreen` 置为 `false`。
  想恢复旧行为可在 ClickGUI → 旅行 → 设置里打开。

### 修复

- 打开 ClickGUI / 工具 / HUD 编辑器等界面崩溃：
  `IllegalStateException: Can only blur once per frame`。
  1.21.11 的 `Screen.renderWithTooltip` 已经渲染背景，自定义 Screen 的 `render` 又调用了一次
  `renderBackground`，导致同一帧 blur 两次。

## 1.0.0 — 2026-11-07

首个开源版本。

### 新增

- **ClickGUI**：深色三栏界面（分类 / 模块 / 实时设置），支持滚动、开关、可拖动滑块、枚举循环。
- **可自定义 HUD**：`hud.elements` 元素数组，11 种类型（文本、坐标、自身、目标、玩家、掉落物、
  记录点、模块、通知、Baritone、性能），逐元素配置模板、位置、锚点、对齐、颜色、缩放、行高、
  内边距、间距、背景、边框、阴影、行数与排序。
- **HUD 编辑器**：画布直接拖动、元素列表选择、新增/复制/删除，属性分基础/样式/数据三页。
- **无动力爬升**（默认关闭）：俯冲→拉平→拉杆→爬升的俯仰循环，只改视角，不使用烟花。
- **甲飞**（默认关闭）：耐久结算前切走胸甲、等待服务端停飞后再装回并重新展开，重置耐久计时。
- **内置 Baritone 1.17.0**：以 Fabric Jar-in-Jar 嵌入，开箱即用；集成仍通过公开 API 反射调用。
- `/chaoshelper elytra [status|climb|swap on|off|toggle]` 指令。

### 变更

- 旅行模块合并步行 / 鞘翅直飞 / 滑翔三种模式，旧 `autopath` / `elytrafly` 保留为别名。
- 原点击菜单改为 ClickGUI；旅行坐标、路点、重载动态模块移到标题栏「工具」页面。
- HUD 从固定六面板改为元素系统，旧 `hud.players/drops/status/records/self/target` 坐标自动迁移。

### 修复

- 滑翔落地后仍持续改视角。
- 步行/鞘翅自然结束后未取消 Baritone 目标。
- 掉落物消失时若在 3 格内未判定为已捡起。
- `travel.minY` 对末地误判。

### 边界

- 只读取客户端本来可见的数据，不伪造或篡改发往服务端的数据包。
- 所有自动行为都能被玩家输入、打开界面、panic 立即打断。
- 鞘翅辅助默认关闭，是否启用由玩家自行决定；部分服务器可能视为违规，风险由使用者承担。
