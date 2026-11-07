# ChaosHelper

Minecraft **1.21.11 / Fabric** 客户端生存辅助模组。

> **定位声明**：本模组只读取客户端本来就能看到的数据（实体、血量、TAB 列表、聊天、本地记录），
> 用于信息展示与本地记录。它**不**实现自动采集、自动战斗、刷物品，**不**绕过反作弊，
> **不**伪造或篡改发往服务端的数据包。自动行为不会因普通移动/攻击输入而中断，
> 按 `V`（停止旅行/寻路）或 `P`（panic，关闭全部自动行为）随时结束。

- 版本锁定：MC 1.21.11 / Fabric Loader 0.18.2 / Fabric API 0.140.0+1.21.11 / Yarn 1.21.11+build.3 / JDK 21
- 当前版本：**1.0.5**（变更记录见 [CHANGELOG.md](CHANGELOG.md)）
- 构建：`.\gradlew.bat build`
- 许可证：LGPL-3.0（见 `LICENSE`、`NOTICE`）

---

## 安装

1. 安装 Fabric Loader 0.18.2（MC 1.21.11）。
2. 把 `build/libs/chaoshelper-<version>.jar` 放进 `.minecraft/mods/`。
3. 需要 Fabric API 0.140.0+1.21.11。
4. **Baritone 1.17.0 已内置**（`META-INF/jars/baritone-api-fabric-1.17.0.jar`），不需要再单独安装；
   如果 `mods/` 里已有独立 Baritone，请删掉，避免 mod id 重复。
   已用官方 `baritone-api-fabric-1.17.0.jar`（sha1 `553bf1f5…`，mod id `baritone`）核对过接口签名。

集成只通过公开 API 的反射入口调用，不复制其源码；Baritone 以 LGPL-3.0 原样再分发，来源与源码地址见 `NOTICE`。

---

## 指令

所有指令都是客户端指令，不会发送到服务端。按用途分组，每条单独说明。

