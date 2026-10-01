# GT Ponder

在 Create 的思索（Ponder）场景里画出 **GregTech CEu Modern 机器真实的界面**，并让场景脚本按真实槽位序号往里放物品。

- 针对官方 GTCEu 编译（`curse.maven:gregtechceu-modern-890405:7917773`），不依赖任何分支改动
- 纯客户端库：服务端加载本 mod 不会触碰任何绘制类
- 面板用 Ponder 自己的 speech box 画，指针尖指向场景里的坐标
- 槽位序号与实机 UI 一致；放入时数量从 0 在 1 秒内叠到目标值
- 可以指定面和覆盖板，往机器上贴覆盖板；机器不支持时在日志里报错
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
        .show(200);
```

覆盖板同理，写在 `show()` 之前：

```java
MachineUIs.showUI(builder, MY_UI)
        .at(pos)
        .cover(Direction.UP, GTRegistries.COVERS.get(GTCEu.id("conveyor")))   // 也可以直接给 id
        .cover(Direction.NORTH, new ResourceLocation("gtceu", "pump"), 40)    // 第 40 tick 再贴
        .slot(0).withItem(stack, 20)
        .show(200);
```

贴不上时（机器没有覆盖板容器、这一面放不了、覆盖板拒绝附着）会在日志里报一行 error 并跳过这一条，场景继续播。
需要精确指向点时用 `at(vec)`（机器取该点所在方块）或 `at(vec, pos)`（指向点与机器分开给）。默认只画标题栏、左侧页签和机器页。`showPlayerInventory()`、`showConfigurators()`、`showNavigationButtons()`
可以把其余部分打开；缩放用 `scale(f)`，或用 `fitToPanel(0.42f)` 按 Ponder 面板宽度自适应。

## 示例场景

`gtponder:input_bus_ui` 用的是 ULV 输入总线：画一遍界面、往 0 号槽位写 64 个草方块、再在顶面贴一条传送带覆盖板。

- 游戏里：JEI 搜 "ULV Input Bus"，悬停按 **W**（或者 `/ponder gtponder:input_bus_ui`）
- storyboard：`assets/gtponder/ponder/input_bus_ui/common.nbt`（3x3 地板 + (1,1,1) 的 `gtceu:ulv_input_bus`）
- 本地运行的开发依赖里带了 JEI 与 JustEnoughCharacters（拼音搜索），方便从 JEI 直接开思索

## 目录

```
src/main/java/com/ctnh/gtponder/
├── GTPonder.java                     mod 入口
└── client/
    ├── GTPonderClient.java             客户端入口，把场景插件交给 Ponder
    └── ponder/
        ├── MachineUIs.java             场景侧入口 showUI
        ├── GTPonderPonderPlugin.java   场景注册
        ├── machine/                    时间线上的机器改动：MachineEdit / CoverChange
        ├── scenes/InputBusUi.java      示例场景
        └── ui/                         MachineUI / MachineUiPlacement / MachineUiElement / ShowMachineUiInstruction
```

## 许可

GPL-3.0
