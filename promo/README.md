# RDDMP 宣传片

本目录保存 RDDMP 工程交付管理平台的 Ink Press 纸张墨印风格宣传片工程和成片。

视频使用项目真实页面截图作为产品画面，覆盖项目概览、项目计划、流程审批和平台底座四组能力。演示数据为本地脱敏数据，不包含账号、口令、Token 或生产信息。

## 渲染

```powershell
cd promo/remotion
npm install
npx remotion still src/index.ts RddmpPromo out/qa/keyframe.png --frame=150
npx remotion render src/index.ts RddmpPromo out/rddmp-product-promo-nobgm.mp4 --props=props-nobgm.json
```

成片规格为 1920x1080、30fps、约 36.2 秒。默认输出不带 BGM，仅保留转场音效；`props-bgm.json` 可用于渲染带 BGM 版本。
