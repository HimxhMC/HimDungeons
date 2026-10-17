# HimDungeons

> 让房间自己决定怎么拼 —— 任意尺寸、任意形状、任意门位、原生三维。
>
> Let the rooms decide how they connect — any size, any shape, any door position, native 3D.

[![Paper](https://img.shields.io/badge/Paper-1.21%2B-4B8BBE)]()
[![Java](https://img.shields.io/badge/Java-25%2B-orange)]()
[![WorldEdit](https://img.shields.io/badge/WorldEdit-NOT%20required-brightgreen)]()
[![Version](https://img.shields.io/badge/version-1.0-yellow)]()

---

## 为什么选 HimDungeons？· Why HimDungeons?

传统地牢插件要求你把房间做成尺寸统一、网格对齐、门在固定位置的模块，否则拼不起来。你花几个小时搭出一个漂亮的 L 形房间，结果发现门必须在北墙正中央——形状一改，图纸就废了。
Most dungeon plugins demand that you pre-build rooms in uniform sizes, grid-aligned, with doors in fixed positions, or they simply won't connect. You spend hours crafting a beautiful L-shaped room, only to discover that doors must be centered on the north wall — change the shape, and your blueprint is dead.

HimDungeons 的思路完全不同：房间自己声明门在哪里、朝向哪里，算法负责找到两个房间的正确旋转和偏移把它们拼起来。你只要搭你想搭的，剩下的交给生成器。
HimDungeons takes a fundamentally different approach: each room declares where its doors are and which way they face, and the algorithm figures out the correct rotation and offset to stitch any two rooms together. You build what you want — the generator handles the rest.

| 能力 / Capability | 传统地牢插件 / Typical Plugins | **HimDungeons** |
|---|---|---|
| 房间尺寸 / Room size | 必须统一 / Must be uniform | 任意 / Any |
| 房间形状 / Room shape | 多为矩形网格 / Mostly rectangular | 任意 / Any |
| 门的位置 / Door position | 固定墙面 / Fixed walls | 任意坐标 / Any coordinate |
| 门的大小 / Door size | 1×1 或固定 / 1×1 or fixed | 任意 / Any |
| 垂直拼接 / Vertical stacking | 极少支持 / Rarely | 原生三维 / Native 3D |
| 房间文件夹 / Room folders | 平铺强制前缀 / Flat with prefixes | 任意子目录 / Any subdirectory |
| WorldEdit | 强依赖 / Hard dependency | 无依赖 / None |
| 异步加载 / Async loading | 常卡主线程 / Often blocks main thread | 分刻无卡顿 / Tick-batched |

---

## 核心算法：门锚点双向匹配 · Core Algorithm: Bidirectional Door Anchoring

这是整个插件的地基。理解它，就理解了 HimDungeons 为什么能做到「房间想怎么搭就怎么搭」。
This is the foundation of the entire plugin. Understand it, and you'll see why HimDungeons can offer "build any room you want."

### 传统插件怎么做 · How typical plugins do it

大多数地牢插件用「网格 + 固定方向」：房间必须占据整数格，门必须开在四条边的中点，所有房间朝北。生成时按格子摆放，天然不会穿模，但也天然杜绝了任何非标准形状。
Most dungeon plugins use a grid plus fixed orientation: rooms must occupy integer cells, doors must sit on edge midpoints, all rooms face north. Generation is trivially collision-free — but it also makes any non-standard shape impossible.

### HimDungeons 怎么做 · How HimDungeons does it

每个房间在 `room_door.yml` 里声明自己的门，包括相对坐标、朝向、类型和权重。生成时，算法取当前房间的空门 A（世界坐标 P1、朝向 D1），尝试把候选房间 B 的每一扇门（P2、D2）放上去，并尝试 4 种旋转。一对门能拼上的充要条件是一个双向等式。
Each room declares its doors in `room_door.yml`, including relative coordinates, facing, type, and weight. During generation, the algorithm takes the current room's available door A (world position P1, facing D1), tries to place each candidate door (P2, D2) of room B, and tests all four rotations. The necessary and sufficient condition for a pair of doors to connect is a bidirectional equation.

```
P1 + D1 == P2  且  P2 + D2 == P1
```

这个等式的含义是：A 的门向门外走一格，正好落在 B 的门上；B 的门向门外走一格，正好落在 A 的门上。数学上意味着两扇门面对面、紧贴、朝向相反。任何尺寸、任何形状、任何门位置的房间，只要满足这一条就能拼。
What this means is: walking one block out from door A lands exactly on door B, and walking one block out from door B lands exactly on door A. Mathematically, the two doors face each other, touch, and point in opposite directions. Any room — any size, any shape, any door position — connects as long as this holds.

### 带来什么 · What you get

房间尺寸可以从 3×3 到 50×50 任意混用，小储藏室和大 Boss 厅可以紧挨着，只要门能对上。
Room sizes can range freely from 3×3 to 50×50 — a small storage room can sit right next to a giant boss hall, as long as their doors line up.

房间形状完全自由，L 形、十字形、环形、不规则多边形都可以，只要在门文件里标出门的坐标和朝向。
Room shapes are completely free — L-shaped, cross-shaped, ring-shaped, irregular polygons all work, as long as door coordinates and facings are declared in the door file.

门的位置任意，不用在墙的正中央，开在角落也行；门的大小任意，1×1 的门和 3×3 的拱门可以互通，因为算法只关心「坐标 + 朝向」，不关心门的几何尺寸。
Door positions are arbitrary — not required to be centered on a wall; corners are fine. Door sizes are arbitrary too: a 1×1 door and a 3×3 arch can connect, because the algorithm only cares about coordinate and facing, not the geometric size of the opening.

四种旋转全自动：候选房间会被自动旋转 0°、90°、180°、270°，直到门对得上，你不需要为每个方向准备一套原理图。AABB 碰撞检测保证不穿模。
All four rotations are automatic: candidate rooms are rotated 0°, 90°, 180°, and 270° until the doors match, so you never need a separate schematic per orientation. AABB collision detection guarantees no clipping.

### 怎么自定义 · How to customize

用木镐（创造 + OP）框选房间，在 GUI 里点「保存新房间」，然后站在每个门前面看向门外，点「添加门锚点」，输入朝向、可选类型和可选权重。
Select the room with a wooden pickaxe (creative + OP), click "Save New Room" in the GUI, then stand in front of each door looking outward, click "Add Door Anchor", and enter the facing plus an optional type and weight.

门还可以通过 `type` 字段做类型门控：楼梯门只接楼梯门，大拱门只接大拱门，`fallback` 门只接 `fallback` 门。这让你的地牢可以做出「必须走楼梯」或「大门后面必然是 Boss 厅」这样的语义约束。
Doors also support type gating via the `type` field: stair doors only connect to stair doors, grand arches only to grand arches, fallback doors only to fallback doors. This lets you enforce semantic constraints like "stairs must lead down" or "a grand arch always leads to a boss hall."

---

## 原生三维拼接 · Native 3D Stitching

### 传统插件怎么做 · How typical plugins do it

绝大多数地牢插件是二维的，房间只在 XZ 平面上拼接，垂直方向永远是「楼层换世界」或者干脆不支持。想做一个越往下越深的地牢，只能手动拼或者开多个世界。
Most dungeon plugins are 2D — rooms only stitch on the XZ plane; vertical movement means "another world" or nothing at all. Want a descending dungeon? You either hand-build it or spawn multiple worlds.

### HimDungeons 怎么做 · How HimDungeons does it

门锚点的 `facing` 字段支持 `y+`（向上）和 `y-`（向下）。当一扇 `y-` 门接上另一扇 `y+` 门，算法会自动把新房间的锚点放到当前房间的下方，形成真正的垂直叠层。
The door anchor's `facing` field supports `y+` (up) and `y-` (down). When a `y-` door connects to a `y+` door, the algorithm places the new room's anchor below the current one, forming a genuine vertical stack.

### 带来什么 · What you get

你可以做多层地牢：地表入口、地下墓穴、深处巢穴，一套原理图搞定；也可以做塔楼，向上螺旋攀升，每层一个 `y+` 门；还可以做垂直难度梯度，越往下 Boss 越强，通过 `structureChoose` 的权重控制。
You can build multi-layer dungeons — surface entrance, underground crypt, deep lair — with one schematic set. You can build towers that spiral upward, one `y+` door per floor. You can build vertical difficulty gradients where the boss gets stronger the deeper you go, controlled by `structureChoose` weights.

### 怎么自定义 · How to customize

在门锚点里写 `facing: "y-"` 或 `facing: "y+"` 即可。上下门自动匹配时不做绕 Y 轴旋转的变换，所以上下门只需准备一套原理图。
Just write `facing: "y-"` or `facing: "y+"` in the door anchor. Vertical doors are invariant under Y-axis rotation, so you only need one schematic for up/down connections.

---

## 文件自由组织 · Free File Organization

### 传统插件怎么做 · How typical plugins do it

房间文件夹强制平铺，命名必须带前缀，一旦有 200 个房间，文件夹会变成一团乱麻。
Room folders are usually flat and filenames must carry prefixes; with 200 rooms the folder becomes a mess.

### HimDungeons 怎么做 · How HimDungeons does it

`ResourceUtil.scanRooms()` 递归扫描 `rooms/` 下的所有子目录，房间放在任意层级都可以，路径本身作为房间标识。
`ResourceUtil.scanRooms()` recursively scans every subdirectory under `rooms/`. Rooms can live at any depth, and the path itself serves as the unique room identifier.

```
plugins/HimDungeons/my_dungeon/rooms/
├── chapter1/
│   ├── start_entrance.schem
│   ├── combat/
│   │   ├── sword_fight.schem
│   │   └── ambush.schem
│   └── boss_chapter1.schem
├── chapter2/
│   └── ...
└── fallback/
    └── sealed_wall.schem
```

`chapter1/combat/sword_fight` 就是它在配置里的唯一名字。你可以按章节、按难度、按主题、按作者随意分类。
`chapter1/combat/sword_fight` is its unique name in configuration. Organize by chapter, difficulty, theme, or author — your choice.

### 带来什么 · What you get

大型地牢不混乱，一个地牢几百个房间也能保持清爽；跨项目复用方便，可以把一套 `fallback/` 复制到别的项目用；版本控制友好，Git 里每个章节一个文件夹，PR 审核清晰。
Large dungeons stay clean — hundreds of rooms remain manageable. Cross-project reuse is easy — copy a `fallback/` set into another project. Version control is friendly — one folder per chapter, clean PR reviews.

### 怎么自定义 · How to customize

直接在 GUI 里输入路径，比如 `chapter1/combat/sword_fight`，算法会自动创建中间目录。
Just enter the path in the GUI, e.g. `chapter1/combat/sword_fight`; intermediate directories are created automatically.

---

## 无 WorldEdit 依赖 · No WorldEdit Required

### 传统插件怎么做 · How typical plugins do it

几乎所有地牢插件都强依赖 WorldEdit 来做原理图读写。服务端升级 WorldEdit 版本不兼容时插件直接崩，WorldEdit 自己又依赖 FAWE，加载链越来越长，中小服务器管理员为了一个地牢插件不得不装整个 WorldEdit。
Nearly all dungeon plugins hard-depend on WorldEdit for schematic IO. A server upgrade with an incompatible WorldEdit version breaks the plugin; WorldEdit itself may pull in FAWE; small-server admins end up installing the entire WorldEdit stack just for a dungeon plugin.

### HimDungeons 怎么做 · How HimDungeons does it

插件内置 SpongeSchematicReader 和 SpongeSchematicWriter（基于 Querz NBT 库），直接读写 Sponge Schematic v2 和 v3；方块旋转用 Bukkit 原生的 `BlockData.rotate(StructureRotation)`；选区工具用木镐加 OP 加创造模式。整套流程零额外依赖。
The plugin ships with SpongeSchematicReader and SpongeSchematicWriter (built on the Querz NBT library) that read and write Sponge Schematic v2 and v3 directly; block rotation uses Bukkit's native `BlockData.rotate(StructureRotation)`; region selection uses a wooden pickaxe plus OP plus creative mode. The entire flow has zero extra dependencies.

### 带来什么 · What you get

插件体积约 3.5MB，远小于 WorldEdit；升级服务端不担心 WorldEdit 跟进；两者互不拖累；原理图格式在全局配置里可选。
The plugin weighs about 3.5MB, far smaller than WorldEdit; server upgrades no longer wait on WorldEdit; neither plugin can drag the other down; the schematic format is configurable globally.

### 怎么自定义 · How to customize

```yaml
# plugins/HimDungeons/config.yml
schematic:
  readFormat: auto        # auto | sponge_v2 | sponge_v3
  saveFormat: sponge_v3   # sponge_v2 | sponge_v3
```

如果你从 WorldEdit 迁移，把 WorldEdit 保存的 `.schem` 直接丢进 `rooms/` 就能读，因为 WorldEdit 默认也是 Sponge v2 或 v3。
If migrating from WorldEdit, just drop the `.schem` files saved by WorldEdit into `rooms/` — they read directly, because WorldEdit saves in Sponge v2 or v3 by default.

---

## 异步 IO 与分刻粘贴 · Async IO and Tick-Batched Pasting

### 传统插件怎么做 · How typical plugins do it

WorldEdit 的粘贴是同步的。一个 30×20×30 的房间在低配服务器上会卡 100 到 500 毫秒，多个玩家同时进地牢会让 TPS 掉到个位数。
WorldEdit pasting is synchronous. A 30×20×30 room can cause a 100–500ms stall on low-end servers, and multiple players entering dungeons simultaneously can push TPS into single digits.

### HimDungeons 怎么做 · How HimDungeons does it

`AsyncPaster` 把流程拆成三步：异步读取并解析 `.schem`，异步预计算所有方块的坐标和旋转后的方块数据，然后回到主线程分刻批量提交，每 tick 只放置一批方块后让出主线程。
`AsyncPaster` splits the process into three steps: read and parse the `.schem` asynchronously, precompute every block's coordinates and rotated block data asynchronously, then return to the main thread and submit in tick-sized batches, placing one batch per tick before yielding.

```java
// AsyncPaster.java 里可调 / Tunable in AsyncPaster.java
private static final int BLOCKS_PER_TICK = 2000;
```

### 带来什么 · What you get

粘贴一个巨型房间期间服务器照常运行，TPS 不掉；批量大小可调，高配服务器可以调到 5000 到 10000，低配调到 500；保存也异步，主线程读方块，异步线程写文件。
The server keeps running normally while a giant room is pasted — TPS stays steady. The batch size is tunable: high-end servers can go to 5000–10000, low-end to 500. Saving is asynchronous too: the main thread reads blocks, the async thread writes the file.

### 怎么自定义 · How to customize

直接改 `AsyncPaster.BLOCKS_PER_TICK`。如果要让它可配置，在 `config.yml` 里加 `schematic.blocksPerTick`，然后在 `AsyncPaster` 初始化时读进来即可。
Edit `AsyncPaster.BLOCKS_PER_TICK` directly. To make it configurable, add `schematic.blocksPerTick` to `config.yml` and read it in AsyncPaster's initializer.

---

## 完整生命周期与崩溃安全 · Complete Lifecycle and Crash Safety

### 传统插件怎么做 · How typical plugins do it

大部分地牢插件只关心生成、传送、通关，不管玩家离线、崩溃、服务器被强杀。结果是玩家在地牢里断线背包丢了，服务器硬崩后玩家背包卡在某个不确定状态，管理员只能事后道歉。
Most dungeon plugins only care about generate, teleport, and complete — they ignore disconnects, crashes, and hard kills. The result: players lose inventories on disconnect, inventories get stuck in undefined states after hard crashes, and admins can only apologize afterwards.

### HimDungeons 怎么做 · How HimDungeons does it

引入 `DungeonSessionService` 作为唯一状态机入口，所有玩家进出路径都经过它。背包备份存在 SQLite 里，使用 WAL 模式加 `synchronous=NORMAL`，进程被强杀也不会丢已提交事务。胜利次数存在独立的 YAML 里，恢复背包绝不会影响胜利统计。
Introducing `DungeonSessionService` as the single state-machine entry point — all player transitions pass through it. Inventory backups live in SQLite with WAL mode plus `synchronous=NORMAL`, so a hard kill never loses committed transactions. Win counts live in a separate YAML file, so restoring an inventory can never affect win stats.

```
[主世界] ──enter──► [地牢中] ──finish──► [主世界]（恢复背包 + 发奖 + wins++）
                       │
                       ├── death ──► [地牢中]（重生到存档点或出生点，不触发背包操作）
                       ├── /dg leave ──► [主世界]（恢复背包 + 5 分钟清理倒计时）
                       ├── 离线 ──► [离线]（立即恢复背包，保留 5 分钟可重入窗口）
                       └── 重连 ──► 小于 5 分钟重入，大于等于 5 分钟停留在主世界
```

```
[Overworld] ──enter──► [In Dungeon] ──finish──► [Overworld] (restore inventory + rewards + wins++)
                          │
                          ├── death ──► [In Dungeon] (respawn at checkpoint or spawn, no inventory op)
                          ├── /dg leave ──► [Overworld] (restore inventory + 5-min cleanup timer)
                          ├── quit ──► [Offline] (restore inventory immediately, 5-min rejoin window)
                          └── join ──► less than 5 min: re-enter; 5 min or more: stay in overworld
```

### 带来什么 · What you get

玩家断线背包不丢，离线瞬间就恢复；服务器硬崩能恢复，启动时扫描所有活动行标记为待恢复，玩家登录时自动恢复；有 5 分钟重连窗口，玩家断线后 5 分钟内回来地牢世界还在；死亡逻辑尊重原版，`keepInventory` 决定死亡掉不掉落，插件只负责重生位置。
Players never lose inventories on disconnect — they're restored the instant the player quits. Hard crashes recover too: on startup every active row is marked for restore, and player join triggers automatic recovery. A 5-minute reconnect window keeps the dungeon world alive if the player returns quickly. Death logic respects vanilla: `keepInventory` decides drops, and the plugin only picks the respawn location.

### 怎么自定义 · How to customize

```yaml
dungeonInstance:
  keepInventory: false       # 地牢内死亡是否掉落 / Whether death drops items in dungeon
  deepCloneInventory: false  # 是否备份背包并在离开时恢复 / Whether to back up and restore inventory
```

两者相互独立。只想要地牢内不掉落就开 `keepInventory`；只想要进出地牢背包隔离就开 `deepCloneInventory`；两个都开也不冲突。
The two are independent. Want "no drops in dungeon"? Enable `keepInventory`. Want "inventory isolation on enter/exit"? Enable `deepCloneInventory`. Both can coexist without conflict.

---

## 其他亮点 · Other Highlights

AABB 旋转碰撞检测会正确处理旋转后向负方向延伸的情况，保证房间不重叠。
AABB rotation-aware collision detection correctly handles rotated boxes that extend in negative directions, guaranteeing rooms never overlap.

连通性 BFS 检查可选开启，生成后验证所有房间都能从起点走到，防止死锁。Fallback 空门封堵让没用完的门自动接上单门房间，封死漏风的空门。
Optional BFS connectivity checks verify every room is reachable from the start after generation, preventing deadlocks. Fallback door sealing auto-connects unused doors to single-door rooms, sealing any "air leak."

房间模板复用控制和门模板复用控制可以强制每个房间只用一次，做出线性关卡。游戏内管理 GUI 让房间、门、箱子、出生点、存档点、配置项全部可视化编辑，无需手写 YAML。批量配置编辑可以一次修改所有地牢的某个配置项。
Template reuse control for rooms and doors lets you force linear progression where each room is used once. The in-game admin GUI makes rooms, doors, chests, spawns, checkpoints, and config values fully visual — no YAML editing required. Batch config editing modifies a given key across all dungeons at once.

软依赖全部可选：Vault、PlaceholderAPI、Parties、Slimefun、MythicMobs 都能缺席，插件优雅降级。
All soft dependencies are optional: Vault, PlaceholderAPI, Parties, Slimefun, and MythicMobs can all be absent, and the plugin degrades gracefully.

---

## 快速开始 · Quick Start

### 环境要求 · Requirements

需要 Paper 1.20 以上（推荐 1.21.3 以上）、Java 21 以上，内存至少 2GB，每个活跃地牢世界额外占用约 50MB。
Requires Paper 1.20+ (1.21.3+ recommended), Java 21+, at least 2GB RAM, and roughly 50MB extra per active dungeon world.

### 安装 · Install

把 jar 丢进 `plugins/`，启动服务器一次自动释放示例地牢，然后执行 `/dg start example_dungeon` 试跑。
Drop the jar into `plugins/`, start the server once to extract the sample dungeon, then run `/dg start example_dungeon` to try it.

### 创建你自己的房间 · Build your own room

用木镐（创造 + OP）左键点 1 号点、右键点 2 号点；`/dg edit <地牢名>` 点「保存新房间」输入路径；站在门前面看向门外点「添加门锚点」输入朝向；原理图和门文件会自动生成。
Use a wooden pickaxe (creative + OP) to left-click point 1 and right-click point 2. Run `/dg edit <dungeon>`, click "Save New Room", and enter a path. Stand in front of each door looking outward, click "Add Door Anchor", and enter the facing. The schematic and door files are generated automatically.

### 命令一览 · Commands

| 命令 / Command | 权限 / Permission | 说明 / Description |
|---|---|---|
| `/dg start [地牢名] [模式]` | `himdungeons.start` | 启动地牢 / Start a dungeon |
| `/dg leave` | `himdungeons.leave` | 离开地牢 / Leave current dungeon |
| `/dg edit [地牢名]` | `himdungeons.admin` | 打开管理 GUI / Open admin GUI |
| `/dg create <地牢名>` | `himdungeons.admin` | 创建地牢目录 / Create dungeon folder |
| `/dg reload` | `himdungeons.admin` | 重载配置 / Reload config |

三种加入模式分别是 single（单人）、multi（附近 3 格内潜行玩家）、party（需 Parties 插件）。
The three join modes are single, multi (nearby sneaking players within 3 blocks), and party (requires the Parties plugin).

---

## 配置文件示例 · Configuration Sample

```yaml
dungeonInstance:
  minRoomAmount: 5
  maxRoomAmount: 12
  dfsMaxDeep: 8
  startToBossRoomAmmount: "10 10"
  allowRoomReuse: false
  allowDoorReuse: false
  useAabb: true
  useBfsCheck: false
  disconnectedRoomDistance: 0
  keepInventory: false
  deepCloneInventory: false
  time: 0
  dungeonTime: 1200
  weather: clear
  difficulty: normal
  lobby: "world,0,64,0,0,0"
  bossType: ["zombie"]
  bossNbt: ["{Health:100.0f}"]
  rewards:
    vault: {}
    playerPoints: {}
    consoleCommands: []
```

`startToBossRoomAmmount` 控制 start 到 boss 主干房间数范围，`useAabb` 控制碰撞检测，`keepInventory` 和 `deepCloneInventory` 相互独立。
`startToBossRoomAmmount` controls the trunk room count range from start to boss, `useAabb` toggles collision detection, and `keepInventory` and `deepCloneInventory` are independent.

---

## 数据存储 · Data Storage

| 数据 / Data | 位置 / Location | 格式 / Format |
|---|---|---|
| 胜利次数 / Wins | `plugins/HimDungeons/<地牢>/stats.yml` | YAML（可读、可手改 / readable, editable） |
| 背包备份 / Backups | `plugins/HimDungeons/backup.db` | SQLite（WAL） |

服务器启动时扫描所有活动行标记为待恢复，玩家登录时兜底恢复并删除记录。强杀或硬断电都不会丢已提交的备份。
On server startup, every active row is marked for restore; on player join, the fallback restore runs and deletes the row. Hard kills and power loss never lose committed backups.

---

## 软依赖 · Soft Dependencies

| 插件 / Plugin | 功能 / Feature |
|---|---|
| `Vault` | 入场费、通关金币奖励 / Entry fee, reward money |
| `PlaceholderAPI` | 占位符 / Placeholders |
| `Parties` | 队伍模式 / Party mode |
| `Slimefun` | 物品使用门控 / Item usage gating |
| `MythicMobs` | 自定义 Boss / Custom bosses |

全部可选，缺席时自动降级。
All optional; degrades gracefully when absent.

---

## 已知限制 · Known Limitations

仅支持 Sponge v2 和 v3 原理图，不支持 MCEdit 旧 `.schematic`。地牢世界使用虚空生成器，不支持自然生物和地形。内置 SQLite native 库仅包含 Linux x86_64 和 Windows x86_64，ARM 和 macOS 需自行修改 pom。硬崩后世界目录会残留磁盘，需手动清理，v0.6 将加入自动扫描。
Only Sponge v2 and v3 are supported; legacy MCEdit `.schematic` is not. Dungeon worlds use a void generator with no natural mobs or terrain. Bundled SQLite native libs cover only Linux x86_64 and Windows x86_64 — ARM and macOS users must modify the pom. After hard crashes, world folders remain on disk and must be cleaned manually; auto-scan cleanup is planned for v0.6.

---

## 许可证 · License

See the Licese file.

---

## 致谢 · Credits

感谢 PaperMC 提供高性能服务端，Querz NBT 提供 NBT 库（MIT），SQLite JDBC 提供嵌入式数据库（Apache-2.0）。
Thanks to PaperMC for the high-performance server, Querz NBT for the NBT library (MIT), and SQLite JDBC for the embedded database (Apache-2.0).

<p align="center"><i>Made with ❤️ by HimxhMC</i></p>
