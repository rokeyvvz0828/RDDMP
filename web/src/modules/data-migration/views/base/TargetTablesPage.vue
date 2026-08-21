<!--
  用途：数迁基础资料 - 目标表结构页
  说明：结构化资产薄页面，复用 StructuredListView 组件，传入结构化类型 TABLE_STRUCTURE 与页面标题；
        支持关键词查询、批量删除、Excel 导入/导出、JSON 编辑字段（见 StructuredListView）。
-->
<script setup lang="ts">
import StructuredListView from '../../components/StructuredListView.vue'
</script>

<template>
  <StructuredListView structured-type="TABLE_STRUCTURE" page-title="目标表结构" />
</template>
