# ChaosHelper M1–M7 交付报告

> **历史文档（对应 0.1.0）**：保留用于追溯设计决策，其中的版本号、体积与功能清单已过时。
> 当前版本与最新变更见 [CHANGELOG.md](CHANGELOG.md)。

构建环境（未改动）：MC 1.21.11 / Fabric Loader 0.18.2 / Fabric API 0.140.0+1.21.11 /
Yarn 1.21.11+build.3 / JDK 21 / Loom 1.17.21 / Gradle 9.8.2。

统一构建结果：`gradlew.bat build` **BUILD SUCCESSFUL**。

产物：

| 文件 | 说明 |
| --- | --- |
| `build/libs/chaoshelper-0.1.0.jar` | 主 jar，约 175 KB，可直接放 `mods/` |
| `build/libs/chaoshelper-0.1.0-api.jar` | 动态模块编译用 API jar（classifier `api`） |

---

## 基线核对（M0 现状）

读取现有工程后发现：骨架文件已存在但**从未接线**——`ChaosHelperClient` 仍是只打日志的 M0 空壳，
`build/libs/chaoshelper-0.1.0.jar` 仅 2162 字节（无 class）。因此本次是在已有骨架上补齐实现，
而非重建工程；`build.gradle`、`gradle.properties`、wrapper 均未改动版本。

---

## M1 核心框架

### 改动文件

- 重写 `ChaosHelperClient.java`：服务构造顺序、事件接线、`saveConfigQuietly`、`onWorldLeave`。
- 新增 `config/ConfigKeys.java`：全部配置键的集中登记（路径/类型/默认值/说明/取值器）。
- 修改 `config/ChaosConfig.java`：配置版本升到 2，新增 `team`、`dynamic` 段与多项键；`normalize()` 全量夹取与空段修复。
- 修改 `cache/EntityCache.java`：新增 `clear()`、`entitiesView()`、遮挡预计算。
- 新增 `command/ChaosCommand.java`：`/chaoshelper` 根指令。
- 修改 `module/modules/HudModule.java`（旧 `hud.x/y` 已并入四角布局）。

### 代码级自检

- 模块注册：`ModuleManager.registerBuiltin` × 9，`/chaoshelper list` 输出 id/状态/名称。
- 启停隔离：`Module.setRuntimeEnabled` 中 `onEnable` 抛异常则 `enabled` 保持 false；
  `ModuleManager.tick` 对每个模块单独 try-catch，`failModule` 只禁用该模块并写 config + 错误日志 + HUD 通知。
- panic：`ModuleManager.panic()` 只遍历 `behavior()==true` 的模块，HUD/ESP/Chams 不受影响；
  同时调用 `baritoneBridge.cancel()` 兜底。
- 配置键集中：所有键只在 `ChaosConfig` 声明、只在 `ConfigKeys` 登记，模块不落盘；`/chaoshelper config` 直接打印。
- 数据落盘仅 3 处：`config.json`、`data.json`、`modules/*.jar`（只读扫描）。
- 按键：`P` panic、`H` HUD，模块键默认未绑定；按键改动写回 `config.keybinds`。

### 遗留

- 路点（`Waypoint`）已有存储结构，暂未提供指令入口（本阶段未要求）。

---

## M2 ESP + Chams

### Mixin 目标（先输出再写，已按此顺序执行）

| 项 | 值 |
| --- | --- |
| 目标类 | `net.minecraft.client.render.entity.LivingEntityRenderer` |
| 注入 1 | `updateRenderState(LivingEntity;LivingEntityRenderState;F)V` → `@At("RETURN")`，重映射后 `method_62355` |
| 注入 2 | `getMixColor(LivingEntityRenderState)I` → `@At("RETURN")` + cancellable，重映射后 `method_62484` |
| 依据 | `javap` 确认两方法存在；重映射结果已用 `javap -v` 检查产物常量池 |

### 改动文件

