<template>
  <view class="page">
    <!-- 书源列表 -->
    <view class="list">
      <view v-for="s in sources" :key="s.id" class="source-item">
        <view class="s-info" @click="editSource(s)">
          <view class="s-name-row">
            <text class="s-name">{{ s.name }}</text>
            <text v-if="s.isDefault" class="s-default-tag">默认</text>
          </view>
          <text class="s-url">{{ s.baseUrl }}</text>
        </view>
        <switch :checked="s.enabled" color="#E8383D" style="transform: scale(0.8)" @change="toggleEnable(s, $event)" />
      </view>
    </view>

    <!-- 添加按钮 -->
    <button class="add-btn" @click="openEditor(null)">+ 添加书源</button>
    <button class="add-btn import-btn" @click="openImport">⇩ 从订阅批量导入</button>
    <text class="tip">简单模式只需填名称和网址；个别网站结构不同时用「高级模式」改解析规则</text>
    <text class="tip">「从订阅导入」支持 Legado（「阅读」App）的订阅地址，可一次导入几十个书源</text>

    <!-- 编辑器弹层 -->
    <view v-if="editing" class="mask" @click="editing = false">
      <scroll-view scroll-y class="editor" @click.stop>
        <text class="e-title">{{ form.id ? '编辑书源' : '添加书源' }}</text>

        <text class="e-label">名称 *</text>
        <input class="e-input" v-model="form.name" placeholder="如：我的书源" />

        <text class="e-label">网址 *</text>
        <input class="e-input" v-model="form.baseUrl" placeholder="https://www.xxx.com（不带 http 也可以）" />

        <!-- 高级模式开关 -->
        <view class="e-adv-row" @click="advanced = !advanced">
          <text class="e-adv-label">高级模式（修改解析规则）</text>
          <text class="e-adv-arrow">{{ advanced ? '收起 ▴' : '展开 ▾' }}</text>
        </view>
        <view v-if="advanced">
          <text class="e-hint">规则为 JSON 格式，选择器写法：".xxx" 取文本、".xxx@href" 取属性、{{ keyPlaceholder }} 为搜索关键字占位。不确定就保持默认（笔趣阁通用规则）。</text>
          <textarea class="e-textarea" v-model="form.rules" :maxlength="-1" auto-height />
        </view>

        <view class="e-btns">
          <button size="mini" class="e-btn" @click="testSource" :disabled="testing">{{ testing ? '测试中…' : '测试' }}</button>
          <button size="mini" class="e-btn" @click="saveSource">保存</button>
          <button v-if="form.id" size="mini" class="e-btn e-del" @click="deleteSource">删除</button>
          <button v-if="form.id && !form.isDefault" size="mini" class="e-btn" @click="makeDefault">设为默认</button>
        </view>
        <text v-if="testResult" class="e-test-result">{{ testResult }}</text>
      </scroll-view>
    </view>

    <!-- 订阅导入弹层 -->
    <view v-if="showImport" class="mask" @click="showImport = false">
      <scroll-view scroll-y class="editor" @click.stop>
        <text class="e-title">从订阅批量导入</text>
        <text class="e-hint">填 Legado（「阅读」App）的书源订阅地址。后端会下载、转换后批量入库；网址已存在的书源会更新而不是重复添加。</text>

        <text class="e-label">订阅地址</text>
        <input class="e-input" v-model="importUrl" placeholder="https://.../shuyuan" />

        <view class="e-btns">
          <button size="mini" class="e-btn" @click="showImport = false">取消</button>
          <button size="mini" class="e-btn" @click="doImport" :disabled="importing">{{ importing ? '导入中…' : '开始导入' }}</button>
        </view>
        <text v-if="importResult" class="e-test-result">{{ importResult }}</text>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import api from '@/common/api.js'

