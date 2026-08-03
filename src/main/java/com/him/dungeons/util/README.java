/*
都位于 com.him.dungeons.util包下。

public static ...

方法签名 作用 输入 输出 类型
TransformUtil.java
Vector facingToVector(String facing) 将朝向字符串转为单位方向向量 facing: "x+"/"x-"/"y+"/"y-"/"z+"/"z-" 对应单位向量，不可识别返回(0,0,0) 纯数学
boolean isHorizontalFacing(String facing) 判断是否为水平方向（x/z轴） facing: 朝向字符串 true=水平方向 纯数学
boolean isVerticalFacing(String facing) 判断是否为垂直方向（y轴） facing: 朝向字符串 true=垂直方向 纯数学
String rotateFacing(String facing, int times) 绕Y轴旋转朝向（90°倍数） facing: 原朝向；times: 0~3 旋转后的朝向字符串 纯数学
BlockVector3 rotateBlockVector(BlockVector3 vec, int times) 绕Y轴旋转BlockVector3 vec: 原向量；times: 0~3 旋转后的新向量 纯数学
Vector rotateVector(Vector vec, int times) 绕Y轴旋转Vector vec: 原向量；times: 0~3 旋转后的新向量 纯数学
BlockVector3 locationToBlockVector(Location loc) Location → BlockVector3 loc: Bukkit位置 BlockVector3对象，null返回null 纯数学
boolean isValidFacing(String facing) 校验朝向字符串是否合法 facing: 待校验字符串 true=合法 纯数学

AABBUtil.java
方法签名 作用 输入 输出 类型
AABB buildFromClipboard(Clipboard clipboard) 从Clipboard构建原始AABB clipboard: 已加载的剪贴板 原始坐标系AABB 数据构建
AABB buildPastedAABB(Clipboard clipboard, Location anchor, AffineTransform transform) 计算粘贴后的世界坐标AABB clipboard: 剪贴板；anchor: 粘贴锚点；transform: 变换（可null） 世界坐标AABB 数据构建
AABB build(Location anchor, BlockVector3 size) 用锚点和尺寸构建AABB anchor: 锚点；size: 尺寸 AABB对象 数据构建
double minDistance(AABB a, AABB b) 计算两个AABB的最近距离 a, b: 两个AABB 欧几里得距离（重叠返回0） 纯数学
boolean overlaps(AABB a, AABB b) 检查两个AABB是否重叠 a, b: 两个AABB true=重叠 纯数学
AABB offset(double dx, double dy, double dz) 偏移生成新AABB dx/dy/dz: 偏移量 偏移后的新AABB 数据构建

WorldUtil.java
方法签名 作用 输入 输出 类型
World createVoidWorld(String worldName) 创建虚空世界 worldName: 唯一世界名 World对象，失败抛异常 世界操作
void applyWorldSettings(World world, FileConfiguration config) 应用配置到世界（时间/天气/难度/怪物等） world: 目标世界；config: 实例配置 void 世界操作
boolean deleteWorldAndRelease(World world) 删除世界并清理磁盘 world: 待删除世界 true=删除成功 世界操作
Location toLocation(BlockVector3 vec, World world) BlockVector3 → Location vec: 向量；world: 目标世界 Location对象 数据转换

ResourceUtil.java
方法签名 作用 输入 输出 类型
File getDungeonRoot(String dungeonName) 获取地牢根目录 dungeonName: 地牢名 File对象，不存在返回null 文件操作
File getRoomFolder(String dungeonName, String roomType) 获取房间类型文件夹 dungeonName: 地牢名；roomType: 房间类型 File对象，不存在返回null 文件操作
File getRoomSchematic(String dungeonName, String roomType) 获取房间Schematic文件 dungeonName: 地牢名；roomType: 房间类型 .schematic文件，不存在返回null 文件操作
File getRoomDoorYaml(String dungeonName, String roomType) 获取房间door.yml dungeonName: 地牢名；roomType: 房间类型 door.yml文件，不存在返回null 文件操作
File getRoomChestYaml(String dungeonName, String roomType) 获取房间chest.yml dungeonName: 地牢名；roomType: 房间类型 chest.yml文件，不存在返回null 文件操作
File getRoomSpawnYaml(String dungeonName, String roomType) 获取房间spawn.yml dungeonName: 地牢名；roomType: 房间类型 spawn.yml文件，不存在返回null 文件操作
List<String> listAvailableRoomTypes(String dungeonName) 扫描地牢下所有有效房间类型 dungeonName: 地牢名 房间类型名列表 文件操作

YamlDoorUtil.java
方法签名 作用 输入 输出 类型
List<DoorAnchor> parseDoorAnchors(File doorFile) 解析door.yml为门锚点列表 doorFile: door.yml文件 门锚点列表，为空返回空列表 解析
List<Map<String, Object>> parseChestLoot(File chestFile) 解析chest.yml为战利品列表 chestFile: chest.yml文件 战利品Map列表，为空返回空列表 解析
Vector parseSpawnPoint(File spawnFile) 解析spawn.yml为出生点 spawnFile: spawn.yml文件 出生点Vector，不存在返回null 解析

WorldEditUtil.java
方法签名 作用 输入 输出 类型
void pasteAndReleaseSync(File schematicFile, Location anchor, int rotationTimes, Consumer<Boolean> callback) 粘贴Schematic（自动切主线程，自动释放） schematicFile: .schematic文件；anchor: 锚点；rotationTimes: 旋转次数；callback: 完成回调 void（回调返回Boolean） 世界编辑
BlockVector3 getClipboardSizeAndRelease(File schematicFile) 仅读取Schematic尺寸（加载后立即释放） schematicFile: .schematic文件 尺寸向量，失败返回null 世界编辑
boolean isSchematicValid(File schematicFile) 校验Schematic文件是否有效 schematicFile: .schematic文件 true=有效 世界编辑

RoomInstance.java
方法签名 作用 输入 输出 类型
List<Integer> getAvailableDoorIndices() 获取当前房间未使用的门索引 无 空闲门索引列表 数据操作
void markDoorUsed(int idx) 标记某个门已被占用 idx: 门索引 void 数据操作

DoorMatcherUtil.java
方法签名 作用 输入 输出 类型
boolean isMatching(BlockVector3 p1, String f1, BlockVector3 p2, String f2) 核心方程验证：P₁+D₁=P₂且P₂+D₂=P₁ p1/f1: 门1坐标和朝向；p2/f2: 门2坐标和朝向 true=配对成功 纯数学
int findAvailableMatchingDoor(BlockVector3 currentDoorWorld, String currentFacing, List<DoorAnchor> candidateDoors, Location candidateAnchor, int rotationTimes, List<Integer> usedDoorIndices) 在候选房间中查找空闲且配对的 currentDoorWorld: 当前门坐标；currentFacing: 当前门朝向；candidateDoors: 候选门列表；candidateAnchor: 候选锚点；rotationTimes: 旋转次数；usedDoorIndices: 已占用索引 配对门索引，无返回-1 配对查找
int[] findDirectConnection(RoomInstance roomA, RoomInstance roomB) 检测两个房间是否有门对门连接 roomA: 房间A；roomB: 房间B {idxA, idxB}，无返回null 配对查找
BlockVector3 toWorldDoorPosition(DoorAnchor door, Location roomAnchor, int rotationTimes) 门相对坐标转世界坐标 door: 门锚点；roomAnchor: 房间锚点；rotationTimes: 旋转次数 世界坐标 数据转换
int calculateRequiredRotation(BlockVector3 currentDoorWorld, String currentFacing, List<DoorAnchor> candidateDoors, Location candidateAnchor, List<Integer> usedDoorIndices) 暴力计算使配对成功的旋转次数 currentDoorWorld: 当前门坐标；currentFacing: 当前门朝向；candidateDoors: 候选门列表；candidateAnchor: 候选锚点；usedDoorIndices: 已占用索引 0~3，无解返回-1 配对查找

RoomSelectorUtil.java
方法签名 作用 输入 输出 类型
String selectByWeight(Map<String, Integer> weightMap, Set<String> excludeTypes, String fallbackType) 按权重随机选房间类型 weightMap: 类型→权重；excludeTypes: 排除类型；fallbackType: 保底类型 选中的类型名 选择器

RoomGrapgUtil.java
方法签名 作用 输入 输出 类型
RoomInstance tryExtend(RoomInstance currentRoom, int doorIdx, List<RoomInstance> placedRooms, String dungeonName, World world, FileConfiguration config, int maxTotalRooms, int currentDepth, int maxDepth, boolean isMainBranch, int fallbackRetries) DFS尝试扩展一个分支（含门配对、AABB、间距检测、标记占用） 见上方参数列表 新房间实例，失败返回null 拼接算法
int getNextAvailableDoor(RoomInstance room) 获取房间的下一个可用门索引 room: 房间实例 门索引，无返回-1 辅助
boolean isFullyConnected(List<RoomInstance> rooms) 检查所有房间的门是否都已连接 rooms: 所有房间列表 true=所有门已用完 辅助
boolean checkLogicalConnectivity(List<RoomInstance> placedRooms) BFS逻辑连通性检查（非物理） placedRooms: 所有已放置房间 true=所有房间逻辑连通 校验
int[] getNextBranchNode(List<RoomInstance> placedRooms) 获取DFS下一个待扩展节点 placedRooms: 已放置房间列表 {roomIndex, doorIndex}，无返回null 辅助

ConfigEditorUtil.java    使用ConfigEditorUtil.registerListener(this)来注册监听器
方法签名 作用 输入 输出 调用位置
static void registerListener(JavaPlugin plugin) 注册事件监听器 plugin：插件主类实例 void 主类 onEnable()
static void openEditor(Player player, String dungeonName) 打开配置编辑器 GUI player：目标玩家；dungeonName：地牢名（null=全局） void（异步打开 GUI） 命令执行器
*/