- 新增 `render/EspWorldRenderer.java`：3D 框，注册在 `WorldRenderEvents.AFTER_ENTITIES`。
- 删除 `hud/EspOverlayRenderer.java`（2D 投影框已被替换；`ScreenProjector` 保留给 HUD 标签）。
- 重写 `chams/ChamsController.java`：距离/类型过滤、队友与目标配色、透明度。
- 修改 `hud/HudRenderer.java`：ESP 名单标签改为读缓存 + `ScreenProjector` 投影。
- 修改 `module/modules/EspModule.java` 未变（状态文案沿用）。

### 代码级自检

- 两阶段渲染：`context.matrices()` 取世界 `MatrixStack`，`context.consumers().getBuffer(RenderLayers.lines())` 取消费者。
- 顶点格式经字节码确认是 `POSITION_COLOR_NORMAL_LINE_WIDTH`（`RenderPipelines.RENDERTYPE_LINES_SNIPPET`），
  因此每个顶点写 `vertex(entry,x,y,z).color(argb).normal(entry,nx,ny,nz).lineWidth(w)`。
- 矩阵语义：`WorldRenderer` 对每个实体 `translate(world - cameraPos)`，说明基础栈在原点且已含相机旋转；
  故绘制时 `matrices.push(); translate(min - cameraPos);` 后按局部坐标画 12 条边。
- 不每帧遍历世界：只遍历 `entityCache.entitiesView()`（快照），上限 `esp.maxEntities`。
- 遮挡：`throughWalls=false` 时使用缓存里预计算的 `occluded` 集合，渲染路径零 raycast。
- 分类色：敌对红 / 被动绿 / 玩家蓝 / 掉落物黄 / 其他灰；队友与目标色单独可配。
- 关闭无残留：ESP/Chams 均无持久状态；动态模块的 `renderBoxes` 每 tick 由 `DynamicModule.tick` 清空，
  `onDisable` 再清一次。

### 遗留

- 穿墙描边走 `outlineColor`（原版 outline 通道），不同光影/渲染器下观感可能不一致。
- ESP 标签不做遮挡剔除（框已剔除），避免每帧多一次 raycast。

---

## M3 HUD 框架 + 玩家追踪 + 掉落物追踪

### 改动文件

- 重写 `hud/HudRenderer.java`：四角布局、折叠、自身面板、ESP 标签、错误框。
- 重写 `module/modules/PlayerTrackerModule.java`：可见/TAB 双来源、离开记录、维度切换跳过。
- 新增 `player/HealthEstimator.java`：血量估算状态机。
- 修改 `api/PlayerData.java`：新增 `firstSeenMillis`、`equipment`。
- 修改 `module/modules/DropTrackerModule.java`：新增 `resetForWorldChange()`。
- 修改 `config/ChaosConfig.java`：`estimateResetSeconds=30`、`maxEntries` 改为硬上限 100。

### 代码级自检

- 数据/渲染分离：追踪模块每 `refreshTicks`（默认 10）重建不可变列表；HUD 每帧只读该列表，不计算。
- 血量估算（已用纯 Java 运行验证，输出见下）：
  - 未见过 → `未加载 / -1`
  - 可见 → `客户端同步 / 高`，健康值来自实体
  - 离开 10s → `最后可见 / 可能过期`，误差 5.5
  - 离开 40s → `估算 (重置) / 低`，20/20
  - 重新可见 → 立即被同步值覆盖（7.0）
- 估算值不会覆盖真实值：`observe()` 始终返回同步值；估算只在 `absent()` 分支产生。
- 列表上限与折叠：追踪/掉落列表只做硬上限 100，HUD 按 `hud.maxEntries`（默认 10）截断并追加 `+N 更多`。
- 四角：左上玩家、左下掉落、右上状态+记录、右下自身+目标；越界时整块跳过而不是画到屏幕外。
- 距离分级着色：≤8 / ≤24 / ≤48 / 更远 四档，队友与目标优先。
- 护甲：`getArmor()` 可见值，玩家面板标题标注「护甲未计附魔」，自身面板额外标注未计药水。