### 界面与总闸

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper` | 打印帮助 |
| `/chaoshelper menu` | 打开 ClickGUI（等同按 `K`） |
| `/chaoshelper list` | 列出全部模块的 id、名称与开关状态 |
| `/chaoshelper status` | 已开启模块的运行状态、Baritone 状态、缓存数量 |
| `/chaoshelper panic` | 总闸：关闭所有行为模块，保留 HUD |
| `/chaoshelper reloadmodules` | 热重载 `config/chaoshelper/modules/*.jar` |
| `/chaoshelper config` | 打印全部配置键、当前值与默认值 |

### 模块开关

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper on <模块>` | 开启模块 |
| `/chaoshelper off <模块>` | 关闭模块 |
| `/chaoshelper toggle <模块>` | 切换模块 |
| `/chaoshelper <模块> on\|off\|toggle` | 同上，模块名在前 |

模块 id：`hud`、`esp`、`chams`、`player-tracker`、`drop-tracker`、`record`、`travel`、`autoeat`、`autotake`。
例如 `/chaoshelper esp on` 或 `/chaoshelper travel toggle`。

### 旅行

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper travel goto <x> <y> <z>` | 按默认模式前往坐标 |
| `/chaoshelper travel goto <x> <y> <z> walk` | 强制步行（Baritone 寻路，可精确到 Y） |
| `/chaoshelper travel goto <x> <y> <z> elytra` | 强制鞘翅直飞（只按 X/Z 导航） |
| `/chaoshelper travel goto <x> <y> <z> glide` | 强制滑翔模式（需已展开鞘翅） |
| `/chaoshelper travel goto item <uuid>` | 前往缓存中的掉落物 |
| `/chaoshelper travel goto player <玩家>` | 前往追踪到的玩家 |
| `/chaoshelper travel goto death` | 前往最后一次死亡点 |
| `/chaoshelper travel goto log <玩家>` | 前往某玩家最后可见位置 |
| `/chaoshelper travel goto waypoint <名称>` | 前往小地图路点 |
| `/chaoshelper travel goto elytra` | 走向最近的、装着鞘翅的展示框 |
| `/chaoshelper travel goto ship` | 飞向最近的未搜末地船 |
| `/chaoshelper ship list` | 列出当前维度已发现的末地船（含已搜/未搜与距离） |
| `/chaoshelper ship goto` | 同 `travel goto ship` |
| `/chaoshelper ship clear` | 清空末地船列表 |
| `/chaoshelper ship dir <north\|east\|south\|west\|度数>` | 设定巡航方位，并按当前位置重新起算 |
| `/chaoshelper ship sweep on\|off\|toggle` | 单向前进巡航开关 |
| `/chaoshelper travel stop` | 停止当前旅行（等同按 `V`） |
| `/chaoshelper travel mode <auto\|walk\|elytra\|glide>` | 切换默认旅行模式 |
| `/chaoshelper autopath ...` | 旧名别名，强制步行 |
| `/chaoshelper elytrafly ...` | 旧名别名，强制鞘翅 |

> 鞘翅直飞传的是 X/Z（Baritone 的 `GoalXZ`），它内部会把巡航高度**硬编码成 y=64**，
> 然后再找「安全落点」。所以**地下/洞穴目标请用 `walk`**，否则只会落在目标正上方的地面。
> 末地船走的是另一条路：传 `GoalBlock`（保留船的真实高度），见下面的末地船章节。

### 鞘翅辅助

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper elytra` / `elytra status` | 查看开关、阈值与运行状态 |
| `/chaoshelper elytra climb on\|off\|toggle` | 无动力爬升（默认关闭） |
| `/chaoshelper elytra swap on\|off\|toggle` | 甲飞：切甲省耐久（默认关闭） |

开启任一开关会自动启用 `travel` 模块。机制、保护与风险见下方「鞘翅辅助」章节。

### 团队、记录与目标

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper team add <玩家> [备注]` | 加入本地队友名单 |
| `/chaoshelper team remove <玩家>` | 移除队友 |
| `/chaoshelper team list` | 查看队友名单 |
| `/chaoshelper team clear` | 清空队友名单 |
| `/chaoshelper record list` | 查看最近的本地记录点 |
| `/chaoshelper record clear` | 清空本地记录 |
| `/chaoshelper target set <玩家>` | 设置 HUD/ESP 的目标标记 |
| `/chaoshelper target clear` | 清除目标标记 |

### 小地图与种子

| 指令 | 说明 |
| --- | --- |
| `/chaoshelper xaero status` | Xaero 集成状态（路径、是否可写） |
| `/chaoshelper xaero list` | 列出当前维度路点 |
| `/chaoshelper xaero mark <名称>` | 在当前位置写一个路点 |
| `/chaoshelper waypoint list\|add <名称>` | 同上（简写） |
| `/chaoshelper seed` | 显示种子：单人精确直读，多人明确说明无法获取 |
| `/chaoshelper seed export` | 导出生物群系 + 路点证据，交给 SeedCracker 等外部工具 |

### 默认按键

| 键 | 作用 |
| --- | --- |
| `V` | 停止旅行 / 寻路 |
| `P` | panic：关闭全部自动行为 |
| `H` | HUD 开关 |
| `K` | 打开 ClickGUI |

各模块默认未绑定，可在「按键设置 → 杂项」里绑定。

---

## 模块

| id | 名称 | 说明 |
| --- | --- | --- |
| `hud` | HUD | 高度可自定义元素（文本/列表/坐标/自身/目标/性能）+ 错误提示 |
| `esp` | ESP | 世界内 3D 实体框，分类配色，可选穿墙 |
| `chams` | Chams | Mixin 实体着色 / 描边 |
| `player-tracker` | 玩家追踪 | 名字、距离、血量（同步/估算分开标注）、护甲、装备、移动状态 |
| `drop-tracker` | 掉落物追踪 | 物品名×数量、距离、存在时长 |
| `record` | 本地记录 | 死亡点、离开点、聊天日志点 |
| `travel` | 旅行 | 步行 / 鞘翅直飞 / 滑翔三合一；普通输入不打断，按 `V` 停止、`P` panic |
| `autoeat` | 自动进食 | 阈值触发，尊重冷却与进食动画，玩家输入即暂停 |
| `autotake` | 自动取鞘翅 | 扫描附近展示框，取出里面鞘翅；可设范围与冷却，已有鞘翅时跳过 |
| `endship` | 末地船自动搜刮 | 扫描龙首/酿造台/鞘翅展示框 → Baritone 飞过去 → 步行接近展示框 → 取鞘翅 → 下一艘；发现即标记 Xaero 路点与本地列表 |
| `autolog` | 自动退服 | 血量低于阈值且背包（含副手）没有不死图腾时断开连接，只在多人模式生效 |

HUD 默认布局：左上玩家、右上坐标/模块/记录点、左下掉落物、右下自身与目标；
所有元素都能在编辑器里增删、拖动、改类型/模板/样式，不再有固定面板。
每个列表默认最多 10 行，超出显示 `+N 更多`。

---

## ClickGUI 与 HUD 编辑

按 `K` 或 `/chaoshelper menu` 打开 ClickGUI（类作弊客户端的三栏布局）：

- 左侧分类：渲染 / 信息 / 移动 / 玩家 / 杂项；中间模块列表，右侧实时设置面板。
- 模块行左侧色条表示开启；点右侧开关块直接切换，点名字选中后在右侧调参数。
- 设置面板支持开关、可拖动滑块、枚举循环；修改立即写入 `config.json`。
- 标题栏「工具」进入旅行/路点面板：坐标出发、填入当前坐标、下个路点、重载动态模块、鞘翅辅助入口。
- `hud` 模块的「HUD 布局」进入 HUD 编辑器。

HUD 编辑器：

- 画布就是真实 HUD，左键直接拖动任意元素；左侧列表选中元素，点色块切换显示。
- 「新增」创建自定义文本元素，「复制」「删除」管理，「属性」进入单元素编辑。
- 单元素属性三页：
  - **基础**：显示、标题、类型（文本/坐标/自身/目标/玩家/掉落/记录/末地船/模块/通知/Baritone/性能）、
    锚点、对齐、排序、空列表隐藏、位置重置。
  - **样式**：缩放、行高、内边距、间距、背景、边框、文字阴影、文字颜色、强调色、重置样式。
  - **数据**：逐行模板、关键词过滤、行数上限、恢复默认模板。
- 未拖动的元素按锚点 `tl/tr/bl/br` 自动堆叠，`gap` 控制间距；拖动后存绝对坐标，换分辨率会被夹取回屏幕内。

### HUD 模板变量

全局：`{x} {y} {z} {xi} {yi} {zi} {dim} {biome} {yaw} {pitch} {facing}`、
`{hp} {maxhp} {absorption} {armor} {food} {air} {exp} {speed} {vspeed} {onground} {gliding} {sprinting} {sneaking}`、
`{fps} {ping} {mem_used} {mem_max} {time} {real_time} {chunk} {light}`、
`{modules} {players} {drops} {target} {baritone} {mainhand} {offhand} {helmet} {chest} {legs} {boots}`。

列表行额外：
- 玩家：`{name} {dist} {hp} {maxhp} {armor} {flags} {equip} {team} {visible} {first} {lastseen} {index}`；
- 掉落：`{item} {count} {dist} {age} {visible}`；
- 记录：`{type} {message} {x} {y} {z} {time} {dist}`；
- 模块：`{id} {name} {status}`；通知：`{message} {error}`；
- 末地船：`{name} {dist} {dir} {looted} {x} {y} {z} {index}`（`{dir}` 是相对玩家的方位：东/东南/南…）。

未知变量会原样保留，便于发现拼写错误；窗口关闭或编辑器改动时自动保存。

## 鞘翅辅助（默认全部关闭，由玩家自行选择）

菜单里点「鞘翅辅助」进入设置；命令行等价：`/chaoshelper elytra [status | climb|swap on|off|toggle]`。
两个开关默认都是 `false`，阈值可以直接在设置页循环调整（或改 `config.json`）。开启任一开关会自动启用
`travel` 模块。两者都在按 `V` 停止、`P` panic 或死亡时结束；普通移动/攻击输入不再打断。
甲飞在打开界面等无法安全换装的场景会先等界面关闭再收回鞘翅，绝不把鞘翅留在快捷栏。

### 无动力爬升（`travel.glideClimb`）

按「俯冲 → 拉平 → 拉杆 → 爬升 → 顶点」循环**只改 `pitch`**；有目标时 `yaw` 仍指向目标，自由飞行时保持不变。
位置、速度、耐久全部由服务端计算，不发烟花、不发自定义包，因此不绕过服务端限制。

- 需要 `glideClimbClearance`（默认 80 格）离地净空，不足时立刻退回普通滑翔；
- 每次俯冲最多下潜 `glideClimbDiveBlocks`（默认 70 格）；
- 前方 18 格内探测到方块、速度过低（失速）、或连续 3 次循环无净上升时自动放弃并退回普通滑翔；
- 低空和狭窄空间仍可能摔落/撞墙，这是技巧本身的限制，模组不能消除。

### 甲飞（`travel.elytraSwap`）

1.21.11 的服务端在 `glidingTicks % 20 == 0` 时对鞘翅扣 1 点耐久，而 `!isGliding()` 会把 `glidingTicks` 归零。
本功能在结算前把胸甲槽换成备用胸甲（或空槽），**等服务端同步「已停止滑翔」后**再换回鞘翅并发出
`START_FALL_FLYING`（玩家按跳跃时发的同一个包）。两次换装必须跨服务端 tick，同一 tick 内连发是无效的。

- 用的是 `clickSlot`（SWAP）与 `ClientCommandC2SPacket`，都是玩家手动能发出的正常交互包；
  不伪造位置/速度，服务端全程权威计算。
- 只使用快捷栏里的**备用胸甲**或**空槽**，尽量不动当前手持槽，也不移动背包其他物品。
- 保护：`elytraSwapClearance`（默认 24 格）低空不切；耐久只剩 1 点（`willBreakNextUse`）不切；
  `elytraSwapDurabilityLimit` 可设为「剩余耐久低于 N 才启用」；`elytraSwapMaxSwaps` 限制单轮次数；
  连续两次失败、超时未同步停飞/未重新展开时自动放弃并提示。
- 任何中断（GUI / 输入 / panic / 关开关）都会先确保鞘翅已装回。

### 风险，先说清楚

- 这是原版机制，但「自动执行高级技巧」在部分服务器算自动化违规；甲飞每秒两次换装是已知 exploit 特征，
  **可能被反作弊记录甚至封禁**。是否使用、在哪用由你自己决定，后果自负。
- 甲飞依赖客户端与服务端 tick 对齐，高延迟下可能只是白切或照样扣耐久；`elytraSwapLeadTicks` 是提前量容错，
  连续失败时功能会自行放弃。
- 收益很低：**耐久 III + 经验修补**通常已经解决问题，零风险、零操作，建议优先使用。
- 两个功能同时开启会互相干扰：甲飞每秒中断一次滑翔，会不断打断爬升的俯仰循环，建议只开一个。
- **自动飞行期间也会切甲**（`travel.elytraSwapDuringTravel`，默认开）：切走胸甲必然让 `isGliding()` 掉几 tick，
  Baritone 会把它当成「飞行结束」。所以 `travel` 在检测到鞘翅流程掉线时会**自动重新发起同一个目标**
  （最多 6 次、每次间隔 40 tick），航线不会永久停在半路；不想在飞行中切甲就把它关掉。

## 末地船自动搜刮（`endship`，默认开启）

在末地飞行时找船，**不能用潜影贝**：潜影贝只在末地城的地面生成，末地船上没有，
飞到高空时也早已超出实体同步距离。所以检测只看「只有末地船才有的东西」：

- 船头的**龙首**（`minecraft:dragon_head`）；
- 宝藏室里的**酿造台**（`minecraft:brewing_stand`）；
- 装着**鞘翅的展示框**（实体，兼容玩家手动放置的情况）。

实现方式：

- 只读已加载区块：半径 `scanRadiusChunks`（默认 8 区块 = 128 格），每次扫描 `chunksPerScan`（默认 3）列，
  每 `intervalTicks`（默认 2）tick 扫一次；同一区块 `rescanTicks` 内不重复读。
- 先用 `ChunkSection.hasAny(...)` 查调色板，只有命中才遍历 16³，开销很低。
- 命中后按 `dedupeRadius`（默认 48 格）合并成「一艘船」，位置从船头自动升级到鞘翅展示框，
  写入 `data.json` 并（可选）写一个名为「末地船」的 Xaero 路点，同时在 HUD 提示。
- 展示框变空（无论自动取还是你手动取）即视为「已搜」，不会重复转场。

自动搜刮流程（`endship.autoLoot`，默认开）：

1. **选船**：巡航模式下取「走廊内、沿飞行方向最靠前」的未搜末地船；`travel` 有其它目标时不动，避免和手动旅行打架。
2. **飞过去**：自动启用 `travel`，用 Baritone 鞘翅（`elytra`）飞向该船；没烟花会退化为纯滑翔。
3. **停飞接近**：水平距离 ≤ `approachRadius`（默认 32 格）时停飞，用 Baritone **步行寻路**走向宝藏室的展示框。
4. **取鞘翅**：进入 `takeRange`（默认 3 格，等于服务端交互上限）后攻击展示框；未变空会每 20 tick 重试。
5. **衔接**：展示框空了就算已搜，等 `chainDelayTicks`（默认 60 tick，留给拾取）后继续沿原方向飞。

### 直接落到船上（`endship.flyExact`，默认开）

之前飞到船附近会「落偏」，根因在 Baritone：

- `IElytraProcess.pathTo(GoalXZ)` 会把目标 Y **直接写成 64**（`ElytraProcess.pathTo` 字节码里的
  `bipush 64`），然后进入 `Path complete, searching for safe landing spot...` 的落点搜索。
  末地岛的地表通常就在 y=50~70，所以它很容易落在岛面或船底的地面上，而不是船上。
- `pathTo(GoalBlock)` 才会用你给的真实 Y。现在末地船的目标改成 `GoalBlock(船的 x, y, z)`，
  Baritone 会按船的高度规划，落点搜索也从船所在的位置开始，**基本会直接落在甲板/船顶**。
- 落点仍然由 Baritone 的安全落点算法决定：它不会「穿墙飞进宝藏室」，因为宝藏室是封闭的，
  只有船头一个小门。所以最后一小段仍然是落地后走进去，这也是原版里人肉操作的做法。
- 落地后如果就在船上，脚本会继续用步行寻路走向展示框；走不进去就转「手动」，你在甲板上
  往下挖一格/走门进去即可，3 格内仍会自动取下鞘翅。
- 想恢复旧行为（按 y=64 巡航后找落点）：`endship.flyExact = false`。

### 单向前进巡航（`endship.sweep`，默认开）

不开巡航时是「永远飞最近的未搜船」，会在两艘船之间来回掉头。开了巡航就变成一条直线扫过去：

- 以你当前所在位置为原点，按 `sweepBearing`（默认 90° = 正东 `+X`；0=北 `-Z`、180=南 `+Z`、270=西 `-X`）
  一直往前飞，每段 `sweepStep`（默认 256 格）重新规划一次。
- **只有落在走廊里的船才会去搜**：横向偏离航线 ≤ `sweepCorridor`（默认 96 格，即 192 格宽的带），
  且不在身后超过 `sweepBacktrack`（默认 48 格）。走廊外的船照样标记，但留到以后。
- 搜完一艘立刻回到原方向继续飞，不会掉头找上一艘。
- 换成纯滑翔（没有烟花）时，只要沿航线的进度到达计划距离就算一段结束，
  不会因为「高度没对上」而掉头绕圈。
- 连续 3 段几乎没有前进（比如站在地上没起飞）会自动暂停并提示先展开鞘翅。
- 手动用 `/chaoshelper travel goto ...` 指定目标时，巡航会让路，不会抢你的目的地。

改方向或开关巡航：

```
/chaoshelper ship dir east          # 正东（也可 north/south/west 或 0~360 的度数）
/chaoshelper ship sweep off         # 关掉巡航，回到「飞最近的未搜船」
/chaoshelper ship sweep on          # 重新按当前位置起算一条巡航线
```

ClickGUI → 末地船 里也有「巡航(朝一个方向飞)」「巡航方位」「每段距离」「走廊半宽」「允许回头」。

兜底：船悬空、步行路径不可达、或接近超时（`approachTimeoutTicks`，默认 600 tick）时会提示
「请手动飞进船内」，进入等待状态；你飞到展示框 3 格内仍会自动攻击。等待超过 `manualTimeoutTicks`
（默认 1200 tick）才暂停回待机。

- `endship.chainNext` 关掉就只搜一艘，取完停下；重新打开或重开模块会继续。
- 用 `/chaoshelper travel goto ship` 或 `/chaoshelper ship list` 手动查看/前往。
- HUD 加一个「末地船」元素即可随时看到方位（`{dir}`）与 `{looted}` 状态，便于在高空转向。
- `autotake` 模块不再是必需项：搜刮流程自己会取；它仍然可以单独用来取普通展示框里的鞘翅。

## 自动退服（`autolog`，默认关闭）

血量（含伤害吸收）≤ `healthThreshold`（默认 6 = 3 颗心）**且**背包里（主背包、快捷栏、护甲、副手）
一个不死图腾都没有时，直接 `MinecraftClient.disconnect`，等同于自己点「断开连接」——

- 只在多人服务器生效（`onlyMultiplayer`，默认 `true`），单人不会触发；
- 每 `checkIntervalTicks`（默认 5 tick）检查一次，死亡后不再触发，回到标题界面后自动重新武装；
- 判定使用 `player.getHealth() + getAbsorptionAmount()`，不会误判「有图腾但还没触发」的情况；
- 想更保守就把阈值调高（例如 10），想更激进就调低（例如 4）。**退服不等于无敌**：
  从掉血到断开之间仍有 1~2 tick 的窗口，摔落/虚空/瞬间爆发伤害仍可能先判定死亡。

## Xaero 小地图集成（Xaero's Minimap / XaeroPlus）

已按本机 Xaero 26.6.0 的真实落盘格式实现，未凭猜测：

```
xaero/minimap/<世界>/dim%0|dim%-1|dim%1/
  waypoints.txt          传统单文件
  mw$<集合>.txt          多世界集合文件
waypoint:名称:缩写:x:y:z:颜色:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination
```

- 维度目录：`dim%0` 主世界、`dim%-1` 下界、`dim%1` 末地、`dim%<ns>$<path>` 自定义维度。
- 读取：合并 `waypoints.txt` 与所有 `mw$*.txt`，供 `travel goto waypoint <名称>` 导航。
- 写入：**优先调用 Xaero 的内存 API**（`XaeroMinimapSession → MinimapSession → MinimapWorld →
  WaypointSet.add`），由 Xaero 自己持久化，标记立即生效，运行期也不会被覆盖。
- 回退：API 不可用（Xaero 版本变化 / 尚未进入世界）时退回文件写入 —— 新建 `mw$chaoshelper_1.txt`
  （独立集合），没有 `mw$` 文件时追加到 `waypoints.txt`，首次写入前生成 `.chaoshelper.bak`。
- 末地自动标记：每 40 tick 扫描潜影贝标记「末地城」；末地船由 `endship` 模块单独扫描
  （龙首/酿造台/鞘翅展示框），两者互不重复。
- **限制**：API 是反射调用的 Xaero 内部接口，Xaero 更新后可能失效；失效时 `/chaoshelper xaero status`
  会显示「文件」模式。文件模式下，Xaero 运行期缓存路点，通常要重进世界或重启才会显示，建议先退出游戏再写。

## 配置文件

目录：`.minecraft/config/chaoshelper/`

| 文件 | 内容 |
| --- | --- |
| `config.json` | 全部设置（模块开关、按键、各功能参数） |
| `data.json` | 队友名单、路点、记录点、本地目标 |
| `modules/*.jar` | 动态模块 |

写盘只发生在这些文件里；模块不允许各自写文件。缓存、血量估算、掉落物列表、进出提示只存在内存中。

### 配置键与默认值

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `cacheIntervalTicks` | 5 | 实体缓存刷新间隔（tick） |
| `errorDisplaySeconds` | 120 | HUD 错误保留秒数 |
| `notificationSeconds` | 45 | 普通提示保留秒数 |
| `hud.margin` | 4 | 自动布局距屏幕边缘 |
| `hud.elements` | 自动生成 | HUD 元素数组：类型/模板/位置/样式，由编辑器维护 |
| `hud.customLayout` | false | 首次生成默认元素后置 true；设为 false 可重建默认布局 |
| `hud.maxEntries` / `background` / `show*` | — | 仅用于首次生成默认元素，之后由元素自身控制 |
| `esp.range` | 32 | ESP 距离（格） |
| `esp.throughWalls` | false | 穿墙 |
| `esp.showLabels` | true | 名称/距离标签 |
| `esp.maxEntities` | 128 | 单帧最多绘制实体 |
| `esp.lineWidth` | 1.5 | 线宽 |
| `chams.range` | 32 | Chams 距离 |
| `chams.throughWalls` | false | 描边穿墙 |
| `chams.opacity` | 190 | 透明度 0-255 |
| `playerTracker.range` | 64 | 追踪距离上限 |
| `playerTracker.refreshTicks` | 10 | 数据刷新间隔 |
| `playerTracker.staleSeconds` | 15 | 离开后多久从列表移除 |
| `playerTracker.estimateResetSeconds` | 30 | 离开超过该秒数后血量重置为满血估算 |
| `playerTracker.includeTabListOnly` | true | 显示仅 TAB 列表可见的玩家 |
| `playerTracker.maxEntries` | 100 | 追踪列表硬上限（HUD 折叠由元素自身的行数上限控制） |
| `dropTracker.range` | 32 | 掉落物记录距离 |
| `dropTracker.recordSeconds` | 60 | 消失后保留秒数 |
| `dropTracker.maxEntries` | 100 | 掉落物列表硬上限 |
| `record.maxEntries` | 50 | 记录点上限 |
| `record.autoDeath` | true | 自动记录死亡点 |
| `record.autoLog` | true | 聊天/系统消息记为本地记录 |
| `record.autoLogoff` | true | 玩家离开视野时记录最后坐标 |
| `autoEat.threshold` | 18 | 进食饥饿阈值 |
| `autoEat.cooldownTicks` | 40 | 失败冷却 |
| `autoEat.restoreSlot` | true | 进食后恢复槽位 |
| `travel.mode` | auto | auto / walk / elytra |
| `travel.glide` | false | 滑翔模式：只调视角，不消耗烟花 |
| `travel.cancelOnInput` | false | 是否恢复旧行为：玩家移动/攻击输入时取消旅行 |
| `travel.cancelOnScreen` | false | 是否恢复旧行为：打开界面时取消旅行 |
| `travel.maxDistance` | 0 | 目标距离上限，0 = 不限 |
| `travel.minY` | 64 | 鞘翅起飞安全高度下限 |
| `travel.arriveRadius` | 3 | 到达判定距离 |
| `autotake.range` | 3 | 自动取鞘翅的展示框搜索范围（格） |
| `autotake.onlyWhenMissing` | false | 已穿鞘翅时跳过（默认否，方便收集备用鞘翅） |
| `autotake.cooldownTicks` | 20 | 取鞘翅后的冷却（tick） |
| `endship.notify` | true | 发现末地船时提示 |
| `endship.markXaero` | true | 发现时写入 Xaero 路点 |
| `endship.onlyEnd` | true | 只在末地扫描，避免误判玩家放置的龙首 |
| `endship.scanRadiusChunks` | 8 | 扫描半径（区块） |
| `endship.chunksPerScan` | 3 | 每次扫描的区块数 |
| `endship.intervalTicks` | 2 | 扫描间隔（tick） |
| `endship.dedupeRadius` | 48 | 同一艘船的信号合并半径（格） |
| `endship.autoLoot` | true | 自动搜刮：飞过去 → 走到展示框 → 取鞘翅 → 下一艘 |
| `endship.approachRadius` | 32 | 距船多少格停飞并开始步行接近 |
| `endship.takeRange` | 3 | 攻击展示框的距离（服务端上限 3 格） |
| `endship.approachTimeoutTicks` | 600 | 步行接近超时（tick），超时转手动 |
| `endship.manualTimeoutTicks` | 1200 | 等待手动飞入船内的超时（tick） |
| `endship.chainNext` | true | 取走鞘翅后自动飞往下一艘未搜的船 |
| `endship.chainDelayTicks` | 60 | 转场前等待，留给拾取鞘翅 |
| `endship.chainRange` | 0 | 转场距离上限，0 = 不限 |
| `autolog.healthThreshold` | 6 | 血量（含伤害吸收）低于该值且无图腾时退服 |
| `autolog.requireNoTotem` | true | 只在背包没有不死图腾时退服 |
| `autolog.onlyMultiplayer` | true | 只在多人服务器生效 |
| `autolog.checkIntervalTicks` | 5 | 血量检查间隔（tick） |
| `travel.glideClimb` | false | 无动力爬升：周期性俯仰循环，不用烟花保持高度 |
| `travel.glideClimbClearance` | 80 | 爬升要求的最低离地净空（格） |
| `travel.glideClimbDiveBlocks` | 70 | 每个循环允许下潜的格数 |
| `travel.elytraSwap` | false | 甲飞：结算前切走胸甲重置耐久计时 |
| `travel.elytraSwapLeadTicks` | 2 | 甲飞提前触发的 tick 数 |
| `travel.elytraSwapDurabilityLimit` | 0 | 0=总是启用；>0 表示剩余耐久低于该值才启用 |
| `travel.elytraSwapMaxSwaps` | 0 | 单轮最多切换次数，0=不限 |
| `travel.elytraSwapClearance` | 24 | 甲飞要求的最低离地净空（格） |
| `travel.elytraSwapDuringTravel` | true | 自动飞行期间也切甲；掉线后自动重新发起航线 |
| `xaero.enabled` | true | 启用 Xaero 路点集成 |
| `xaero.autoMarkEnd` | true | 末地自动标记末地城/鞘翅 |
| `xaero.setName` | chaoshelper | 写入 Xaero 的集合名（新目录） |
| `xaero.allowWrite` | true | 允许写入 Xaero 文件 |
| `baritone.enabled` | true | 是否允许调用 Baritone |
| `dynamic.enabled` | true | 是否加载动态模块 |
| `dynamic.maxModules` | 16 | 动态模块数量上限 |

完整列表（含 ARGB 颜色键）可用 `/chaoshelper config` 查看。

---

## 血量标注规则

- **可见时**：使用客户端实体同步值，标注 `客户端同步 / 高`。
- **离开视野后**：保留最后值，标注 `最后可见 / 可能过期`，误差随时间增长。
- **离开超过 `playerTracker.estimateResetSeconds`（默认 30s）**：重置为满血，标注 `估算 / 低`。
- **只在 TAB 列表出现、从未进入视野**：满血估算，明确标注为估算。
- 任何情况下都不会从伤害包反推血量，也不绕过服务端隐藏血量。

---

## 动态模块 API

主 jar 会把 `com.chaoshelper.api.**` 单独打成 `chaoshelper-<version>-api.jar`（classifier `api`），
动态模块只针对这个 jar 编译。

### 目录

`config/chaoshelper/modules/*.jar`，启动时加载，`/chaoshelper reloadmodules` 热重载。

### jar 内必须包含

- `META-INF/chaoshelper-module.json`
- 实现了 `com.chaoshelper.api.ChaosModule` 的入口类

```json
{
  "schema": 1,
  "id": "my-autoeat",
  "name": "My AutoEat",
  "version": "1.0.0",
  "apiVersion": 1,
  "entrypoint": "com.example.MyAutoEat",
  "description": "示例",
  "defaultEnabled": false,
  "behavior": true,
  "overrides": "autoeat",
  "settings": { "threshold": "18" }
}
```

- `schema`：元数据格式版本，目前只支持 `1`
- `id`：2-32 位小写字母/数字/`_`/`-`，字母开头
- `apiVersion`：必须等于 `ChaosApi.API_VERSION`（当前 `1`）
- `behavior`：`true` 表示会执行动作，`panic` 会将其关闭
- `overrides`：可选，填写内置模块 id 以覆盖它（仅 `overridable` 的模块允许，见下）
- `settings`：`context.setting(key, default)` 的默认值

### 访问能力（`ChaosContext`）

只读：`self()`、`entities()`、`players()`、`drops()`、`tick()`、`moduleId()`、`apiVersion()`。
动作：`addRenderBox(...)`（世界空间 3D 框，经宿主渲染）、`notify(...)`、`error(...)`、
`isModuleEnabled(id)`、`disableModule(id)`。

### 硬性限制

- **禁止**包含 `net/minecraft`、`net/fabricmc`、`org/spongepowered`、`com/mojang`、`com/chaoshelper`（api 除外）类；
  类加载器会直接抛 `ClassNotFoundException`。
- **禁止**包含 `fabric.mod.json`、Mixin 配置、`assets/`、任何非 `.class` 资源；加载器会拒绝整个 jar。
- 动态模块不能碰 Fabric API、Mixin 或资源，一切都经 `ChaosContext` 桥接。
- 单个模块 tick 抛异常只会禁用该模块，并在 HUD 红框与日志中报告。
- **可被覆盖**（`overridable`）：`autoeat`、`player-tracker`、`drop-tracker`、`record`。
- **永远在主 jar**：`esp`、`chams`、`hud`、`travel`（Mixin / Baritone 依赖）。

---

## 已知限制

- 仅客户端可见数据：目标不在加载范围内时只能给出最后可见位置，可能已过期。
- 护甲值按可见装备计算，**不含附魔与药水效果**，HUD 中已标注。
- Chams 使用 `LivingEntityRenderer#updateRenderState` 与 `getMixColor`，只影响客户端渲染。
- ESP 关闭后不保留任何渲染状态；动态模块的框每 tick 清空重填。
- Baritone 版本不匹配时只标记不可用，不会崩溃，也不会自动下载；接口签名以 1.17.0 为准，
  其他 1.17.x 小版本若改动 API 会在 HUD 显示「初始化失败/鞘翅 API 不可用」而不是崩溃。
- Baritone 只在进入世界后才探测（主菜单阶段 provider 尚未创建），失败会每 5 秒重试。
- 网络受限环境下 Gradle wrapper 指向腾讯镜像；如需官方源请自行修改 `gradle/wrapper/gradle-wrapper.properties`。
- 服务器若隐藏血量（插件伪造），客户端拿到的就是伪造值，本模组不会也不能修正。
- **种子**：单人世界直接读内置服务端的 `getSeed()`，是精确值不是推测；多人服务器不下发种子，
  客户端拿不到，`/chaoshelper seed export` 只导出生物群系采样与路点证据，交给 SeedCracker
  一类外部工具。本模组不做（也无法做）多人种子的穷举反解。
- 掉落物：消失时若玩家在 3 格内视为已捡起并立即移除；否则保留 `dropTracker.recordSeconds` 秒并标"已消失"。
