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
    /** 备份与恢复入口 */
    backup() {
      uni.showActionSheet({
        itemList: ['导出备份（书架+进度+设置）', '从备份文件恢复'],
        success: (res) => {
          if (res.tapIndex === 0) this.exportBackup()
          else this.importBackup()
        }
      })
    },
    /** 收集要备份的数据 */
    collectBackup() {
      const data = {
        app: 'yuxia-books',
        time: Date.now(),
        bookshelf: uni.getStorageSync('bookshelf') || [],
        reader_settings: uni.getStorageSync('reader_settings') || {},
        progress: {}
      }
      for (const b of data.bookshelf) {
        const p = uni.getStorageSync('progress_' + b.id)
        if (p) data.progress[b.id] = p
      }
      return data
    },
    /** 导出：安卓写成 JSON 文件到下载目录；其他端复制到剪贴板 */
    exportBackup() {
      const shelf = uni.getStorageSync('bookshelf') || []
      if (shelf.length === 0) {
        uni.showToast({ title: '书架是空的，没什么可备份', icon: 'none' })
        return
      }
      const json = JSON.stringify(this.collectBackup())
      // #ifdef APP-PLUS
      const d = new Date()
      const pad = n => (n < 10 ? '0' + n : '' + n)
      const name = 'yuxia_backup_' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + '.json'
      plus.io.resolveLocalFileSystemURL('_downloads/', dir => {
        dir.getFile(name, { create: true }, file => {
          file.createWriter(writer => {
            writer.onwrite = () => {
              uni.showModal({
                title: '导出成功',
                content: '备份文件：下载目录/' + name + '，可复制到电脑或新手机',
                showCancel: false
              })
            }
            writer.onerror = () => uni.showToast({ title: '写入文件失败', icon: 'none' })
            writer.write(json)
          }, () => uni.showToast({ title: '创建写入器失败', icon: 'none' }))
        }, () => uni.showToast({ title: '创建文件失败', icon: 'none' }))
      }, () => uni.showToast({ title: '访问下载目录失败', icon: 'none' }))
      // #endif
      // #ifndef APP-PLUS
      uni.setClipboardData({
        data: json,
        success: () => uni.showModal({
          title: '已复制到剪贴板',
          content: '备份 JSON 已复制，请粘贴保存到备忘录等位置（App 端会直接导出文件）',
          showCancel: false
        })
      })
      // #endif
    },
    /** 恢复：安卓从下载目录选备份文件；其他端从剪贴板读 */
    importBackup() {
      // #ifdef APP-PLUS
      plus.io.resolveLocalFileSystemURL('_downloads/', dir => {
        const reader = dir.createReader()
        const files = []
        const readEntries = () => {
          reader.readEntries(entries => {
            if (entries.length === 0) {
              if (files.length === 0) {
                uni.showToast({ title: '下载目录没找到备份文件', icon: 'none' })
                return
              }
              uni.showActionSheet({
                itemList: files,
                success: (res) => this.restoreFromFile('_downloads/' + files[res.tapIndex])
              })
              return
            }
            for (const e of entries) {
              if (e.name.indexOf('yuxia_backup_') === 0 && e.name.indexOf('.json') > 0) files.push(e.name)
            }
            readEntries()
          }, () => uni.showToast({ title: '读取目录失败', icon: 'none' }))
        }
        readEntries()
      }, () => uni.showToast({ title: '访问下载目录失败', icon: 'none' }))
      // #endif
      // #ifndef APP-PLUS
      uni.getClipboardData({
        success: (res) => {
          try {
            this.restoreData(JSON.parse(res.data))
          } catch (e) {
            uni.showToast({ title: '剪贴板内容不是有效备份', icon: 'none' })
          }
        }
      })
      // #endif
    },
    restoreFromFile(path) {
      // #ifdef APP-PLUS
      plus.io.resolveLocalFileSystemURL(path, entry => {
        entry.file(file => {
          const reader = new plus.io.FileReader()
          reader.onloadend = (evt) => {
            try {
              this.restoreData(JSON.parse(evt.target.result))
            } catch (e) {
              uni.showToast({ title: '备份文件格式不对', icon: 'none' })
            }
          }
          reader.onerror = () => uni.showToast({ title: '读取文件失败', icon: 'none' })
          reader.readAsText(file, 'utf-8')
        }, () => uni.showToast({ title: '打开文件失败', icon: 'none' }))
      }, () => uni.showToast({ title: '找不到文件', icon: 'none' }))
      // #endif
    },
    /** 恢复数据：书架合并（同书不重复），进度以更新时间新的为准 */
    restoreData(data) {
      if (!data || data.app !== 'yuxia-books' || !Array.isArray(data.bookshelf)) {
        uni.showToast({ title: '不是遇夏小说的备份文件', icon: 'none' })
        return
      }
      const shelf = uni.getStorageSync('bookshelf') || []
      let added = 0, updated = 0
      for (const b of data.bookshelf) {
        const i = shelf.findIndex(x => x.id === b.id)
        let newer = false
        if (i < 0) {
          shelf.push(b)
          added++
          newer = true
        } else {
          newer = (b.lastReadTime || 0) >= (shelf[i].lastReadTime || 0)
          if (newer) {
            shelf[i] = b
            updated++
          }
        }
        const p = data.progress && data.progress[b.id]
        if (p && newer) {
          uni.setStorageSync('progress_' + b.id, p)
        }
      }
      uni.setStorageSync('bookshelf', shelf)
      if (data.reader_settings) uni.setStorageSync('reader_settings', data.reader_settings)
      uni.showModal({
        title: '恢复完成',
        content: '新增 ' + added + ' 本，更新 ' + updated + ' 本',
        showCancel: false
      })
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
