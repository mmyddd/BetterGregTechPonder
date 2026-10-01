# Ponder GT UI

在 Create 的思索（Ponder）场景里画出 **GregTech CEu Modern 机器真实的界面**，并让场景脚本按真实槽位序号往里放物品。

- 针对官方 GTCEu 编译（`curse.maven:gregtechceu-modern-890405:7917773`），不依赖任何分支改动
- 纯客户端库：服务端加载本 mod 不会触碰任何绘制类
- 面板用 Ponder 自己的 speech box 画，指针尖指向场景里的坐标
- 槽位序号与实机 UI 一致；放入时数量从 0 在 1 秒内叠到目标值

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

需要精确指向点时用 `at(vec)`（机器取该点所在方块）或 `at(vec, pos)`（指向点与机器分开给）。默认只画标题栏、左侧页签和机器页。`showPlayerInventory()`、`showConfigurators()`、`showNavigationButtons()`
可以把其余部分打开；缩放用 `scale(f)`，或用 `fitToPanel(0.42f)` 按 Ponder 面板宽度自适应。

## 目录

```
src/main/java/com/ctnh/pondergtui/
├── PonderGTUI.java                     mod 入口
└── client/ponder/
    ├── MachineUIs.java                 场景侧入口 showUI
    └── ui/                             MachineUI / MachineUiPlacement / MachineUiElement / ShowMachineUiInstruction
```

## 许可

GPL-3.0