export default {
  data() {
    return {
      sources: [],
      editing: false,
      advanced: false,
      testing: false,
      testResult: '',
      keyPlaceholder: '{{key}}',
      showImport: false,
      importUrl: '',
      importing: false,
      importResult: '',
      form: { id: null, name: '', baseUrl: '', rules: '' }
    }
  },
  onShow() {
    this.load()
  },
  methods: {
    load() {
      api.getSources().then(list => {
        this.sources = list || []
      }).catch(err => {
        uni.showToast({ title: String(err), icon: 'none' })
      })
    },
    /** 打开订阅导入弹层 */
    openImport() {
      this.importUrl = this.importUrl || ''
      this.importResult = ''
      this.showImport = true
    },
    /** 从订阅地址批量导入书源 */
    doImport() {
      const url = (this.importUrl || '').trim()
      if (!url) {
        uni.showToast({ title: '请填订阅地址', icon: 'none' })
        return
      }
      if (!/^https?:\/\//.test(url)) {
        uni.showToast({ title: '地址要以 http 开头', icon: 'none' })
        return
      }
      this.importing = true
      this.importResult = ''
      api.importSources(url).then(r => {
        this.importing = false
        const msg = '共 ' + r.total + ' 条：新增 ' + r.imported
          + '、更新 ' + r.updated + '、跳过 ' + r.skipped
        this.importResult = msg
        this.load()
        uni.showToast({ title: '导入完成', icon: 'success' })
      }).catch(err => {
        this.importing = false
        this.importResult = '导入失败：' + err
      })
    },
    openEditor(source) {
      this.testResult = ''
      if (source) {
        this.form = { id: source.id, name: source.name, baseUrl: source.baseUrl, rules: source.rules || '', isDefault: source.isDefault }
        this.advanced = false
      } else {
        this.form = { id: null, name: '', baseUrl: '', rules: '', isDefault: false }
        this.advanced = false
      }
      this.editing = true
    },
    editSource(s) {
      this.openEditor(s)
    },
    toggleEnable(s, e) {
      api.updateSource(s.id, { name: s.name, baseUrl: s.baseUrl, enabled: e.detail.value, rules: s.rules }).then(() => {
        this.load()
      }).catch(err => {
        uni.showToast({ title: String(err), icon: 'none' })
        this.load()
      })
    },
    /** 保存：简单模式 rules 留空即可（后端自动套模板） */
    saveSource() {
      const f = this.form
      if (!f.name.trim() || !f.baseUrl.trim()) {
        uni.showToast({ title: '名称和网址必填', icon: 'none' })
        return
      }
      const body = { name: f.name.trim(), baseUrl: f.baseUrl.trim(), rules: f.rules || '' }
      const p = f.id
        ? api.updateSource(f.id, Object.assign({ enabled: true }, body))
        : api.saveSource(Object.assign({ enabled: true }, body))
      p.then(res => {
        this.load()
        // 保存接口现在会附带自动测试结果：{ source, test: { ok, resultCount, message } }
        const t = res && res.test
        if (t && t.ok) {
          this.editing = false
          uni.showToast({ title: '已保存，测试搜到 ' + t.resultCount + ' 条结果', icon: 'success' })
        } else if (t && t.message) {
          // 保存已成功，但测试没过：保留编辑器展开显示原因，方便继续改规则
          this.testResult = t.message
          uni.showToast({ title: '已保存，但测试未通过', icon: 'none' })
        } else {
          this.editing = false
          uni.showToast({ title: '已保存', icon: 'success' })
        }
      }).catch(err => {
        uni.showToast({ title: String(err), icon: 'none' })
      })
    },
    deleteSource() {
      uni.showModal({
        title: '删除书源',
        content: '确定删除「' + this.form.name + '」？',
        confirmColor: '#E8383D',
        success: (res) => {
          if (!res.confirm) return
          api.deleteSource(this.form.id).then(() => {
            this.editing = false
            this.load()
          }).catch(err => {
            uni.showToast({ title: String(err), icon: 'none' })
          })
        }
      })
    },
    makeDefault() {
      api.setDefaultSource(this.form.id).then(() => {
        this.editing = false
        this.load()
        uni.showToast({ title: '已设为默认', icon: 'success' })
      }).catch(err => {
        uni.showToast({ title: String(err), icon: 'none' })
      })
    },
    /** 测试书源：搜一个关键字看有没有结果 */
    testSource() {
      if (!this.form.baseUrl.trim()) {
        uni.showToast({ title: '先填网址', icon: 'none' })
        return
      }
      this.testing = true
      this.testResult = ''
      const f = this.form
      const doTest = (id) => {
        api.testSource(id, '斗破').then(list => {
          this.testing = false
          this.testResult = list && list.length > 0
            ? '✓ 测试通过，搜到 ' + list.length + ' 条结果'
            : '△ 连接成功但没搜到结果，可能需要调整高级规则'
        }).catch(err => {
          this.testing = false
          this.testResult = '✗ ' + err
        })
      }
      if (f.id) {
        // 已存在的源：先保存再测，保证测的是最新规则
        api.updateSource(f.id, Object.assign({ enabled: true }, { name: f.name.trim(), baseUrl: f.baseUrl.trim(), rules: f.rules || '' }))
          .then(() => doTest(f.id))
          .catch(err => { this.testing = false; this.testResult = String(err) })
      } else {
        // 新源：先临时创建再测，测试后删除
        api.saveSource(Object.assign({ enabled: false }, { name: (f.name.trim() || '临时测试') + '（测试）', baseUrl: f.baseUrl.trim(), rules: f.rules || '' }))
          .then(res => {
            // 保存接口返回 { source, test }，兼容直接返回源对象的老结构
            const c = (res && res.source) || res
            api.testSource(c.id, '斗破')
              .then(list => {
                this.testing = false
                this.testResult = list && list.length > 0
                  ? '✓ 测试通过，搜到 ' + list.length + ' 条结果（请点保存）'
                  : '△ 连接成功但没搜到结果，可能需要调整高级规则（未保存）'
              })
              .catch(err => { this.testing = false; this.testResult = '✗ ' + err })
              .finally(() => { api.deleteSource(c.id).catch(() => {}) })
          })
          .catch(err => { this.testing = false; this.testResult = String(err) })
      }
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
.list {
  background: #ffffff;
  border-radius: 16rpx;
  overflow: hidden;
}
.source-item {
  display: flex;
  align-items: center;
  padding: 28rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.s-info { flex: 1; overflow: hidden; }
.s-name-row { display: flex; align-items: center; }
.s-name {
  font-size: 30rpx;
  font-weight: bold;
  color: #222222;
}
.s-default-tag {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: #e8383d;
  border: 1rpx solid #e8383d;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
}
.s-url {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9aa0a6;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.add-btn {
  margin-top: 32rpx;
  background: #e8383d;
  color: #ffffff;
  border-radius: 44rpx;
}
/* 「从订阅批量导入」按钮：次要动作，用描边弱化，避免和主按钮抢焦点 */
.import-btn {
  margin-top: 16rpx;
  background: #ffffff;
  color: #e8383d;
  border: 1rpx solid #e8383d;
}
.tip {
  display: block;
  margin-top: 16rpx;
  text-align: center;
  font-size: 22rpx;
  color: #c0c4cc;
  padding: 0 40rpx;
  line-height: 1.6;
}
/* 编辑器弹层 */
.mask {
  position: fixed;
  left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 99;
  display: flex;
  align-items: flex-end;
}
.editor {
  width: 100%;
  max-height: 85vh;
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.e-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #222222;
  display: block;
  margin-bottom: 24rpx;
}
.e-label {
  font-size: 26rpx;
  color: #666666;
  display: block;
  margin: 16rpx 0 8rpx;
}
.e-input {
  background: #f5f6f7;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
}
.e-adv-row {
  margin-top: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.e-adv-label { font-size: 26rpx; color: #e8383d; }
.e-adv-arrow { font-size: 24rpx; color: #909399; }
.e-hint {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #c0c4cc;
  line-height: 1.6;
  display: block;
}
.e-textarea {
  margin-top: 12rpx;
  width: 100%;
  min-height: 240rpx;
  background: #f5f6f7;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 24rpx;
  box-sizing: border-box;
  font-family: monospace;
}
.e-btns {
  margin-top: 28rpx;
  display: flex;
  justify-content: flex-end;
}
.e-btn {
  margin-left: 16rpx;
  background: #e8383d;
  color: #ffffff;
}
.e-del {
  background: #f0f1f3;
  color: #e8383d;
}
.e-test-result {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #666666;
}
</style>
