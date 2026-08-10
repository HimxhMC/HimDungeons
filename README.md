HimDungeons Dungeon Plugin
HimDungeons 地牢插件

Dynamic Void World Generation System
一、动态虚空世界生成体系
This plugin adopts an independent and isolated dynamic world generation scheme. 
Each time a dungeon is generated, a unique exclusive void world will be automatically created with the naming rule dungeon_dungeonName_timestamp, 
which completely prevents block conflicts between different dungeon instances.
该插件采用独立隔离的动态世界生成方案，每次生成地牢时会自动创建唯一命名的专属虚空世界，命名规则为 dungeon_地牢名_时间戳，完全避免不同地牢实例之间的区块冲突。

The entire generation process uses a dual scheduling mechanism of main thread + async thread: 
world creation is executed on the main thread to ensure Bukkit safety, while room layout calculation runs on the async thread to avoid server lag. 
If generation fails, it will automatically trigger world destruction and resource release, leaving no residual useless world files.
生成全程采用主线程+异步线程双调度机制：世界创建放在主线程执行保障Bukkit安全，房间布局计算放到异步线程运行避免服务器卡顿，一旦生成失败会自动触发世界销毁和资源释放，
不会产生残留无用世界文件。

Phased Dungeon Layout Generation Algorithm
二、分阶段地牢布局生成算法
Instead of using a single random generation logic, 
the plugin implements a three-layer progressive generation process that balances the rationality and randomness of dungeon paths.
插件没有采用单一随机生成逻辑，而是设计了三层递进的生成流程，兼顾地牢通路的合理性和随机性。

Backtracking-enabled Main Path Generation
带回溯的主路径生成

Starting from the start room, the main path is strictly generated according to the configured "range of room count from start to Boss room". 
Fallback rooms are strictly forbidden from entering the main path. When a path segment cannot be extended further, 
the system will automatically perform backtracking, roll back to the last expandable node and retry, 
until the Boss room is successfully placed at the end of the main path, completely avoiding dead-end main paths.
从起始房间开始，严格按照配置的「起点到Boss房间数量范围」生成主通路，全程禁止fallback房间进入主路径。当某段通路无法继续延伸时会自动执行回溯逻辑，
回退到上一个可扩展节点重新尝试，直到成功将Boss房间放置在主路径末端，完全避免生成断头主路的问题。

BFS Branch Auto Expansion
BFS分支自动拓展

After the main path is generated, all free doors on the main path will be automatically used as root nodes to start a 
Breadth-First Search to expand branch rooms outward. The expansion depth strictly follows the configured maximum depth limit. 
When the configured minimum room count is reached and there are no remaining expandable nodes, the expansion will be terminated early to 
avoid unnecessary performance consumption.
主路径生成完成后，会自动将主路径所有空闲门作为根节点，启动广度优先搜索向外拓展分支房间，拓展深度严格遵守配置的最大深度限制。
当已经达到配置的最小房间数且没有剩余可拓展节点时，会提前终止拓展避免不必要的性能消耗。

Empty Door Pre-Matching + Fallback Blocking
空门预配对+fallback封堵

After branch expansion, full empty door pre-matching will be performed first, 
automatically binding and connecting free room doors that have completely matched positions and orientations.
Then all remaining unused empty doors will be traversed and blocked uniformly with fallback rooms, which not only improves room utilization, 
but also leaves no floating invalid doors.
分支拓展完成后会先执行全量空门预匹配，将位置、朝向完全对接的空闲房间门自动绑定连通，之后再遍历所有剩余未使用的空门，用fallback房间统一封堵，既提升了房间利用率，
也不会留下悬空无效门。

Optional Full-map Connectivity Validation
可选全图连通性校验

It supports enabling BFS full-map connectivity check, which automatically abstracts all rooms into a connected graph for traversal and validation,
ensuring that all rooms are connected to the start point and no isolated rooms are separated from the main dungeon system.
支持开启BFS全图连通性检查，自动将所有房间抽象为连通图遍历校验，确保所有房间都和起点连通，不会出现孤立房间脱离地牢主体系的问题。

Highly Compatible Room Docking Core Technology
三、高兼容的房间对接核心技术
This is the most fundamental generation capability of this plugin, which fully implements a custom misalignment-free automatic door pairing logic.
这是该插件最核心的生成底层能力，完全自定义实现了无错位自动门配对逻辑。

Door information for all rooms is defined through an independent _door.yml file, supporting custom door relative coordinates, 
orientation, type and weight. Each room can automatically adapt to docking requirements with up to 4 times 90-degree Y-axis rotation.
所有房间的门信息通过独立的_door.yml文件定义，支持自定义门相对坐标、朝向、类型和权重，房间最多支持4次90度绕Y轴旋转自动适配对接需求。

It introduces an original strict dual-validation door matching rule: the coordinate one block forward from door A's 
orientation must equal the anchor point of door B, and meanwhile the coordinate one block forward from door B's orientation must equal the anchor point of door A,
which 100% eliminates door misalignment and clipping issues.
独创严格的双校验门匹配规则：要求A门朝向前进一格的坐标等于B门锚点，同时B门朝向前进一格的坐标等于A门锚点，100%避免门错位、穿模的问题。