### 遗留

- 服务端若伪造血量，客户端只能显示伪造值（已在 README 说明）。
- 掉落物按 UUID 跟踪，堆叠合并/拆分会产生新条目，属预期行为。

---

## M4 记录点 + team + TargetLink 骨架

### 改动文件

- 修改 `data/RecordEntry.java`：新增 `subject`（离开记录的玩家名）。
- 修改 `data/DataStore.java`：`addRecord(..., subject, max)` 重载、`lastRecordBySubject`。
- 修改 `record/RecordManager.java`：`logoff()`、`lastLogFor()`、`autoDeath` 开关、维度变化跳过。
- 新增 `target/TargetResolver.java`：item / player / death / log 四类目标解析 + 跨维度检查。
- 修改 `target/TargetLink.java`、`team/TeamManager.java`：适配 `GameProfile` record 访问器。
- 修改 `command/ChaosCommand.java`：team / record / target / autopath goto 子指令。

### 代码级自检

- 死亡记录：坐标、维度、时间、到当前距离（HUD 记录面板显示 `%dm` 或「其他维度」）。
- log 点：玩家从实体视野消失时写一次，`subject` 存玩家名；`goto log <name>` 取最近一条。
- 维度切换不误记：`PlayerTracker` 检测 `worldId` 变化后整表清空并直接返回，不产生 logoff；
  `RecordManager.logoff` 再用 `lastWorld` 二次校验。
- 队友名单：本地 `data.json`，不读计分板；HUD/ESP/Chams 统一走 `TeamManager`。
- goto 解析：`TargetResolver` 对死亡/离开点比对 `entry.world` 与当前维度，不一致返回
  「目标在 X，当前维度 Y，请先前往同一维度」。
- Baritone 不可用时指令仍可解析，执行阶段返回「Baritone 不可用: 原因」。

### 遗留

- `goto item` 只认缓存内 UUID（含已消失但仍在 60s 记录窗口内的掉落物）。

---

## M5 Baritone 封装 + AutoPath + ElytraFly

### 核实结果（已完成）

`libs/baritone-api-fabric-1.17.0.jar` 已从官方 release 下载，sha1 与发布页 `checksums.txt` 一致
（`553bf1f57179e7d765a6131659b62b4c865ed47e`）。

- `fabric.mod.json`：id = `baritone`，version = `1.17.0`，`minecraft` 精确为 `1.21.11`，license LGPL-3.0
- `javap` 核实（全部存在且签名一致）：
  `BaritoneAPI.getProvider()`、`IBaritoneProvider.getPrimaryBaritone()`、
  `IBaritone.getPathingBehavior()/getCustomGoalProcess()/getElytraProcess()`、
  `ICustomGoalProcess.setGoalAndPath(Goal)`、`IElytraProcess.pathTo(Goal)/resetState()`、
  `IPathingBehavior.isPathing()/cancelEverything()`、`IBaritoneProcess.isActive()`、
  `GoalBlock(int,int,int)`、`GoalXZ(int,int)`
- 结论：`pathTo` 接受的是 `Goal`（另有 `pathTo(BlockPos)` 重载），原桥接的反射签名无需改动。

两级能力拆分：

| 能力 | 依赖 | 缺失时表现 |
| --- | --- | --- |
| 普通寻路 | `BaritoneAPI.getProvider` → `getPrimaryBaritone` → `getPathingBehavior/getCustomGoalProcess` | 整体标记不可用 |
| 鞘翅 | `IBaritone.getElytraProcess` → `IElytraProcess.pathTo(Goal)` | 仅 ElytraFly 不可用，寻路不受影响 |

