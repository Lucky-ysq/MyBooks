<template>
  <view class="page">
    <!-- 应用信息 -->
    <view class="card head">
      <image class="logo" src="/static/logo.png" mode="aspectFill" />
      <view class="app-info">
        <text class="app-name">遇夏小说</text>
        <text class="app-ver">版本 1.0.0</text>
      </view>
    </view>

    <!-- 服务器地址设置（联调/换服务器用） -->
    <view class="card">
      <view class="card-title"><text>服务器地址</text></view>
      <input class="server-input" v-model="serverUrl" placeholder="如 https://book.example.com 或 http://192.168.1.5:8080" />
      <view class="row-btns">
        <button class="btn-test" size="mini" :disabled="testing" @click="testServer">{{ testing ? '测试中…' : '测试连接' }}</button>
        <button class="btn-save" size="mini" @click="saveServer">保存</button>
      </view>
      <text class="test-result" :class="{ ok: testOk, bad: testOk === false }">{{ testResult }}</text>
      <text class="hint">手机上调试请填电脑局域网 IP，如 http://192.168.1.5:8080</text>
    </view>

    <!-- 说明 -->
    <view class="card">
      <view class="card-title"><text>说明</text></view>
      <text class="desc">遇夏小说是一个个人学习用的小说阅读软件壳，不内置任何书源；图书源由使用者在「书源管理」中自行添加（小说网站网址）。</text>
      <text class="desc">学习软件，个人使用，无商业行为。</text>
      <text class="desc link">开源仓库：gitee.com/luckyThings/MyBooks</text>
    </view>
  </view>
</template>

<script>
const api = require('@/common/api.js')

export default {
  data() {
    return {
      serverUrl: '',
      testing: false,
      testOk: null,
      testResult: ''
    }
  },
  onLoad() {
    this.serverUrl = uni.getStorageSync('server_url') || 'http://127.0.0.1:8080'
  },
  methods: {
    /** 测试后端连通性 */
    testServer() {
      const url = (this.serverUrl || '').trim().replace(/\/+$/, '')
      if (!url) {
        uni.showToast({ title: '请先填写地址', icon: 'none' })
        return
      }
      this.testing = true
      this.testResult = ''
      // 临时用填的地址测（还没保存）
      uni.request({
        url: url + '/api/health',
        method: 'GET',
        timeout: 8000,
        success: (res) => {
          this.testing = false
          const body = res.data
          if (body && body.code === 0) {
            this.testOk = true
            this.testResult = '✓ 连接成功（后端版本 ' + (body.data && body.data.version ? body.data.version : '?') + '）'
          } else {
            this.testOk = false
            this.testResult = '✗ 地址能连上，但不是遇夏小说后端'
          }
        },
        fail: () => {
          this.testing = false
          this.testOk = false
          this.testResult = '✗ 连不上，请检查地址、网络和防火墙'
        }
      })
    },
    saveServer() {
      const url = (this.serverUrl || '').trim().replace(/\/+$/, '')
      if (!url) {
        uni.showToast({ title: '地址不能为空', icon: 'none' })
        return
      }
      uni.setStorageSync('server_url', url)
      this.serverUrl = url
      uni.showToast({ title: '已保存', icon: 'success' })
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
.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}
.head {
  display: flex;
  align-items: center;
}
.logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 24rpx;
}
.app-info { margin-left: 24rpx; }
.app-name {
  font-size: 38rpx;
  font-weight: bold;
  color: #222222;
  display: block;
}
.app-ver {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #909399;
  display: block;
}
.card-title {
  margin-bottom: 20rpx;
  font-size: 30rpx;
  font-weight: bold;
  color: #222222;
}
.server-input {
  background: #f5f6f7;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 26rpx;
}
.row-btns {
  margin-top: 20rpx;
  display: flex;
}
.btn-test {
  background: #f0f1f3;
  color: #666666;
  margin-right: 16rpx;
}
.btn-save {
  background: #e8383d;
  color: #ffffff;
}
.test-result {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #9aa0a6;
}
.test-result.ok { color: #1aad19; }
.test-result.bad { color: #e8383d; }
.hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #c0c4cc;
  line-height: 1.5;
}
.desc {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: #666666;
  line-height: 1.6;
}
.link { color: #e8383d; }
</style>
