HimDungeons – 地牢生成插件
HimDungeons 是一款基于 WorldEdit 的 Minecraft 地牢生成插件，支持自定义房间模板、自动布局、门配对、Boss 战、背包隔离、多种奖励及权限控制，适合用于 RPG 或小游戏服务器。

简介
HimDungeons 通过读取预制的 .schem 原理图（由 WorldEdit 创建）和配套的 YAML 配置文件，自动生成一座随机、连通、带有 Boss 房间的迷宫式地牢。玩家可单人挑战，也可组队（支持 Party 插件）进入。

✨ 主要特性
完全可视化配置：所有地牢管理操作（添加房间/门/箱子/出生点/存档点、修改配置）均可通过 GUI 完成，无需手动编辑文件。

完全自定义房间池：只需准备 .schem 和 _door.yml，即可定义**任意形状，任意大小，门位置方向数量没有限制**的房间（GUI 可辅助生成门配置）。

智能门配对：通过 x±/y±/z± 朝向自动匹配，支持旋转。

多种房间类型：start、boss、fallback（封堵门）以及普通房间，各有权重。

高效生成算法：先保证一条从起点到 Boss 的主路径，再通过 BFS 分支扩展，最后用 fallback 补全所有空门。

AABB 碰撞检测：防止房间重叠（可配置关闭）。

完整配置系统：全局 + 每个地牢独立配置，涵盖游戏规则、奖励、权限、费用等。

管理 GUI：可视化创建/编辑地牢、添加房间/门/箱子/出生点/存档点，以及编辑任意配置项（支持批量修改）。

玩家背包隔离：支持深拷贝背包，进入地牢时备份并清空，离开时恢复。

多种 Boss 支持：支持原版实体及 MythicMobs（软依赖）。

奖励与入场费：支持 Vault 货币、PlayerPoints 点券、执行控制台命令。

权限控制：可设置允许/禁止的命令、方块放置/破坏白名单。

支持与HimDSL一起控制地牢。

Soft Dependencies：支持 Parties、Vault、Slimefun、MythicMobs、PlaceholderAPI。

安装与依赖
硬依赖
WorldEdit（≥ 7.0）

软依赖（可选）
Vault – 经济奖励/入场费

PlayerPoints – 点券奖励/入场费

Parties – 组队模式

Slimefun – 限制 Slimefun 物品使用

MythicMobs – Boss 生成

PlaceholderAPI – 占位符支持（未强制，但可扩展）

安装步骤
将 HimDungeons.jar 放入服务器的 plugins/ 目录。

确保 WorldEdit 已安装并正常启动。

启动服务器，插件会自动生成 plugins/HimDungeons/config.yml 以及 example_dungeon 示例地牢（内含完整的房间示例）。

根据需要，使用管理 GUI 或直接修改配置文件来定制地牢。

快速开始
1. 创建新地牢
使用命令 /himdungeons create <地牢名> 创建地牢目录和默认配置。

建议：创建后直接用 /himdungeons edit <地牢名> 打开管理 GUI 进行后续配置。

2. 通过 GUI 添加房间
用 WorldEdit 在游戏内选中一个区域（作为房间模板）。

在管理 GUI 中点击 “保存房间”，输入房间基础名（如 combat_01），插件会自动将选区保存为 .schem，并生成一个空的 _door.yml 模板（后续需添加门）。

若要添加门，点击 “添加门”，然后看向要设为门位置的方块，点击即可记录坐标，随后输入 基础名 朝向 [类型] [权重]。

同理，可添加箱子、出生点、存档点。

所有文件操作（创建/删除房间、门、箱子、出生点、存档点）均可在 GUI 中完成，无需手动编辑 YAML。

3. 配置地牢
在管理 GUI 中点击 “编辑配置”，打开配置编辑器，可调整所有实例参数（房间数量、深度、权重、Boss、奖励、权限等）。支持批量编辑，可一次性将某项设置应用到所有地牢。

4. 启动地牢
玩家使用：

bash
/himdungeons start [地牢名] [模式]
不指定参数则打开 GUI 选择地牢。

模式：single（单人）、multi（附近潜行玩家加入）、party（通过 Party 插件邀请队伍）。

地牢生成后，玩家会被传送至 start 房间的出生点，背包会被备份并清空（若开启 deepCloneInventory）。