调用入口（反射字符串，编译期零依赖）：`BaritoneAPI.getProvider`、`IBaritoneProvider.getPrimaryBaritone`、
`IBaritone.getPathingBehavior/getCustomGoalProcess/getElytraProcess`、
`ICustomGoalProcess.setGoalAndPath(Goal)`、`IElytraProcess.pathTo(Goal)/resetState`、
`IPathingBehavior.cancelEverything/isPathing`、`GoalBlock(int,int,int)`、`GoalXZ(int,int)`。

### 改动文件

- 重写 `baritone/BaritoneBridge.java`：mod id 兼容 `baritone` / `baritone-api-fabric`，版本前缀 `1.17.`，
  核心/鞘翅分别初始化，`startPath/startElytra/cancel/isPathing/isElytraActive/statusLine`。
- 修改 `module/modules/AutoPathModule.java`：新目标先 `stop(false)` 再设置；`overridable=false`。
- 修改 `module/modules/ElytraFlyModule.java`：`overridable=false`。
- `fabric.mod.json`：`suggests.baritone = ">=1.17.0"`，不 hard depend。

### 代码级自检

- 未装 Baritone：`initialize()` 捕获所有 Throwable 并置 `available=false`，构造期不加载任何 Baritone 类，
  启动不会失败；HUD 状态行显示「未安装 (需要 Baritone 1.17.x Fabric)」。
- 装了但版本不符：显示「版本 X 不兼容 (需要 1.17.x)」。
- panic 中断：`ModuleManager.panic` 关闭 autopath/elytrafly 触发 `onDisable → stop() → bridge.cancel()`。
- 玩家输入中断：`InputWatcher` 检测移动/跳跃/攻击/使用/潜行/疾跑；打开界面亦可中断（可配）。
- 不复制实现：jar 内 `com/chaoshelper/baritone/` 仅 1 个桥接类，无任何 `baritone.*` 类。

### 遗留

- 探测时机已修正：HUD 在主菜单调用时 `getPrimaryBaritone()` 为 null，原先会把桥接永久标记不可用；
  现在「未进入世界」直接返回、异常后 5 秒重试、只有「未安装 / 版本不符」才视为终态。
- `cancelEverything()` 实际返回 `boolean`（原以为是 void），反射调用忽略返回值即可，无影响。
- 仍需实机验证的点：不同光影/服务端下 Baritone 的实际寻路体验，属运行时行为而非接口问题。

---

## M6 AutoEat + B2 动态加载

### 改动文件

- 重写 `dynamic/DynamicModuleMetadata.java`：`schema/id/name/version/apiVersion/entrypoint/description/defaultEnabled/behavior/overrides/settings`。
- 修改 `dynamic/DynamicModule.java`：注册 id 用 `effectiveId()`，`behavior` 参与 panic。
- 修改 `dynamic/DynamicModuleLoader.java`：schema 校验、overrides 校验、数量上限、settings 落库、错误汇总。
- 修改 `build.gradle`：新增 `apiJar` 任务（classifier `api`，仅 `com/chaoshelper/api/**`）。
- 修改 `module/modules/AutoEatModule.java`：尊重 `autoEat.restoreSlot`。

### 代码级自检

- 动态 jar 隔离：`ModuleClassLoader.loadClass` 直接拒绝 `net.minecraft.`、`net.fabricmc.`、
  `org.spongepowered.`、`com.mojang.`、`org.lwjgl.`、`io.netty.`、以及 `com.chaoshelper.`（`api` 除外）。
- 资源隔离：`validateEntries` 只允许 `.class` + `META-INF/chaoshelper-module.json` + MANIFEST/LICENSE/NOTICE，
  出现 `fabric.mod.json`、mixin 配置、`assets/` 一律整包拒绝。
- 崩溃隔离：`DynamicModule.tick` 抛异常 → `ModuleManager` 捕获 → 禁用该模块 + 写 `config.modules[id]=false`
  + 错误日志 + HUD 红框 + 聊天提示，其他模块继续运行。
