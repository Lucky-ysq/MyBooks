<script>
const downloader = require('./common/downloader.js')

export default {
  onLaunch() {
    // 首次启动初始化：服务器地址默认本机（调试），阅读偏好默认值
    if (!uni.getStorageSync('server_url')) {
      uni.setStorageSync('server_url', 'http://127.0.0.1:8080')
    }
    if (!uni.getStorageSync('reader_settings')) {
      uni.setStorageSync('reader_settings', {
        fontSize: 18,      // 字号 px
        lineHeight: 1.8,   // 行距倍数
        bgTheme: 'paper',  // 背景主题 white/paper/green/gray/black
        pageMode: 'scroll',// 翻页方式 scroll 上下滚动 / slide 左右滑动
        nightMode: false,  // 夜间模式
        keepScreenOn: true // 阅读时屏幕常亮
      })
    }
    // 恢复未完成的下载任务
    downloader.init()
  }
}
</script>

<style>
/* 全局公共样式 */
page {
  background-color: #f5f6f7;
  font-size: 28rpx;
  color: #222222;
}
</style>