管理 GUI 详解
打开方式
/himdungeons edit – 打开主菜单，列出所有已配置的地牢。

/himdungeons edit <地牢名> – 直接进入指定地牢的管理面板。

主菜单
显示所有带有 config.yml 的地牢文件夹，点击图标进入该地牢的管理面板。底部还有：

全局配置 – 修改全局语言、消息前缀、生成冷却。

批量编辑 – 进入配置编辑器，可将某项设置同时应用到所有地牢。

刷新 – 重新扫描地牢列表。

地牢管理面板（功能按钮）
按钮	功能	操作说明
保存房间	将 WorldEdit 选区保存为房间模板	输入基础名（可含子路径，如 a/b/room1），自动生成 .schem 和空的 _door.yml
添加门	为房间添加门定义	先看向方块以记录相对坐标，然后输入 基础名 朝向 [类型] [权重]（如 combat_01 z+ default 10）
添加箱子	为房间添加战利品箱	先看向方块记录位置，然后输入房间基础名，之后逐个输入概率和物品（如 50 DIAMOND 1），输入 done 结束
添加出生点	为房间设置玩家/Boss 出生点	站在目标位置，输入房间基础名，生成 _spawn.yml
删除房间	删除房间的所有相关文件	输入基础名，会删除 .schem、_door.yml、_chest.yml、_spawn.yml、_checkpoint.yml
删除门	仅删除该房间的 _door.yml	输入基础名
删除箱子	仅删除 _chest.yml	输入基础名
删除出生点	仅删除 _spawn.yml	输入基础名
编辑配置	打开配置编辑器	可修改所有 dungeonInstance.* 参数，支持批量操作
添加存档点	设置玩家进入该房间时的自动存档点	站在目标位置，输入房间基础名，生成 _checkpoint.yml
删除存档点	删除 _checkpoint.yml	输入基础名
所有输入均支持 cancel 取消操作。

配置编辑器
配置编辑器以 GUI 形式展示所有配置项，按类别排列，支持：

点击调整数值：左键 +1，右键 -1，Shift+左键 +10，Shift+右键 -10（整数类型）。

布尔值切换：点击直接翻转。

字符串输入：左键弹出聊天输入，右键清空为默认值。

列表编辑：左键添加条目，右键删除条目（输入要移除的元素，或 * 清空全部）。

软依赖提示：若配置项依赖的插件未安装，会显示红色警告，禁止修改。

批量模式：在批量编辑器中，修改会同步到所有地牢。

配置指南
全局配置（plugins/HimDungeons/config.yml）
yaml
lang: zh_CN                     # 语言文件（支持 zh_CN, en_US 等）
msgPrefix: "&a[HimDungeons] &r" # 全局消息前缀
generateCooldown: 0             # 生成冷却（秒，0 为无限制）
地牢实例配置（<地牢名>/config.yml）
所有实例配置位于 dungeonInstance 节点下。推荐使用配置编辑器修改，避免格式错误。

基本设置
键	说明	默认值
enable	是否启用该地牢	true
displayName	显示名称	地牢
fileName	文件名（不重要）	dungeon_01
msgPrefix	地牢专用消息前缀（覆盖全局）	无
生成参数
键	说明	默认值
minRoomAmount	最少非 fallback 房间数	5
maxRoomAmount	最多非 fallback 房间数	12
dfsMaxDeep	BFS 分支最大深度	8
useBfsCheck	是否进行连通性检查（BFS）	false
useAabb	是否使用 AABB 碰撞检测（关闭则使用阻止方块检测）	true
disconnectedRoomDistance	房间最小间距（0 为不检查）	0
allowRoomReuse	是否允许重复使用同一房间模板	false
allowDoorReuse	是否允许重复使用同一门配对	false
allowFallbackDoorReuse	是否允许 fallback 门复用	false
startToBossRoomAmmount	起点到 Boss 之间的房间数范围，如 "10 10"	"10 10"
blockRoomGenerateBlocks	阻止方块列表（如 ["CHEST", "SPAWNER"]）	[]
generatorChoose	自定义类型权重覆盖（格式：[[类型, 权重], ...]）	[]
结构权重（dungeonInstance.structureChoose）
此节点下可以按房间基础名（不含后缀）设置权重，数值越大被选中的概率越高。
示例：