- 加载失败可见：`fail()` 同时写 `ErrorLog`（HUD 红框数据源）与 slf4j，`reload()` 结束再弹一次汇总提示，不静默跳过。
- 覆盖内置：`overrides` 指向 `autoeat`/`player-tracker`/`drop-tracker`/`record`；受保护的
  `esp`/`chams`/`hud`/`autopath`/`elytrafly` 被 `isProtected` 拦下并报错。
- 热重载：`reloadmodules` 先禁用旧实例、关闭 ClassLoader，再按目录重建；删除 jar 后自动恢复内置版。
- API jar 纯净性：解包确认只含 `com/chaoshelper/api/*.class` 与 MANIFEST，无 `fabric.mod.json`、无 mixin、无 assets。
- AutoEat：阈值默认 18；只选快捷栏内、有 `FOOD` 组件且不在冷却的物品；
  `interactItem` 触发后等待 `isUsingItem` 自然结束（不发包加速）；玩家输入/开界面/死亡/旁观即停；
  槽位按 `restoreSlot` 恢复；失败进入冷却。

### 遗留

- 动态模块仍可通过 `Thread.currentThread().getContextClassLoader()` 绕过类加载器限制（Java 层无法完全封死），已在 README 标注为「约定 + 类加载器双重约束」。
- `overrides` 覆盖后，内置版本的运行时状态不做迁移（按 id 共用同一 config 键）。

---

## M7 收尾 + 统一构建

### 改动文件

- 新增 `src/main/resources/assets/chaoshelper/icon.png`（128×128，程序化生成，已目视确认无异常）。
- 新增 `LICENSE`（LGPL-3.0 全文，165 行）、`NOTICE`（Baritone 来源与「不打包」声明）、`README.md`、`.gitignore`。
- 修改 `fabric.mod.json`：补 `icon`，`suggests.baritone=">=1.17.0"`，更新描述。
- 修改 `build.gradle`：`apiJar` 任务、NOTICE 一并入 jar；未恢复 `withSourcesJar()`。
- 清理 M0 残留的 `build/libs/chaoshelper-0.1.0-sources.jar`（旧空产物）。

### 构建与修复过程

1. 第一次 `compileJava`：13 个错误，集中在三处 API 变更
   - `Entity.getPos()` 已移除 → 改用 `getX/getY/getZ` 组合 `Vec3d`
   - `GameProfile` 变为 record → `getId()/getName()` 改为 `id()/name()`（4 个文件）
   - `HealthEstimator` 把字段误当方法调用
2. 修复后 `compileJava` 通过。
3. 第一次 `build` 通过；检查产物发现 mixin 配置无 refmap，用 `javap -v` 确认 Loom 已把
   `@Inject(method=...)` 字符串直接重映射为 `method_62355` / `method_62484`（新版 Loom 的 mixin 重映射，无需 refmap）。
4. 逻辑修补后重建：`hud.maxEntries` 折叠被模块层提前截断、`errorDisplaySeconds=0` 语义、
   ESP 遮挡射线从每帧移到缓存、AutoEat `restoreSlot` 未生效、玩家装备与距离配色。
5. 最终 `build` 通过。

### 性能检查

| 项 | 处理 |
| --- | --- |
| HUD 刷新频率 | 玩家 10 tick、掉落 5 tick、实体缓存 5 tick；渲染只读快照 |
| 实体缓存复用 | ESP/追踪/掉落/HUD 全部读 `EntityCache`，世界只在一处遍历 |
| 渲染事件 | 不遍历世界；遮挡 raycast 预计算；`entitiesView()` 零拷贝；实体数上限 128、标签上限 32 |
| 动态模块 | 渲染框每 tick 清空，最多 256 个/模块 |

---

# V2 需求扩展（第二轮）

用户第二轮提出：掉落物捡起判定、点击界面与 HUD 编辑、鞘翅飞与自动寻路合并、
主世界/末地可飞、末地自动标记到 Xaero、可设坐标/导航路点/自动取鞘翅、种子推测。

## 已完成

### 1. 掉落物「捡起即消失」

