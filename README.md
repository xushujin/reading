# 轻阅 · 本地电子书阅读器

原生 Android 阅读器，Kotlin + Jetpack Compose，**零第三方解析库、零 DI 框架**。
只做一件事：把你手机里本地的 TXT / EPUB / MOBI / Markdown 读得舒服一点。

- 包名 `com.azhan.qingyue` ｜ minSdk 26（Android 8.0）｜ targetSdk 35
- 30 个 Kotlin 文件、约 5800 行
- release APK 约 **1.3 MB**

---

## 一、功能清单

### 1. 添加目录 → 按文件夹建书架

- 通过系统文件选择器（SAF）选择目录，**不需要申请任何存储权限**
- 书架分组名 = 文件夹名；一个目录 = 一个分组
- 递归扫描子目录（最深 8 层），跳过隐藏目录
- 支持格式：**TXT / EPUB / MOBI / Markdown（.md / .markdown）**
- MOBI 支持无 DRM 的 MOBI 6：**PalmDOC LZ77 压缩已用真实文件实测通过**；
  HUFF/CDIC 压缩路径未经验证（无 DRM 的样本难找），遇到时会明确报错并建议转 EPUB；
  带 DRM 的 MOBI / AZW 不支持（这是格式限制，不是实现问题）
- Markdown 是**真渲染**，不是把 `#`、`**` 当普通字符显示：标题 H1–H6、无序/有序列表、
  引用、围栏代码块、水平线、粗体/斜体/行内代码/删除线/链接（链接只显示文字）。
  表格、图片、HTML 内联不做
- 组内按**最后阅读时间倒序**，最近读过的排最前；没读过的按书名排
- 首页目录列表**默认折叠**，点分组头展开；展开状态跨页面保留（进阅读页再返回不会丢）
- 顶部「最近阅读」显示最近 3 本
- **收藏**：书籍行右侧的星标可标记收藏；首页「收藏」分组集中列出（跨书架，默认展开）
- **搜索**：顶部搜索框按书名 / 文件名 / 作者实时过滤，跨书架扁平列出结果
- 重复添加同一目录不会建第二条记录，直接重扫

### 2. 阅读

两种阅读方式，随时切换：

- **滚动**：竖向连续滚动，配自动滚屏
- **翻页**：左右翻页，正文按屏幕尺寸预分页

**阅读位置用「正文字符偏移」记录，不是页码** —— 切换阅读方式、改字号行距、换设备，
位置都不会丢。

其他：

- 目录跳转：EPUB 按 spine 章节；TXT 按章节标题正则识别
- 进度保存做了防抖（停顿 600ms 才落库），快速滑动不会疯狂写库

### 3. 排版与主题

- 五套主题：**羊皮纸 / 白天 / 护眼绿 / 夜间 / 纯黑**
- 可调：字号 13–26sp、行距 1.2–2.4、段间距 4–28dp、左右边距 10–44dp
- 修改后实时重排

### 4. 自动滚屏 / 自动翻页

- 滚动模式：按帧推进，速度与屏幕刷新率无关，**0.3x – 7x**
- 翻页模式：按间隔自动翻页（1x ≈ 4 秒/页）
- 手指触碰后暂停 2 秒再自动恢复，避免误触打断阅读节奏
- 滚到末尾自动停止
- 工具栏收起后，底部仍保留一条「停止」入口（占位式，不浮在正文上）

### 5. 防熄屏（双闸门）

需求本身是矛盾的：既要阅读时不熄屏，又要避免「看着看着睡着，亮一整夜」。
用两条互相独立的闸门来解：

| 闸门 | 触发条件 | 行为 |
|---|---|---|
| **无操作** | 手动阅读时 N 分钟无触摸（默认 10 分钟） | 解除常亮，交还系统正常熄屏 |
| **连续亮屏** | 连续亮屏超上限（默认 30 分钟），手动/自动都算 | 停自动滚屏 + 解除常亮 + 弹窗询问 |

**第二条是必需的**：自动滚屏时用户本来就不碰屏幕，「无操作」永远为假，
只靠第一条根本拦不住「开着自动滚屏睡到天亮」。

三种模式可选：关闭 / 智能（推荐）/ 始终常亮。
用 `View.keepScreenOn` 实现，**不需要 WAKE_LOCK 权限**。

---

## 二、工程结构

