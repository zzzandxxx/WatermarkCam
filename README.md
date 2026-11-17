# 水印打卡 WatermarkCam

一款**本地化水印相机** Android 应用：拍照自动叠加时间、地点、经纬度、天气、备注等取证水印，支持 14 种水印样式、拼图与汇报长图生成。所有数据仅保存在本机。

灵感与交互参考「今日水印相机」：**界面竖屏锁定，横握手机时仅取景画面与成片方向跟随旋转**。

## 功能特性

### 📷 水印相机
- 实时预览水印，所见即所得（预览与成片共用同一绘制引擎）
- 水印字段：标题、时间（支持到秒）、日期星期、地点、经纬度、天气、备注、打卡人、单位/项目、自定义 Logo
- **点水印上任意一行直接修改对应内容**（行级命中检测）
- 水印时间支持「拍照时刻」或「固定指定时刻」两种模式
- 整幅画面淡文字水印（平铺/单条，透明度、字号、角度、颜色可调）

### 🔄 今日水印式旋转
- App 界面始终竖屏，顶栏/快捷条不跟着转
- 横握手机：取景框自动变宽幅 4:3，相机画面在框内旋转显示，横拍出横版照片 + 横排水印
- 旋转档位 55° 防抖，边界晃动不跳档

### 🎨 14 种水印模板
经典黄条 / 白色卡片 / 深色玻璃 / 工程蓝标 / 纯文字 / 侧边竖条 / 考勤橙卡 / 质检绿单 / 深蓝商务 / 警示红条 / 科技青框 / 黑金铭牌 / 胶囊浮标 / 描边大字

主屏模板条以**真实效果缩略图**呈现（用绘制引擎实时渲染），点一下即切换。

### 🖼️ 拼图 · 汇报
- 打卡记录九宫格点选 + 相册多选导入
- **拼图**：长图拼接 / 宫格自动排布（2 张→1×2、4 张→2×2、9 张→3×3…）
- **汇报**：白底长图 = 标题 + 时间地点天气打卡人 + 汇报说明 + 逐张带拍摄时间说明的照片 + 页脚
- 生成后自动存入相册「水印打卡」文件夹，一键分享

### 📍 地点与天气
- GPS 自动定位 + 高德逆地理编码，或手动选择/搜索地点（内置全国 POI 数据）
- 自定义地点时地点名与坐标联动；天气支持联网获取或手动填写（优先级更高）

### 🗂️ 记录管理
- 本地 SQLite 记录所有拍摄（时间/地点/标题/备注）
- 相册内自动建「水印打卡」文件夹；导出原图可选

## 技术栈

- **纯 Java + AndroidX**，无 RxJava/无 Kotlin/无 Hilt，依赖仅 5 个（appcompat、core、recyclerview、exifinterface、CameraX）
- 最低支持 Android 7.0（API 24），目标 Android 14
- 无第三方图片库——拼图、水印全部由自研 Canvas 绘制引擎完成

## 构建步骤

1. 用 **Android Studio** 打开项目根目录（推荐，会自动提示安装缺失的 Gradle wrapper）；或命令行使用 Gradle 8.14 + JDK 17 直接执行 `gradle assembleDebug`
2. 签名：本仓库**不含签名密钥**。将你的 keystore 放到 `keystore/huaming.jks`，密码在 `app/build.gradle` 的 `signingConfigs.release` 中修改；不放置时 release 包为未签名状态，不影响 debug 构建与开发调试
3. 编译：

```bash
gradle assembleDebug     # 命令行方式（需本机 Gradle 8.14）
# 或在 Android Studio 中直接 Run
```

更多实现细节见 [docs/技术文档.md](docs/技术文档.md)。

## 项目结构

```
app/src/main/java/com/huaming/watermark/
├── ui/          # 相机、拼图、记录、设置等页面
│   ├── MainActivity            相机主界面（旋转跟随/模板条/水印行级编辑）
│   ├── AspectRatioLayout       取景框比例 + 画面内容旋转容器
│   ├── CollageActivity         拼图/汇报
│   └── ...
├── render/      # 水印绘制引擎（预览与成片共用）
│   ├── WatermarkPainter        14 种模板的 Canvas 绘制
│   └── WatermarkOverlayView    预览覆盖层 + 行级点击
├── model/       # WatermarkData / WatermarkStyle / WatermarkStamp
├── data/        # Prefs(SharedPreferences) / RecordStore(SQLite) / LogoStore
└── util/        # CollageMaker / ImageUtils / LocationHelper / WeatherHelper ...
```

## 免责说明

- 本应用为个人/团队内部使用场景设计（如工程巡检、考勤打卡取证）
- 内置的定位搜索 Key 有每日配额限制，正式使用请到[高德开放平台](https://lbs.amap.com/)申请自己的 Key 并在「设置」中填入
- 请遵守当地法律法规，不得用于非法取证等用途

## License

MIT