yaml
structureChoose:
  combat: 100
  straight: 80
  corner: 60
  tCorner: 40
  cross: 20
  stair: 20
  boss: 30
  fallback: 10
游戏规则与环境
键	说明	默认值
allowParties	是否允许 Party 模式	false
allowSlimefun	是否允许使用 Slimefun 物品	false
bossType	Boss 类型列表（原版实体名）	["zombie"]
bossNbt	对应的 NBT 字符串（可设血量等）	["{Health:100.0f}"]
keepInventory	死亡是否保留物品（MC gamerule）	false
deepCloneInventory	进入时深拷贝背包（隔离物品）	false
time	限时（秒，0 为不限）	0
dungeonTime	世界时间（tick，0~24000）	1200
advanceTime	时间是否流逝	false
weather	天气：clear/rain/thunder	clear
difficulty	难度：peaceful/easy/normal/hard	normal
allowMobs	允许生成的生物列表（all 表示全部）	["all"]
useMythicMobs	是否使用 MythicMobs 生成 Boss	false
权限与控制
键	说明	默认值
commandsAllowListType	命令列表类型：white/black	white
commandsAllow	命令列表（不带 /）	[]
buildSettingsListType	建造白/黑名单类型	white
buildSettings	可放置方块材质列表	[]
breakSettingsListType	破坏白/黑名单类型	white
breakSettings	可破坏方块材质列表	[]
lobby	退出时传送的地点（格式：x,y,z,yaw,pitch,world）	无
奖励（dungeonInstance.rewards）
yaml
rewards:
  vault:
    money: 100.0         # 给予 100 货币
  playerPoints:
    points: 50           # 给予 50 点券
  consoleCommands:
    - "say {player} 通关了！"
入场费（dungeonInstance.enterCost）
yaml
enterCost:
  vault:
    money: 10.0          # 扣除 10 货币
  playerPoints:
    points: 5            # 扣除 5 点券
  consoleCommands:
    - "say {player} 进入地牢"
地牢设计
房间文件结构（由 GUI 自动管理，无需手动创建）
text
<地牢名>/
├── config.yml
└── rooms/
    ├── start_room.schem
    ├── start_room_door.yml
    ├── start_room_spawn.yml       (可选)
    ├── boss_room.schem
    ├── boss_room_door.yml
    ├── boss_room_spawn.yml
    ├── normal/
    │   ├── combat_room.schem
    │   ├── combat_room_door.yml
    │   ├── combat_room_chest.yml   (可选)
    │   └── ...
    └── fallback_room.schem
        └── fallback_room_door.yml
关键文件说明（了解即可，GUI 会生成并维护）
_door.yml：定义门的位置、朝向、类型和权重。
示例内容：

yaml
doors:
  - { x: 0, y: 1, z: 5, facing: z+, type: default, weight: 10 }
_spawn.yml：定义玩家或 Boss 的出生点偏移（相对房间原点）。

yaml
spawn:
  x: 0
  y: 1
  z: 0
_chest.yml：定义战利品箱。

yaml
chests:
  - pos: [2, 1, 3]
    loot: "50 DIAMOND 1"
_checkpoint.yml：定义玩家进入该房间时的自动存档点（格式同 _spawn.yml）。

房间命名规则
以 start 开头的房间视为起始房间（必须存在且只有一个）。

以 boss 开头的房间视为 Boss 房间（必须存在且只有一个）。

以 fallback 开头的房间用作封堵未配对的门（需只有一个门）。

其他房间为普通房间，用于路径填充。

所有房间必须至少有一个门，Boss 房间可以有多个门但通常只保留一个用于连接，其余会自动封堵。

命令与权限
玩家命令
命令	说明	权限节点
/himdungeons start [地牢] [模式]	打开地牢选择 GUI 或直接启动	himdungeons.start
/himdungeons leave	离开当前地牢	himdungeons.leave
/himdungeons help	显示帮助	无（有任一项权限即可）
管理员命令
命令	说明	权限节点
/himdungeons create <地牢名>	创建新地牢目录	himdungeons.admin
/himdungeons edit [地牢名]	打开管理 GUI	himdungeons.admin
/himdungeons reload	重载配置	himdungeons.admin
/himdungeons blockpos <x> <y> <z>	调试：计算相对坐标	himdungeons.admin（需 debug 模式）
技术原理
生成流程
扫描房间池：读取 rooms/ 下所有 .schem 及配套 _door.yml，按前缀分类（start/boss/fallback/normal）。