```
reading/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml           # 版本集中管理
├── gradlew / gradlew.bat / gradle/wrapper/
├── .github/workflows/build-apk.yml     # tag 触发构建 + 挂 Release
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                             # 主题 + 自适应图标（纯矢量，无二进制图）
    └── java/com/azhan/qingyue/
        ├── QingYueApp.kt                # 手写依赖容器（唯一的"注入"处）
        ├── MainActivity.kt
        ├── data/
        │   ├── local/                   # Room：shelves / books 两张表
        │   │   ├── Entities.kt
        │   │   ├── LibraryDao.kt
        │   │   └── QingYueDatabase.kt
        │   ├── parser/
        │   │   ├── BookModels.kt        # BookDocument：段落流 + 章节索引 + 字符偏移
        │   │   ├── TextUtils.kt         # 编码探测 / HTML 转纯文本 / 章节标题规则
        │   │   ├── TxtParser.kt
        │   │   ├── EpubParser.kt        # 零依赖：ZipInputStream + XmlPullParser
        │   │   ├── MobiParser.kt        # 零依赖：PDB 容器 + PalmDOC / HUFF-CDIC 解压
        │   │   └── MarkdownParser.kt    # 块级 + 行内样式 → RichBlock
        │   ├── prefs/SettingsStore.kt   # DataStore：主题 / 排版 / 常亮策略
        │   └── repo/
        │       ├── LibraryRepository.kt # SAF 扫描 + 书架排序
        │       └── BookRepository.kt    # 加载 / 进度 / 封面
        ├── ui/
        │   ├── QingYueRoot.kt           # 导航（3 个页面，字符串路由）
        │   ├── theme/                   # 五套阅读配色 + 亮暗外壳配色
        │   ├── neu/Neumorphic.kt        # 拟态设计系统（NeuSurface / NeuButton / 凹槽）
        │   ├── bookshelf/               # 书架页（分组 / 折叠）
        │   ├── reader/
        │   │   ├── ReaderScreen.kt      # 滚动（ScrolledReader）/ 翻页（PagedReader）
        │   │   ├── BookPaginator.kt     # 按行装箱预分页
        │   │   ├── ReaderSettingsSheet.kt
        │   │   ├── ChapterSheet.kt
        │   │   └── ReaderViewModel.kt
        │   └── settings/SettingsScreen.kt
        └── util/
            ├── ScreenAwake.kt           # 防熄屏双闸门守卫
            └── Formatters.kt
```

---

## 三、构建

### 环境要求

- JDK 17+（AGP 8.7.3 要求）
- Android SDK：`platforms;android-35`、`build-tools;35.0.0`

### 命令行

```bash
cd /Users/wisdom/workbuddy-ai/reading

export JAVA_HOME="$HOME/.workbuddy-ai/toolchain/jdk-17.0.20.1+1/Contents/Home"
export ANDROID_HOME="$HOME/.workbuddy-ai/toolchain/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleRelease   # 1.3 MB，装机用
./gradlew :app:assembleDebug     # 16.8 MB，含完整符号，便于看崩溃堆栈
```

> 上面两个路径是本机工具链位置。其他人用 Android Studio 自带的 JDK/SDK，
> 或在 `local.properties` 里写 `sdk.dir=/path/to/Android/sdk` 即可。

产物：

| 包 | 路径 | 体积 |
|---|---|---|
| release（推荐） | `app/build/outputs/apk/release/QingYue-1.2.0-release.apk` | **1.3 MB** |
| debug（便于看崩溃堆栈） | `app/build/outputs/apk/debug/QingYue-1.2.0-debug.apk` | 16.7 MB |

装到手机：

```bash
adb install -r app/build/outputs/apk/release/QingYue-1.2.0-release.apk
```

也可以直接把 APK 传到手机上点击安装（需要允许「安装未知来源应用」）。

### 签名

release 包用 `keystore/qingyue-release.jks` 签名：

- alias：`qingyue`
- store / key 密码：`qingyue2026`

**这是自签名测试证书，个人自用够用；要上架应用商店请换成你自己的 keystore 并妥善保管**
（丢了就没法覆盖升级）。

> 签名材料**不入库**：`.gitignore` 已忽略 `keystore/`、`keystore.properties`、
> `local.properties`、`build/`、`app/build/`、`*.apk`。

### GitHub Actions 自动编译

仓库自带 `.github/workflows/build-apk.yml`：**打 tag 才触发**（也可在 Actions 页手动触发）。

```bash
git tag v1.2.0          # 需以 v 开头，建议与 versionName 一致，如 v1.2.0 / v1.2.1
git push origin v1.2.0  # 推上去即触发编译
```

