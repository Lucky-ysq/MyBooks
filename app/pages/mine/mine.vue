<template>
  <view class="page">
    <!-- 头部 -->
    <view class="header">
      <image class="avatar" src="/static/logo.png" mode="aspectFill" />
      <view class="user">
        <text class="uname">遇夏读者</text>
        <text class="usign">今天也来看几章吧</text>
      </view>
    </view>

    <!-- 菜单 -->
    <view class="menu-group">
      <view class="menu-item" @click="go('/pages/source/source')">
        <text class="m-icon">📖</text>
        <view class="m-body">
          <text class="m-title">书源管理</text>
          <text class="m-sub">添加、编辑或管理图书源</text>
        </view>
        <text class="m-arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/preference/preference')">
        <text class="m-icon">🎨</text>
        <view class="m-body">
          <text class="m-title">阅读偏好</text>
          <text class="m-sub">字号、行距、背景等默认设置</text>
        </view>
        <text class="m-arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/downloads/downloads')">
        <text class="m-icon">⬇️</text>
        <view class="m-body">
          <text class="m-title">下载管理</text>
          <text class="m-sub">离线章节、缓存空间管理</text>
        </view>
        <text class="m-arrow">›</text>
      </view>
    </view>

    <view class="menu-group">
      <view class="menu-item" @click="backup">
        <text class="m-icon">☁️</text>
        <view class="m-body">
          <text class="m-title">备份与恢复</text>
          <text class="m-sub">导出/导入书架和阅读进度</text>
        </view>
        <text class="m-arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/about/about')">
        <text class="m-icon">ℹ️</text>
        <view class="m-body">
          <text class="m-title">关于遇夏小说</text>
          <text class="m-sub">版本、服务器地址</text>
        </view>
        <text class="m-arrow">›</text>
      </view>
    </view>

    <view class="foot">
      <text>学习软件 · 个人使用 · 无商业行为</text>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {}
  },
  methods: {
    go(url) {
      uni.navigateTo({ url: url })
    },
    /** 备份与恢复：M4 实现，先占位提示 */
    backup() {
      uni.showToast({ title: '备份功能开发中（M4）', icon: 'none' })
    }
  }
}
</script>

<style>
.page {
  min-height: 100vh;
  background: #f5f6f7;
}
.header {
  background: #ffffff;
  padding: 40rpx 32rpx;
  display: flex;
  align-items: center;
}
.avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 55rpx;
  background: #f0f1f3;
}
.user { margin-left: 24rpx; }
.uname {
  font-size: 36rpx;
  font-weight: bold;
  color: #222222;
  display: block;
}
.usign {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #909399;
  display: block;
}
.menu-group {
  margin-top: 20rpx;
  background: #ffffff;
}
.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.menu-item:last-child { border-bottom: none; }
.m-icon { font-size: 40rpx; margin-right: 24rpx; }
.m-body { flex: 1; }
.m-title {
  font-size: 30rpx;
  color: #222222;
  display: block;
}
.m-sub {
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #9aa0a6;
  display: block;
}
.m-arrow {
  color: #c0c4cc;
  font-size: 36rpx;
}
.foot {
  margin-top: 60rpx;
  text-align: center;
  font-size: 22rpx;
  color: #c0c4cc;
}
</style>
