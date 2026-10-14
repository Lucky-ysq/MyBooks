<template>
  <view class="page">
    <view class="group">
      <view class="row">
        <text class="label">字号</text>
        <view class="adjust">
          <text class="adj-btn" @click="adjustFont(-1)">A-</text>
          <text class="adj-val">{{ settings.fontSize }}</text>
          <text class="adj-btn" @click="adjustFont(1)">A+</text>
        </view>
      </view>
      <view class="row">
        <text class="label">行距</text>
        <slider class="slider" :min="12" :max="24" :step="1" :value="lineHeight10" activeColor="#E8383D" block-size="20" @change="onLineHeight" />
        <text class="val">{{ settings.lineHeight.toFixed(1) }}</text>
      </view>
    </view>

    <view class="group">
      <view class="row">
        <text class="label">背景</text>
        <view class="themes">
          <view
            v-for="(t, key) in THEME_MAP"
            :key="key"
            class="theme-dot"
            :class="{ active: settings.bgTheme === key && !settings.nightMode }"
            :style="{ background: t.bg }"
            @click="pickTheme(key)"
          ></view>
        </view>
      </view>
      <view class="row">
        <text class="label">翻页</text>
        <view class="modes">
          <text class="mode-btn" :class="{ on: settings.pageMode === 'scroll' }" @click="setPageMode('scroll')">上下滚动</text>
          <text class="mode-btn" :class="{ on: settings.pageMode === 'slide' }" @click="setPageMode('slide')">左右滑动</text>
        </view>
      </view>
    </view>

    <view class="group">
      <view class="row">
        <text class="label">夜间模式</text>
        <switch :checked="settings.nightMode" color="#E8383D" style="transform: scale(0.8)" @change="onNight" />
      </view>
      <view class="row">
        <text class="label">阅读时常亮</text>
        <switch :checked="settings.keepScreenOn" color="#E8383D" style="transform: scale(0.8)" @change="onKeepScreen" />
      </view>
    </view>

    <!-- 效果预览 -->
    <view class="preview" :style="{ background: theme.bg }">
      <text :style="{ color: theme.text, fontSize: settings.fontSize + 'px', lineHeight: settings.lineHeight, textIndent: '2em' }">
        预览：夏夜的晚风翻动着书页，少年抬起头，望见远山如黛、星河低垂，故事正讲到第无数次重逢。
      </text>
    </view>
  </view>
</template>

<script>
/** 与阅读页保持一致的主题表 */
const THEME_MAP = {
  white: { bg: '#FFFFFF', text: '#222222' },
  paper: { bg: '#F5EFDC', text: '#3B3226' },
  green: { bg: '#C7EDCC', text: '#2C3A2E' },
  gray:  { bg: '#2B2B2B', text: '#B8B8B8' },
  black: { bg: '#000000', text: '#9E9E9E' }
}

export default {
  data() {
    return {
      THEME_MAP: THEME_MAP,
      settings: { fontSize: 18, lineHeight: 1.8, bgTheme: 'paper', pageMode: 'scroll', nightMode: false, keepScreenOn: true }
    }
  },
  computed: {
    theme() {
      return THEME_MAP[this.settings.nightMode ? 'black' : this.settings.bgTheme] || THEME_MAP.paper
    },
    lineHeight10() {
      return Math.round(this.settings.lineHeight * 10)
    }
  },
  onLoad() {
    const s = uni.getStorageSync('reader_settings')
    if (s) this.settings = s
  },
  methods: {
    save() {
      uni.setStorageSync('reader_settings', this.settings)
    },
    adjustFont(d) {
      const v = this.settings.fontSize + d
      if (v >= 12 && v <= 36) {
        this.settings.fontSize = v
        this.save()
      }
    },
    onLineHeight(e) {
      this.settings.lineHeight = e.detail.value / 10
      this.save()
    },
    pickTheme(key) {
      this.settings.bgTheme = key
      this.settings.nightMode = false
      this.save()
    },
    setPageMode(m) {
      this.settings.pageMode = m
      this.save()
    },
    onNight(e) {
      this.settings.nightMode = e.detail.value
      this.save()
    },
    onKeepScreen(e) {
      this.settings.keepScreenOn = e.detail.value
      this.save()
    }
  }
}
</script>

<style>
.page {
  min-height: 100vh;
  background: #f5f6f7;
  padding: 24rpx;
}
.group {
  background: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  padding: 8rpx 32rpx;
}
.row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  border-bottom: 1rpx solid #f5f5f5;
}
.row:last-child { border-bottom: none; }
.label {
  width: 140rpx;
  font-size: 30rpx;
  color: #222222;
}
.adjust { display: flex; align-items: center; }
.adj-btn {
  width: 88rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border: 1rpx solid #e0e0e0;
  border-radius: 10rpx;
  font-size: 28rpx;
  color: #222222;
}
.adj-val {
  width: 100rpx;
  text-align: center;
  font-size: 28rpx;
}
.slider { flex: 1; margin: 0 16rpx; }
.val { font-size: 26rpx; min-width: 70rpx; text-align: right; }
.themes { display: flex; flex: 1; justify-content: space-around; }
.theme-dot {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(128, 128, 128, 0.5);
}
.theme-dot.active { border: 4rpx solid #e8383d; }
.modes { display: flex; flex: 1; justify-content: space-around; }
.mode-btn {
  padding: 12rpx 28rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 10rpx;
  font-size: 26rpx;
  color: #222222;
}
.mode-btn.on {
  color: #e8383d;
  border-color: #e8383d;
}
.preview {
  border-radius: 16rpx;
  padding: 32rpx;
  min-height: 200rpx;
}
</style>