`DropTrackerModule` 记录每个掉落物最后一次可见时的距离。消失瞬间若玩家在 3 格内，
判为已捡起，直接从列表移除；否则保留 `dropTracker.recordSeconds` 并标「已消失」。
依据：Minecraft 拾取半径约 1 格（2 格内必收），3 格是保守阈值。

### 2. 点击菜单 + HUD 拖拽编辑

- `gui/ChaosMenuScreen`：模块开关（双列）、HUD 编辑、重载动态模块、旅行模式循环、
  坐标输入、出发/停止、填入当前坐标、下个路点、Xaero 状态。按 `K` 或 `/chaoshelper menu` 打开。
- `gui/HudEditorScreen`：六个面板直接拖动，落盘到 `hud.players/drops/status/records/self/target`；
  「全部重置」恢复角落吸附。位置是绝对坐标，越界自动夹取。
- 修正：菜单坐标标签原画在按钮上（y=152），已移到输入框上方（y=106）。

### 3. 鞘翅飞 + 自动寻路合并为 `travel`

- 删除 `autopath` / `elytrafly` 两个模块，新增 `travel`，四种模式：
  `AUTO`（>96 格且有鞘翅走飞行）、`WALK`、`ELYTRA`（Baritone 直飞）、`GLIDE`（只调视角，不用烟花）。
- 旧指令 `autopath ...` / `elytrafly ...` 保留为别名，分别强制 WALK / ELYTRA。
- 打断：玩家输入、打开界面、panic、死亡、换维度、滑翔落地，全部立即停止。
- **修复**：WALK/ELYTRA 自然结束时未取消 Baritone 目标，现在会 `cancel()`。
- **修复**：GLIDE 落地后仍在改视角，现在检测 `!isGliding()` 并停止。
- `travel.minY` 现在只在下界强制（天花板低），主世界/末地任意高度可起飞。

### 4. Xaero 路点集成（按真实文件格式实现）

实测本机 Xaero 26.6.0 / XaeroPlus 2.37.0 的落盘结构：

```
xaero/minimap/<世界>/dim%0|dim%-1|dim%1|dim%<ns>$<path>/
  waypoints.txt          单文件模式（"新的世界" 实测）
  mw$<set>_<n>.txt       多集合模式（Multiplayer_cnpvp.top 实测 mw$default_1.txt）
waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination
```

- 读取：合并 `waypoints.txt` + 所有 `mw$*.txt`，14 字段解析，坏行跳过不整包失败。
- 写入：目录已有 `mw$` 文件时写独立集合 `mw$<set>_1.txt`（`xaero.setName`，默认 `chaoshelper`），
  即"新开一个目录/集合"；单文件模式则追加到 `waypoints.txt`。首次写入生成 `.chaoshelper.bak`，
  永不改写已有行。
- 生成行与真实行逐字段对照通过（14/14 字段，type=0 普通路点）。
- 维度目录不存在时自动创建，便于首次进末地就标记。
- 末地自动标记：每 40 tick 扫描潜影贝（末地城）与展框鞘翅，96 格网格去重。
  写入失败也记入内存去重，避免每 2 秒重试刷日志。

### 5. 导航

- `/chaoshelper travel goto <x> <y> <z> [auto|walk|elytra|glide]`
- `... goto waypoint <名称>`（当前维度 Xaero 路点）
- 菜单「下个路点」循环填入当前维度路点坐标。
- 跨维度/不存在均给中文友好错误，不抛异常。

### 6. 自动取鞘翅

到达目标后若 `travel.autoTakeElytra=true`，扫描 4 格内放着鞘翅的物品展示框并击碎一次，
掉落物按正常拾取流程收取。不凭空生成物品，不绕过拾取。

### 7. 种子

- `/chaoshelper seed`：**单人**从 `IntegratedServer.getOverworld().getSeed()` 直读，精确值；
  多人明确回复"服务器不下发种子"，不编造。