产物有两个去处：

- **GitHub Release**（仅 tag 构建）：自动创建/更新对应 tag 的 Release，并把 APK
  作为附件挂上去，文件名形如 `QingYue-v1.2.0.apk`
  （**附件名必须纯 ASCII**：GitHub 会删掉 Release 资源名里的非 ASCII 字符）。
  已存在同名 Release 时会**覆盖**附件（`gh release upload --clobber`），可安全重跑。
- **Artifact**（tag 与手动触发都有）：名为 `qingyue-release-apk`，保留 90 天。

> 普通 `git push` 到 `main` **不会**触发编译（避免每次提交都跑一次构建）。
> 另注：GitHub 的 `if:` 表达式里不能用 `secrets` 上下文，所以工作流把
> 「有没有配密钥」的判断放在 shell 里做，secrets 只通过 `env` 注入。

签名有两种路径（决定 APK 能否安装）：

- **方式 A（推荐）**：仓库 `Settings → Secrets → Actions` 加 `KEYSTORE_BASE64`
  （本地执行 `base64 -i keystore/qingyue-release.jks` 得到）。
  APK 用与你本地**同一个** keystore 签名，版本更新可直接覆盖安装。
- **方式 B（零配置兜底）**：没配密钥时，工作流自动用 Android debug keystore 签名，
  APK **一样能安装**；但每次 CI 密钥不同，覆盖安装前需先卸载旧包。

`app/build.gradle.kts` 的 release signingConfig 优先读环境变量 `KEYSTORE_PATH` /
`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（CI 走这条），未设置则回落到
`keystore.properties`（本地走这条）。两条都没有时不配置签名 ——
release 会出未签名包，但 `assembleDebug` 仍能构建，**别人 clone 下来什么都不配也能跑**。

### Android Studio

直接 Open 项目根目录，等待 Gradle Sync 完成即可运行。

---

## 四、几个关键设计

### 1. 阅读位置用「字符偏移」而不是页码

进度以**正文字符偏移**表示。滚动和翻页两种模式共用同一个坐标系，
所以切换阅读方式、改字号行距、换设备，位置都不会丢。

代价是每次定位要做一次二分查找（`paragraphIndexForOffset`），可以忽略。

### 2. 测量与渲染必须共用同一份定义

翻页模式的分页高度是照着 `TextMeasurer` 的**测量结果**算的。
只要渲染时的字号、行距、首行缩进有一处不同，页面就会溢出或留白。

所以 `readerTextStyle()` / `readerSpacingBefore()` 是**测量与渲染共用**的顶层函数。
同理，`PageContent` 的内边距必须挂在外层 `BoxWithConstraints` 上 ——
否则分页会按「未扣除内边距」的高度计算，每页多塞一两行，最后一行就被切掉半截。

> 这类 bug 编译期发现不了，只能靠肉眼看出「半截行」这种细节。

### 3. EPUB 解析零第三方依赖

`ZipInputStream` 读条目 → `META-INF/container.xml` 找 OPF → 解析 manifest / spine →
按 spine 顺序把 XHTML 剥成纯文本。用系统 `XmlPullParser`，不引 epublib。

封面单独抽出来存 `filesDir/covers/`，用 `inSampleSize` 降采样后再落盘。

### 4. MOBI 解析的两个坑

MOBI 是 Palm Database 容器，正文用 LZ77 压缩。实现时踩了两个坑，都是拿真实文件试出来的：

- **输出缓冲必须跨 record 累积** —— LZ77 的 `dist` 会引用前一个 record 的字节，
  每个 record 单独起缓冲会在第一个跨块引用处越界。
- **每个 record 最多产出 `recordSize`（通常 4096）字节就该停** —— 压缩数据尾部还有 padding，
  继续解压会把 padding 当指令，吐出 `\x00\x83\x80\x80\x85` 这类杂散字节，
  总长度也会比 `textLength` 多出两千多字节。

验证方式：Project Gutenberg 的《Pride and Prejudice》（527 KB，PalmDOC 压缩）解压出
835,642 字节，与头部声明的 `textLength` 完全一致，尾部是干净的 `</body></html>`。

### 5. Markdown 为什么要先产出「块」而不是纯文本

其他格式（TXT / EPUB / MOBI）的解析终点都是纯文本，样式由阅读页统一给。
Markdown 不行 —— 它的样式信息在切段**之前**就存在了（`#` 是标题、`>` 是引用、
` ``` ` 是代码块）。如果先拍平成纯文本再切段，这些信息就丢了，用户只会看到一堆 `#` 和 `**`。

