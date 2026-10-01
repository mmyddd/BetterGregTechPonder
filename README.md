# GT Ponder

在 Create 的思索（Ponder）场景里画出 **GregTech CEu Modern 机器自己的界面**，并按它真实的槽位序号往里放物品、灌流体。

- 针对官方 GTCEu 编译（`curse.maven:gregtechceu-modern-890405:7917773`），不依赖任何分支改动
- 纯客户端库：绘制相关的类不会被服务端加载
- 面板用 Ponder 自己的 speech box 画，指针尖指向场景里的坐标
- 槽位序号与游戏里的机器界面一致；物品数量与储罐流体量都从 0 在 1 秒内涨到目标值
- 往机器上贴覆盖板、把机器模型切成工作/待机，都是独立的场景指令（改机器状态），和界面无关；机器不支持时在日志里报错
- 给一个配方 id 就能把机器填满：输入、流体、成品各就各位，面板里的进度条自己走一遍；机器与配方对不上时报错
- 打开 Ponder 的编辑模式（`ponder-client.toml` 里的 `editingMode`）后，鼠标停在槽位上，tooltip 首行会显示该槽位在机器里的真实序号

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
默认只画标题栏、左侧页签和机器页，`showPlayerInventory()`、`showConfigurators()`、`showNavigationButtons()` 可以把其余部分打开，
缩放用 `scale(f)`，或用 `fitToPanel(0.42f)` 按 Ponder 面板宽度自适应。

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

改机器状态用独立的场景指令，不挂在界面上，因此不受上面的还原影响：

```java
MachineEdits.placeCover(builder, pos, Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack());
MachineEdits.placeCover(builder, pos, Direction.NORTH, GTCovers.PUMPS[1], 40);   // 也可以直接给定义
```

贴不上时（机器没有覆盖板容器、这一面放不了、覆盖板拒绝附着）会在日志里报一行 error 并跳过这一条，场景继续播。

机器模型也能单独切成工作中的样子：

```java
MachineEdits.setWorkingModel(builder, pos, true, 20);    // 正面亮起运行中的贴图
MachineEdits.setWorkingModel(builder, pos, false, 20);   // 回到待机
```

只改模型状态（可工作机器是 `RECIPE_LOGIC_STATUS`，少数机器是 `IS_ACTIVE`），配方逻辑、进度、耗电、面板里的工作开关都原样不动；
机器模型没有工作/待机状态时在日志里报一行 error 并跳过。场景回退时模型也会还原。

## 示例场景

`gtponder:chemical_reactor_ui` 用的是 LV 化学反应釜：第一步只画界面；第二步往 1 号槽位写 64 个草方块、2 号槽位写 64 个玻璃；
第三步往 0 号储罐灌 1000 mB 水；第四步不画面板，直接用场景指令在顶面贴一条传送带覆盖板；
第五步把机器模型切成工作中的样子再切回待机；第六步只给一个配方 id，面板自己把 2 个钠粉 + 1 个硫粉填进去、
进度条走完、3 个硫化钠出来。第四、五步演示的是机器改动与界面无关。

- 游戏里：JEI 搜 "LV Chemical Reactor"，悬停按 **W**（或者 `/ponder gtponder:chemical_reactor_ui`）
- storyboard：`assets/gtponder/ponder/chemical_reactor_ui/common.nbt`（3x3 地板 + (1,1,1) 的 `gtceu:lv_chemical_reactor`）
- 本地运行的开发依赖里带了 JEI 与 JustEnoughCharacters（拼音搜索），方便从 JEI 直接开思索
- `en_us` 由 `gradlew runData` 生成到 `src/generated/resources`，key 与正文都由 Ponder 从场景脚本里收（`<modid>.ponder.<场景 id>.header|text_N`），不用手写；
  `zh_cn` 手写在 `src/main/resources/assets/gtponder/lang/`，datagen 不管它
- 示例只在开发环境注册（`GTPonderPonderPlugin#exampleScenesEnabled`）：正式 jar 里默认不注册，可用 `-Dgtponder.exampleScenes=true|false` 强制开关

## 目录

```
src/main/java/com/ctnh/gtponder/
├── GTPonder.java                      mod 入口
├── client/
│   ├── GTPonderClient.java            客户端入口，把场景插件交给 Ponder
│   └── ponder/
│       ├── MachineUIs.java            场景侧入口 showUI
│       ├── GTPonderPonderPlugin.java  场景注册
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
    └── GTPonderDatagen.java           en_us 生成，文案由 Ponder 从场景里收
```

## 许可

GPL-3.0
