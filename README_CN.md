<div align="center">

# BetterGregTechPonder

**在 Create 的思索（Ponder）场景里画出 GregTech CEu Modern 机器自己的界面，并按真实槽位序号读写物品与流体。**

[English](README.md) | 简体中文

</div>

在 Create 的思索（Ponder）场景里画出 **GregTech CEu Modern 机器自己的界面**，并按它真实的槽位序号往里放物品、灌流体。

- 针对官方 GTCEu 编译（`curse.maven:gregtechceu-modern-890405:7917773`），不依赖任何分支改动
- 纯客户端库：绘制相关的类不会被服务端加载
- 面板用 Ponder 自己的 speech box 画，指针尖指向场景里的坐标
- 槽位序号与游戏里的机器界面一致；物品数量与储罐流体量都从 0 在 1 秒内涨到目标值
- 往机器上贴覆盖板、把机器模型切成工作/待机，都是独立的场景指令（改机器状态），和界面无关；机器不支持时在日志里报错
- 给一个配方 id 就能把机器填满：输入、流体、成品各就各位，面板里的进度条自己走一遍；配方要编程电路时自动写进电路槽；机器与配方对不上时报错
- 物品/流体的自动输出口朝向也能在场景里设（顺带打开自动输出），机器不支持时报错
- 并行仓与维护仓同样是能被场景改写的机器状态：`setParallel(hatchPos, n)`、`fixMaintenance(hatchPos)`，不想贴维护胶带时用 `fixMaintenanceWithoutTape`；每次改动都会重建面板，输入框里的数字跟着变
- 多方块控制器默认显示「结构无效」——思索的假世界不会 tick GT 的成型检测；在界面定义上写 `forceMultiblockActivated()` 就会直接成型
- 编程电路 UI 默认不画：要展示的配方带 `circuitMeta(n)` 时自动画出来，也可以用 `showCircuit()` 常开；展开的设置面板占背包那一行，按钮贴在它左边、垂直居中
- 可以给面板里的槽位、储罐、进度条、编程电路，以及机器页里的按钮与开关套红框，把注意力引过去
- 套红框用那些具名方法：`outlineSlot(i)`、`outlineTank(i)`、`outlineProgress()`、`outlineCircuit()`、`outlineButton(i)`，以及上面列出的配置器按钮各自的方法，都可带一个延迟 tick；它们全部汇入通用的 `outline(Part, index, delayTicks)`，而它同时是扩展点——`Part` 已公开，新增一类控件只需加一个枚举值并收集它，公共 API 不用动
- `showFullUI()` 能把原版 GT 的整套界面原样画出来（配置器面板、提示面板、玩家背包都在），不做任何裁剪
- 那套界面里的工作开关、自动输出、电路设置、总线隔离也能逐个套红框；机器没有的会报一行 error
- 打开 Ponder 的编辑模式（`ponder-client.toml` 里的 `editingMode`）后，鼠标停在槽位、储罐或按钮上，tooltip 首行会显示它在机器里的真实序号；按钮自身的提示也能正常显示

## 展示

