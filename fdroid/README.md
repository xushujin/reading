# F-Droid 收录材料

这个目录是提交到 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 的元数据，
**不是**本项目运行所需的东西。不想上架 F-Droid 的话可以整个删掉。

## 目录结构

```
fdroid/metadata/
├── com.azhan.qingyue.yml                          # 构建配置
└── com.azhan.qingyue/en-US/
    ├── title.txt                                  # 应用名
    ├── short_description.txt                      # 摘要（≤ 80 字符）
    ├── full_description.txt                       # 完整描述
    └── changelogs/1.txt                           # versionCode 1 的更新日志
```

与 fdroiddata 仓库的结构一致，整个 `metadata/` 目录可以直接复制过去。

## 提交步骤

**1. Fork 并克隆 fdroiddata**

```bash
# 先在 GitLab 上 fork https://gitlab.com/fdroid/fdroiddata
git clone https://gitlab.com/<你的用户名>/fdroiddata.git
cd fdroiddata
git checkout -b com.azhan.qingyue
```

**2. 复制元数据**

```bash
cp -r /path/to/reading/fdroid/metadata/* metadata/
```

**3. 本地校验**（强烈建议，能提前发现大部分问题）

```bash
pip install fdroidserver
fdroid lint com.azhan.qingyue
fdroid readmeta
```

**4. 提交并推送**

```bash
git add metadata/com.azhan.qingyue.yml metadata/com.azhan.qingyue
git commit -m "New app: com.azhan.qingyue (轻阅)"
git push origin com.azhan.qingyue
```

**5. 在 GitLab 上开 Merge Request**

目标仓库选 `fdroid/fdroiddata`，分支 `master`。
F-Droid 的 CI 会自动跑构建检查，维护者会 review。

## 还缺什么

| 项 | 说明 |
|---|---|
| **应用图标** | 从 `app/src/main/res/` 导出 512×512 PNG，放到 `en-US/images/icon.png` |
| **截图** | 建议 3–5 张（书架、阅读页、设置页、翻页模式、夜间主题），放 `en-US/images/phoneScreenshots/1.png` 等 |
| **作者名** | `.yml` 里现在填的是 GitHub 用户名 `xushujin`，想用真名自行替换 |

截图需要装机后自己截——这是唯一我没法代劳的部分。

## 几个要注意的点

**签名是分开的。** F-Droid 用它自己的密钥签名，与 GitHub Release 里的 APK 是两套签名，
**不能互相升级**。用户从 GitHub Release 换到 F-Droid 版本必须先卸载。

**自动更新已配好。** `.yml` 里设了：

```yaml
AutoUpdateMode: Version
UpdateCheckMode: Tags
```

以后打 tag 推送后，F-Droid 会自动检测到新版本并构建，不需要再提 MR。
但**每个新 versionCode 需要一份对应的 changelog**（`en-US/changelogs/<versionCode>.txt`），
否则 F-Droid 会用空白的更新说明。

**首次审核有周期。** 提 MR 后要等维护者 review 并跑通构建，通常几天到几周；
收录之后的版本更新会快很多。

## 当前状态

- 许可证：Apache-2.0 ✅（`LICENSE` 已在仓库根目录）
- 零权限声明 ✅
- 无专有依赖 ✅（全是 AndroidX / Kotlin）
- 无签名材料时可从源码构建 ✅（已实测）
- git tag：`v1.3.12`（发布后核验）
- 每个 versionCode 的 changelog：`1.txt` … `21.txt` ✅

**所有前置条件都已满足，只差提交 MR。**
