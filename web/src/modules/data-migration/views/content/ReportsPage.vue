<!--
  用途：数迁资产内容 - 汇报材料页
  说明：文件型资产薄页面，复用 AssetListView 组件，传入资产类型 REPORT 与页面标题；
        支持关键词查询、文件上传、下载、批量移入回收站（见 AssetListView）。
-->
<script setup lang="ts">
import AssetListView from '../../components/AssetListView.vue'
</script>

<template>
  <AssetListView asset-type="REPORT" page-title="汇报材料" />
</template>