所以 `MarkdownParser` 直接产出 `RichBlock`（文本 + 块级样式 + 行内样式区间），
再由 `BookDocumentBuilder` 转成段落流。这样其他三种格式的切分逻辑一行都不用改。

行内样式用「字符区间」而不是 `AnnotatedString` 表示，是为了让 `data/parser` 层
保持纯 Kotlin、不依赖 Compose —— 渲染时才转成 `AnnotatedString`。

### 6. TXT 编码探测

BOM → 严格模式 UTF-8（`CodingErrorAction.REPORT`）→ GB18030 兜底。
比引 juniversalchardet 轻得多，对中文书源的准确率足够。

### 7. 拟态 UI 的物理约束

元素与背景同色，只靠「左上受光 + 右下背光」两个方向的阴影塑造体积。
所以每套主题必须**同时**给出 highlight / shadow 两色 —— 只给一个 elevation 做不出来。

- 外阴影画在背景色**之下**，内阴影画在背景色**之上**，顺序写反就看不见
- 拟态元素自带 `padding(elevation)` 预留阴影空间，占位比视觉面积大一圈是这套风格的固有代价
- 硬件加速下 `setShadowLayer` 对非文本绘制从 **API 28** 起才生效，
  更低版本会退化成扁平卡片（功能不受影响）

### 8. 应用外壳与阅读页用不同配色

外壳（书架 / 设置）只分亮 / 暗，阅读页才用完整阅读主题 ——
否则护眼绿、羊皮纸会铺满整个 App，书架页会显得脏。

---

## 五、已知限制

- **拟态阴影**在 Android 9（API 28）以下会退化成扁平卡片（`setShadowLayer` 的硬件加速限制）。
  功能不受影响，只是不好看。想彻底避免可以把 `minSdk` 提到 28。
- **EPUB 兼容性**按标准（EPUB 2 / 3）实现，对排版混乱的第三方书源未做充分验证。
- **MOBI 的 HUFF/CDIC 压缩**路径未经验证（无 DRM 的样本很难找）。遇到时会报明确错误
  并建议转 EPUB，**不会吐乱码**。PalmDOC 压缩（老书、Gutenberg 下载的）已实测通过。
- **带 DRM 的 MOBI / AZW** 无法解析 —— 这是格式限制，而且绕过 DRM 这件事本身也不该做。
- **TXT 章节识别**靠正则匹配「第 X 章」这类标题；没有章节标记的 TXT 会作为单章处理，
  不影响阅读，只是目录里只有一条。
- **超大文件**：超过 64 MB 的电子书会拒绝加载（`BookRepository.MAX_FILE_BYTES`）。
- 应用**不跟随系统深色模式**，配色完全由阅读主题控制。

---

## 六、验证状态

**已验证：**

- `assembleDebug` / `assembleRelease` 均构建通过、零警告，签名经 `apksigner` 校验
- **MOBI 解析用真实文件跑通**（`MobiParserTest`，Project Gutenberg 的 527 KB 样本）：
  解出书名 `Pride and Prejudice`、作者 `Jane Austen`、63 个章节、835,642 字节正文，
  与头部声明的 `textLength` 完全一致，无空字节污染
- **Markdown 各块级 / 行内样式有单测覆盖**（`MarkdownParserTest`）：断言章节切分、
  9 种块级样式、4 种行内样式、样式区间不越界、链接 URL 被丢弃

**未验证：**

- **整机运行** —— 尚未在真机或模拟器上做过功能与视觉复核
- MOBI 的 HUFF/CDIC 压缩路径
- EPUB 对排版混乱的第三方书源的兼容性

建议装机后重点确认这几处：

- **翻页模式的分页高度** —— 每页底部是否被完整利用，有无半截行或异常留白
- **拟态阴影的实际观感** —— 尤其纯黑主题下对比度天然很弱
- **自动滚屏 7x 的速度手感**（基准值 10dp/s 是按「中文 400 字/分钟」估的）

---

## 七、许可

本项目采用 **[Apache License 2.0](LICENSE)** 授权。

可自由使用、修改、分发（包括商用），需保留版权声明与许可副本。

> 版权行请按需替换为你自己的名字，例如在 `NOTICE` 或源文件头写
> `Copyright 2026 <你的名字>`。