**物品与流体填充** —— 序号就是机器界面里的真实槽位号，数量与液量从 0 在 1 秒内涨到目标值（详见下方的[示例用法](<#示例用法>)）：

![物品与流体填充](<docs/showcase/items_and_fluids.gif>)

**配方自动填充** —— 只给一个配方 id：入料、编程电路、成品各就各位，进度条自己走一遍（详见下方的[示例用法](<#示例用法>)）：

![配方自动填充](<docs/showcase/recipe_autofill.gif>)

**UI 详情** —— 点开左下角的「查看 UI 详情」：场景冻结，原版整套界面里的配置器与开关都能直接点（详见下方的[示例用法](<#示例用法>)）：

![UI 详情](<docs/showcase/ui_details.gif>)

**机器状态也能在思索里改** —— 工作开关、覆盖板、自动输出口都不是只读的：机器的运行/待机模型跟着
配方进度切换，只换正面贴图，配方逻辑与耗电原样不动（详见下方的[示例用法](<#示例用法>)）：

![机器状态可调](<docs/showcase/machine_power_on.png>)

**覆盖板与输出方向同理** —— 往指定面贴覆盖板、改物品/流体的自动输出口朝向，都是独立于界面的场景指令，
演完自动还原（详见下方的[示例用法](<#示例用法>)）：

![覆盖板与输出方向可调](<docs/showcase/covers_and_output.png>)

## 环境

| 依赖 | 版本 |
|------|------|
| Minecraft / Forge | 1.20.1 / 47.4.1 |
| GregTech CEu Modern | 官方 CurseForge 版本（file id 见 gradle.properties） |
| Create + Ponder | 6.0.8-291 / Ponder-Forge 1.20.1 |
| LDLib | 1.0.52 |

## 用法

```java
private static final MachineUI LV_INPUT_BUS_UI = MachineUI.of(GTMachines.ITEM_IMPORT_BUS[GTValues.LV])
        .scale(0.6f);

// PonderStoryBoard 里
MachineUIs.showUI(builder, LV_INPUT_BUS_UI)
        .at(pos)                          // 指向这台机器：尾巴尖落在方块中心，面板也画它
        .pointing(Pointing.DOWN)          // 面板落在指向点的哪一侧，默认 DOWN
        .slot(0).withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
        .tank(0).withFluid(new FluidStack(Fluids.WATER, 1000), 20)   // 储罐同理
        .show(200);
```

`slot(index)` 对应 UI 里第 index 个物品槽，`tank(index)` 对应第 index 个储罐；`withItem` / `withFluid` 的第二个参数是「面板出现后第几个 tick 开始写入」，
写入本身固定 1 秒。GT 与 LDLib 两份 `TankWidget` 都认。需要精确指向点时用 `at(vec)`（机器取该点所在方块）或 `at(vec, pos)`（指向点与机器分开给）；
默认只画标题栏、左侧页签和机器页，`showPlayerInventory()`、`showConfigurators()`、`showCircuit()`、`showNavigationButtons()` 可以把其余部分打开；
`showFullUI()` 更省事：一次性把 GT 那一整套都画上（标题栏、页签、机器页、配置器面板、提示面板、玩家背包），位置也照 GT 自己的布局，一个组件都不裁剪。
缩放用 `scale(f)`，或用 `fitToPanel(0.42f)` 按 Ponder 面板宽度自适应。缩放也可以写在摆放那一步
（`showUI(builder, ui, 0.6f)` 或链式 `.scale(0.6f)`），会盖过界面定义上的设定：

```java
MachineUIs.showUI(builder, LV_CHEMICAL_REACTOR_UI, 0.6f).at(pos).show(120);
MachineUIs.showUI(builder, LV_CHEMICAL_REACTOR_UI).at(pos).scale(0.45f).show(120);
```

机器有编程电路槽（`IHasCircuitSlot`）时，背包那一行可以多出一组编程电路 UI。默认不画；这段要展示的配方自己带
`circuitMeta(n)` 时会自动画（正好看见电路被设成配方要的那一档），也可以在 `MachineUI` 上写 `showCircuit()` 常开。
画出来时：
展开的面板占背包原来的位置（带 GT 自己的背景与标题），左侧贴一个垂直居中的电路按钮，面板里是 GT 自己的
0~32 编码设置格子（用的就是 GT 的 `CircuitFancyConfigurator`，只画这一个配置器，不画整个配置器面板）。
按钮图标每帧重取，机器里的电路换了它跟着换。这里只负责画，不改机器状态；电路槽那个幽灵槽不算进 `slot(index)`，
所以槽位序号仍与实机 UI 一致。

想让观众看某个控件，就给它套个红框（面板像素 2px、跟着面板缩放，透明度有呼吸感）：

```java
MachineUIs.showUI(builder, LV_CHEMICAL_REACTOR_UI).at(pos)
        .slot(1).withItem(stack, 20)
        .outlineSlot(1)          // 框住 1 号槽位
        .outlineTank(0)          // 框住 0 号储罐
        .outlineProgress()       // 框住进度条
        .outlineCircuit()        // 框住编程电路 UI（按钮 + 展开面板）
        .show(160);
```

四个都能带一个延迟参数（`outlineSlot(1, 20)`），到点才亮，一直亮到面板收起。

配合 `showFullUI()` 还能框 GT 配置器面板那一列里的按钮（这几个都是 GT 自己的控件，认得是哪一个看它的 tooltip）：

```java
MachineUIs.showUI(builder, FULL_REACTOR_UI).at(pos)
        .outlinePowerToggle()    // 工作开关（电源键）
        .outlineAutoOutput()     // 物品 / 流体自动输出开关，有几个框几个
        .outlineCircuitButton()  // GT 自己的电路设置按钮
        .outlineDistinct()       // 总线隔离（Distinct）
        .show(160);
```

框不到时不静默：机器没有这一路控件、或者那一列配置器面板没画出来（默认裁剪版就没有，得用 `showFullUI()`），
每个红框会往日志里报一行 error，面板照常画。

**每段 `showUI` 演完（面板淡出时）会把机器恢复原状**——这一段写进去的物品/流体都会还原，不会带进下一段；场景回退同理。

给一个配方 id，入料、流体、成品与进度条都由它安排：

```java
MachineUIs.showUI(builder, LV_CHEMICAL_REACTOR_UI).at(machinePos)
        .recipe("gtceu:chemical_reactor/sodium_sulfide", 10)   // 入料 1 秒 → 进度条 1 秒 → 成品 1 秒
        .show(200);
```

配方 id 就是 JEI 里那条配方，形如 `<模组>:<配方类型路径>/<配方名>`。输入的物品进输入槽、流体进输入储罐，
成品等进度条走完再落进输出槽与输出储罐；哪些槽位和储罐算输入、哪些算输出，看的是 GT 自己打的 `IngredientIO` 标签，
不用猜顺序。机器与配方对不上（不是配方机器、配方 id 不存在、配方类型不属于这台机器、面板里没有对应的槽位）时，
在日志里报一行 error 并跳过这一段，面板照常画；`show(...)` 的时长要留够 3 秒（入料、进度条、成品各 1 秒）。

配方里带 `circuitMeta(n)` 时，那一档编程电路会自动写进机器的电路槽（面板下方那组编码设置 UI 读的就是它），
入料的同时生效，面板收起或场景回退时还原；机器没有电路槽则在日志里报一行 error，料照常填。

配方跑起来时机器模型会跟着进度条自动开关机：进度条一开始走就切成工作中的样子，走到头立刻切回待机
（判断方式与 `setWorkingModel` 相同），面板收起或场景回退也会还原。

改机器状态用独立的场景指令，不挂在界面上，因此不受上面的还原影响：

```java
MachineEdits.placeCover(builder, pos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack());
MachineEdits.placeCover(builder, pos, Direction.NORTH, GTCovers.PUMPS[1], 40);   // 也可以直接给定义
```

贴不上时（机器没有覆盖板容器、这一面放不了、覆盖板拒绝附着）会在日志里报一行 error 并跳过这一条，场景继续播。

自动输出口同样是改机器状态：

```java
MachineEdits.setItemOutput(builder, pos, Direction.UP);         // 物品走顶面
MachineEdits.setFluidOutput(builder, pos, Direction.SOUTH, 40); // 流体走南面，40 tick 后
MachineEdits.setAutoOutput(builder, pos, Direction.NORTH);      // 两个一起设
```

设置朝向时会顺带把这一路自动输出打开（GT 的模型会给输出面画箭头，自动输出开着再多一个标记）；
机器不支持这种输出时在日志里报一行 error 并跳过。场景回退时朝向与开关都还原。
箭头画在机器的哪一面，场景里记得用 `scene.rotateCameraY(180)`（CTNH 那边的场景也是这么转的）
把镜头转过去，不然背对镜头的那几面看不见。

机器模型也能单独切成工作中的样子：

```java
MachineEdits.setWorkingModel(builder, pos, true, 20);    // 正面亮起运行中的贴图
MachineEdits.setWorkingModel(builder, pos, false, 20);   // 回到待机
```

只改模型状态（可工作机器是 `RECIPE_LOGIC_STATUS`，少数机器是 `IS_ACTIVE`），配方逻辑、进度、耗电、面板里的工作开关都原样不动；
机器模型没有工作/待机状态时在日志里报一行 error 并跳过。场景回退时模型也会还原。

## 示例场景

`bettergregtechponder:chemical_reactor_ui` 用的是 LV 化学反应釜：第一步只画界面；第二步往 1 号槽位写 64 个草方块、2 号槽位写 64 个玻璃；
第三步往 0 号储罐灌 1000 mB 水；第四步不画面板，直接用场景指令在顶面贴一条传送带覆盖板；
第五步把机器模型切成工作中的样子再切回待机；第六步只给一个配方 id，面板自己把 1 个碳粉与 4000 mB 氢气填进去、
把电路设成配方要的 1，进度条走完、1000 mB 甲烷出来。第四、五步演示的是机器改动与界面无关。

### 示例用法

```java
// LV 化学反应釜——完整实现在 ChemicalReactorUi 里
private static final MachineUI LV_UI = MachineUI.of(GTMachines.CHEMICAL_REACTOR[GTValues.LV]).scale(0.6f);
private static final MachineUI FULL_UI = MachineUI.of(GTMachines.CHEMICAL_REACTOR[GTValues.LV]).showFullUI().scale(0.6f);

public static void Common(SceneBuilder builder, SceneBuildingUtil util) {
    CreateSceneBuilder scene = new CreateSceneBuilder(builder);
    BlockPos machinePos = util.grid().at(1, 1, 1);

    // 1. 只画界面
    MachineUIs.showUI(scene, LV_UI).at(machinePos).show(120);

    // 2. 物品按机器真实槽位序号写入
    MachineUIs.showUI(scene, LV_UI).at(machinePos)
            .slot(1).withItem(new ItemStack(Items.GRASS_BLOCK, 64), 20)
            .slot(2).withItem(new ItemStack(Items.GLASS, 64), 20)
            .show(140);

    // 3. 流体同理
    MachineUIs.showUI(scene, LV_UI).at(machinePos)
            .tank(0).withFluid(new FluidStack(Fluids.WATER, 1000), 20)
            .show(140);

    // 4. 机器状态：往指定面贴覆盖板，与界面无关
    MachineEdits.placeCover(scene, machinePos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack(), 10);

    // 5. 机器状态：工作/待机模型
    MachineEdits.setWorkingModel(scene, machinePos, true, 10);
    MachineEdits.setWorkingModel(scene, machinePos, false, 10);

    // 6. 一个配方 id：入料、设电路、走进度条、出成品
    MachineUIs.showUI(scene, LV_UI).at(machinePos)
            .recipe("gtceu:chemical_reactor/sodium_bisulfate_from_salt", 10)
            .outlineProgress(20)
            .show(160);

    // 7. 机器状态：自动输出口朝向
    MachineEdits.setItemOutput(scene, machinePos, Direction.WEST, 10);
    MachineEdits.setFluidOutput(scene, machinePos, Direction.SOUTH, 10);

    // 8. 原版整套界面，配置器开关逐个套红框
    MachineUIs.showUI(scene, FULL_UI).at(machinePos)
            .outlinePowerToggle(20)
            .outlineAutoOutput(20)
            .outlineCircuitButton(20)
            .show(160);

    scene.markAsFinished();
}
```

- 游戏里：JEI 搜 "LV Chemical Reactor"，悬停按 **W**（或者 `/ponder bettergregtechponder:chemical_reactor_ui`）
- storyboard：`assets/bettergregtechponder/ponder/chemical_reactor_ui/common.nbt`（3x3 地板 + (1,1,1) 的 `gtceu:lv_chemical_reactor`）
- 本地运行的开发依赖里带了 JEI 与 JustEnoughCharacters（拼音搜索），方便从 JEI 直接开思索
- `en_us` 由 `gradlew runData` 生成到 `src/generated/resources`，key 与正文都由 Ponder 从场景脚本里收（`<modid>.ponder.<场景 id>.header|text_N`），不用手写；
  `zh_cn` 手写在 `src/main/resources/assets/bettergregtechponder/lang/`，datagen 不管它
- 示例只在开发环境注册（`BetterGregTechPonderPlugin#exampleScenesEnabled`）：正式 jar 里默认不注册，可用 `-Dbettergregtechponder.exampleScenes=true|false` 强制开关

## 目录

```
src/main/java/com/ctnh/bettergregtechponder/
├── BetterGregTechPonder.java                      mod 入口
├── client/
│   ├── BetterGregTechPonderClient.java            客户端入口，把场景插件交给 Ponder
│   └── ponder/
│       ├── MachineUIs.java            场景侧入口 showUI
│       ├── BetterGregTechPonderPlugin.java  场景注册
│       ├── machine/                   机器改动：MachineEdit / CoverChange / WorkingModelChange / MachineEditInstruction / MachineEdits
│       ├── scenes/ChemicalReactorUi.java  示例场景
│       └── ui/
│           ├── MachineUI.java            界面描述对象（缩放、画哪些 fancy 组件）
│           ├── MachineUiPlacement.java   摆放：at / pointing / slot / tank / recipe / show
│           ├── MachineUiElement.java     叠加层元素：按坐标解析、跑时间线
│           ├── MachineUiPanelBuilder.java 建面板：白名单、边界、收集槽位/储罐/进度条
│           ├── MachineUiPanel.java       面板快照 + 槽位/储罐读写
│           ├── MachineUiWrites.java      写入时间线：0 → 目标值、原样还原
│           ├── RecipeFiller.java         配方 id → 入料、成品、进度条
│           ├── MachineUiOverlay.java     speech box 与 tooltip 绘制
│           └── ShowMachineUiInstruction.java  展示与收尾指令
└── datagen/
    └── BetterGregTechPonderDatagen.java           en_us 生成，文案由 Ponder 从场景里收
```

## 许可

**GNU General Public License v3.0（GPL-3.0）**：[LICENSE](<LICENSE>)

> **BetterGregTechPonder - Copyright and License Notice**
>
> This mod is derived from the same implementation used and maintained by the CTNH (Create: New Horizon) project,
> where it lives in CTNH-Lib under the package `tech.vixhentx.mcmod.ctnhlib.client.ponder`. This repository is an
> independent distribution of that code.
>
> This mod is released under the GNU General Public License v3.0. The full text of the license follows below.
>
> Use, modification and redistribution - including source code, compiled artifacts, and any **jar-in-jar** (bundled or
> nested) dependencies inside those artifacts - must comply with the GPL-3.0. This notice and the full license text
> must be preserved when redistributing the mod or any part of it.
>
> The GPL-3.0 does **not** propagate to CTNH (Create: New Horizon) as a project. CTNH is the origin of this
> implementation: this derivation places no copyleft obligation on the CTNH project, on its source repositories, or on
> the other code it distributes, and CTNH is not required to relicense any of it.
>
> That exemption applies to the CTNH project **only**, and it is not a loophole for anyone else. Taking this code - in
> whole or in part, from this repository or from the copy inside the CTNH project, in source or compiled form - is still
> taking GPL-3.0-licensed code: such use, modification or redistribution has to comply with the GPL-3.0, including source
> disclosure and the same license for derivative works. Doing so places no obligation on the CTNH project itself.