- `/chaoshelper seed export`：导出 9×9 生物群系采样 + 当前维度路点到
  `config/chaoshelper/seed-evidence-<dim>.json`，供 SeedCracker 类外部工具使用。
- **未做真正的多人种子反解**：客户端没有服务端生成器，穷举需要重算世界生成，属于离线工程，
  不在本模组范围内（详见已知限制）。

## 本轮验证

| 项 | 结果 |
| --- | --- |
| 编译 | 通过（仅 deprecation 提示） |
| 完整构建 | 通过，`chaoshelper-0.1.0.jar` 209 KB |
| 路点行格式 | 与真实 Xaero 文件逐字段对照，14/14 一致 |
| 旧模块引用 | `autopath`/`elytrafly` 仅剩指令别名与注释，无残留类 |
| 部署 | 已复制到 `1.21.11vape/mods/chaoshelper-0.1.0.jar` |

---

# 总报告

## 模块清单

| id | 名称 | 行为模块 | 可被动态覆盖 |
| --- | --- | --- | --- |
| `hud` | HUD | 否 | 否 |
| `esp` | ESP 实体描边 | 否 | 否 |
| `chams` | Chams 着色 | 否 | 否 |
| `player-tracker` | 玩家追踪 | 否 | 是 |
| `drop-tracker` | 掉落物追踪 | 否 | 是 |
| `record` | 本地记录 | 否 | 是 |
| `travel` | 旅行：步行/鞘翅直飞/滑翔 | 是 | 否 |
| `autoeat` | 自动进食 | 是 | 是 |

## 指令清单

`/chaoshelper`、`list`、`status`、`panic`、`reloadmodules`、`config`、
`on|off|toggle <module>`、`<module> on|off|toggle`、
`team add|remove|list|clear`、`record list|clear`、`target set|clear`、
`menu`、`travel goto <x> <y> <z> [mode]`、`travel goto item|player|death|log|waypoint`、
`travel stop`、`travel mode <auto|walk|elytra|glide>`、
`autopath ...` / `elytrafly ...`（兼容别名）、
`xaero status|list|mark <名称>`、`waypoint list|add <名称>`、`seed`、`seed export`。

## 配置键

见 `README.md` 表格与 `/chaoshelper config` 实时输出（`ConfigKeys` 为唯一登记处）。

## 已知限制

1. 仅客户端可见数据；目标离开加载范围只能给最后可见位置。
2. 护甲按可见装备计算，不含附魔/药水。
3. Baritone 不随项目分发；1.17.0 的接口签名已核实，实际寻路效果仍取决于用户安装的版本与服务器策略。
4. 服务端伪造血量时客户端无法识别。
5. 动态模块的隔离是「类加载器 + 元数据校验」，不是安全沙箱。
6. 客户端指令不发送到服务端，因此在服务端 tab 补全里不可见。
7. **种子**：单人直读精确；多人无法获取，只能导出证据。不做客户端侧穷举反解。
8. **Xaero 运行期缓存**：外部写文件后通常要重进世界/重启才显示；Xaero 退出时可能回写覆盖，
   建议先退出游戏再写，或保留 `.chaoshelper.bak`。
9. **鞘翅起飞**：Baritone 不负责从地面起飞，需玩家跳下展开鞘翅后接管；
   滑翔模式只改视角，不喷烟花。

## 安装使用

1. Fabric Loader 0.18.2 + MC 1.21.11 + Fabric API 0.140.0+1.21.11。
2. `chaoshelper-0.1.0.jar` 放入 `mods/`。
3. 可选：Baritone 1.17.x Fabric 版单独放入 `mods/`（已用 1.17.0 核实，sha1 `553bf1f5…`）。
   `libs/` 里的副本仅用于开发期签名核对，不会被本模组加载或打包。
4. 进游戏按 `K` 开菜单、`H` 开 HUD、`P` panic；`/chaoshelper list` 查看模块。
5. 动态模块放 `config/chaoshelper/modules/`，`/chaoshelper reloadmodules` 生效。