It supports door type matching mechanism: non-fallback doors can only be docked with doors of the same type, 
while fallback doors can connect to any normal room door. It also provides a configurable door-pairing reuse switch to flexibly control 
whether the same door pair can be reused to connect different rooms.
支持门类型匹配机制：非fallback门必须同类型才能对接，fallback门可任意对接普通房间门，同时支持门配对复用开关配置，灵活控制是否允许同一对门重复连接不同房间。

Multi-layer Collision Overlap Prevention System
四、多层碰撞防重叠校验体系
Two switchable collision detection schemes are provided to adapt to different usage scenarios.
提供了两种可切换的碰撞检测方案，适配不同的使用场景。

Default AABB Axis-Aligned Bounding Box Detection: Each generated room is assigned an independent spatial bounding box. 
During room expansion, the system automatically validates whether the new room's bounding box overlaps with existing rooms. 
This method has extremely high performance and fits most scenarios.
默认AABB轴对齐包围盒检测：给每个生成的房间生成独立的空间包围盒，房间拓展时自动校验新房间的包围盒是否和已有房间重叠，性能极高，适配绝大多数场景。

Custom Prevention Block Detection Mode: After disabling AABB, 
the system will automatically traverse the coordinates of specified special blocks in all already placed rooms. 
When generating a new room, it will detect whether the candidate space conflicts with these special block positions, 
enabling fine-grained collision control based on user-defined special blocks.
自定义阻止方块检测模式：关闭AABB后，会自动遍历所有已放置房间的指定特殊方块的坐标，新房间生成时检测候选空间是否和这些特殊方块位置冲突，可实现基于
自定义特殊方块的精细碰撞控制。

In addition, it supports configuring the minimum room spacing parameter, 
which enforces a specified distance between different rooms, completely preventing overcrowding and overlapping during dungeon generation.
除此之外还支持配置房间最小间距参数，强制不同房间之间保持指定距离，完全避免地牢生成拥挤重叠的问题。

WorldEdit Asynchronous Optimized Paste Technology
五、WorldEdit异步优化粘贴技术
The native WorldEdit paste logic has been deeply customized and optimized.
对原生WorldEdit粘贴逻辑做了深度定制优化。

Schematic loading and non-air block boundary pre-calculation are all executed on the async thread, 
and only the final block writing operation is placed on the main thread, which greatly reduces main thread IO occupation.
原理图加载和非空气方块边界预计算全部放在异步线程执行，仅把最终方块写入操作放到主线程，大幅降低主线程IO占用。

New automatic Y-axis calibration logic is added: 
it automatically identifies the coordinate of the bottommost non-air block in the schematic, 
offsets the misalignment caused by redundant height in the schematic, so that rooms with different heights can be perfectly 
aligned to the unified anchor height.
新增自动Y轴校准逻辑：自动识别原理图中最底部的非空气方块坐标，抵消原理图冗余高度带来的错位问题，让不同高度的房间都能完美对齐到统一锚点高度。

After pasting is completed, the system will automatically execute chest filling logic. 
Based on the independent _chest.yml configuration file, specified items will be generated at specified relative coordinates with configured probability. 
The coordinates will be automatically adapted to the current room's rotation angle after rotation, no need to manually adjust loot coordinates.
粘贴完成后自动执行宝箱填充逻辑，基于独立的_chest.yml配置文件，按概率在指定相对坐标位置生成指定物品，旋转后坐标自动适配当前房间的旋转角度，无需手动调整 loot 坐标。

Supporting Generation Auxiliary Capabilities
六、配套生成辅助能力
Automatic Room Resource Scanning: It automatically and recursively scans the rooms directory of the dungeon, 
automatically recognizes schem/schematic schematics with valid door.yml, automatically classifies them into four categories by prefix: 
start, boss, fallback and normal rooms, and automatically injects the generation weight set in the configuration file. 
Custom weight configuration is also supported to adjust the generation probability of different rooms.
房间资源自动扫描：自动递归扫描地牢的rooms目录，自动识别带合法door.yml的schem/schematic原理图，自动按前缀分类为start、boss、fallback、普通房间四类，
自动注入配置文件中设置的生成权重，也支持自定义权重配置实现不同房间的生成概率调整。

Room Reuse Control: A toggle switch is provided to control whether the same room template can be reused to generate multiple instances, 
meeting different dungeon style requirements.
房间复用控制：支持开关控制是否允许重复使用同一个房间模板生成多个实例，满足不同的地牢风格需求。

Post-generation Automatic Initialization: After the dungeon is fully generated, 
the system will automatically teleport the player to the custom spawn point in the start room, 
automatically spawn the Boss with configured attributes, and support custom Boss type and NBT. 
It also supports automatic deep copy backup of the player's inventory before entering the dungeon, and starts the dungeon time limit timer. 
The complete closed-loop generation logic requires no extra manual operations.
生成后自动初始化：地牢生成完成后自动把玩家传送到起始房间的自定义出生点，自动生成配置指定属性的Boss，支持自定义Boss的类型和NBT；同时支持自动深拷贝
备份玩家进入地牢前的背包，开启地牢限时计时器，完整的生成闭环逻辑无需额外手动操作。