构建主路径：从起始房间开始，随机选择可用门，扩展普通房间，直到达到指定长度，然后在末端尝试放置 Boss 房间。若失败则回溯。

BFS 分支扩展：将主路径上所有空门作为根节点，以 BFS 方式向外扩展普通房间，直到达到最大房间数或无可扩展节点。

预匹配：在所有非 fallback 房间之间尝试配对空门，减少后续 fallback 数量。

Fallback 封堵：遍历所有剩余空门，尝试放置 fallback 房间（必须只有一个门且朝向匹配）。

连通性检查（可选）：验证所有房间是否通过门逻辑连通。

异步粘贴：按顺序将原理图粘贴到世界，同时填充箱子、应用阻止方块检测。

初始化实例：设置玩家存档点、Boss 实体、背包备份、限时计时器等。

核心技术
门配对算法：基于 DoorMatcherUtil，通过相对坐标和朝向向量，判断两个门是否能正确对接（P1 + D1 = P2 且 P2 + D2 = P1）。

旋转支持：所有相对坐标和朝向均支持 0°/90°/180°/270° 旋转（绕 Y 轴）。

AABB 碰撞：使用 AABBUtil 检测房间包围盒是否重叠，支持间距约束。

权重选择：结构权重、门权重均影响随机选择概率。

Boss 管理：Boss 死亡时触发通关逻辑，恢复所有玩家背包并传送回大厅。

数据持久化：玩家背包备份存储于 <地牢名>/player_data.json，支持重连恢复。

开发者 API
BossAPI
提供静态方法用于 HimDSL 脚本或其他插件获取 Boss 信息：

java
// 获取玩家当前世界的 Boss
BossData data = BossAPI.getBoss(player);
// 获取血量
double health = BossAPI.getBossHealth(player);
// 获取坐标
double x = BossAPI.getBossX(player);
详细方法见 BossAPI 类。

事件监听
插件未提供专门的 API 事件，但可通过监听 EntityDeathEvent 判断 Boss 死亡（通过 HimDungeons.dungeonBosses 匹配）。通关时调用 HimDungeons.triggerHimDSLEvent(true) 用于 HimDSL 集成。

扩展点
实现自己的 ConfigApplier 或继承现有类以覆盖奖励、权限逻辑。

通过 HimDungeons.getInstance() 获取插件实例，访问 dungeonInstances、dungeonBosses 等状态。

常见问题
Q: 地牢生成失败，提示 "Cannot place start room"？
A: 检查 rooms/ 下是否有以 start 开头的 .schem 和对应的 _door.yml，且原理图有效。可以使用 GUI 重新保存或添加。

Q: 生成后房间重叠怎么办？
A: 可开启 useAabb: true 并调整 disconnectedRoomDistance。也可在 _door.yml 中调整门的位置使房间之间保留更大间距。

Q: 玩家退出地牢后背包没有恢复？
A: 检查是否开启了 deepCloneInventory，若开启则必须正常退出（使用 /leave 或通关）。若异常退出，重启服务器会自动恢复备份（PlayerDataManager.restoreAllPendingBackups）。

Q: Boss 不生成或血量异常？
A: 确认 bossType 和 bossNbt 配置正确。若使用 MythicMobs，确保插件已安装且 useMythicMobs 为 true。

Q: 如何让玩家使用自定义命令？
A: 在 commandsAllow 白名单中添加命令（不带 /），或将列表类型改为 black 并排除禁止命令。

Q: 支持 1.20+ 吗？
A: 插件基于 WorldEdit 7.x 和 Bukkit API，理论上支持 1.16.5 及以上版本。建议使用最新稳定版。

贡献与许可
本插件使用 GNU General Public License v3.0 授权。
您可以自由修改、分发，但必须保留版权声明，且任何衍生作品也需采用 GPL v3 开源。

贡献指南
Fork 本仓库，提交 Pull Request。

请保持代码风格一致（遵循 Java 命名规范）。

新增功能建议附带文档说明。

感谢使用 HimDungeons！ 如有问题请提交 Issue 或联系作者。



提示：你可自由提交Issue,PR或者Fork，但是作者在未来一年处于九年级期间，时间很少。若发现
bug请尝试自行修复或者等待明年暑假。
