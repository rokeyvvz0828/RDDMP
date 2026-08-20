# RDDMP 产品宣传片

这是 RDDMP 的独立 Remotion 宣传片工程，使用项目真实页面截图制作 1920x1080、30fps、51 秒成片。

## 运行

```bash
npm install
npm run preview
npm run render
```

无 BGM 版本使用项目根目录中的 `props-nobgm.json`：

```bash
npx remotion render src/index.ts RddmpPromo out/rddmp-product-promo-nobgm.mp4 --props=props-nobgm.json
```

镜头素材位于 `public/assets/`，配乐来自 `video-shotcraft` 随附音频库。`out/` 为本地渲染产物，不纳入源码提交。
