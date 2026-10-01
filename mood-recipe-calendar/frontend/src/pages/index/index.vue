<script lang="ts">
import { STATIC_BASE_URL } from '@/utils/assets'

interface HeroGuozai {
  img: string
  name: string
}

/** 全部锅仔形象池（点击切换时遍历全部，不受时间段限制）。提到模块作用域，避免每次组件实例化重建 */
const ALL_HERO_GUOZAI: HeroGuozai[] = [
  { img: STATIC_BASE_URL + '/static/guozai/action_01_bowl.png', name: '端碗锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_02_soup.png', name: '喝汤锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_06_glasses.png', name: '学者锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_07_empty.png', name: '空空锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_08_peek.png', name: '探头锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png', name: '庆祝锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_10_thinking.png', name: '思考锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_03_camera.png', name: '相机锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_12_heart.png', name: '比心锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_13_wave.png', name: '挥手锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_14_clap.png', name: '鼓掌锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_15_sleepy.png', name: '困困锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_16_chopsticks.png', name: '干饭锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_17_full.png', name: '饱饱锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_18_cheer.png', name: '加油锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_19_kungfu.png', name: '功夫锅仔' },
  { img: STATIC_BASE_URL + '/static/guozai/action_20_panda.png', name: '熊猫锅仔' },
]
/** 时间段 → 初始锅仔（与寄语呼应，仅用于首次展示） */
const HERO_GUOZAI_BY_PERIOD: Record<string, HeroGuozai[]> = {
  morning: [
    { img: STATIC_BASE_URL + '/static/guozai/action_01_bowl.png', name: '端碗锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_13_wave.png', name: '挥手锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_02_soup.png', name: '端锅锅仔' },
  ],
  noon: [
    { img: STATIC_BASE_URL + '/static/guozai/action_02_soup.png', name: '喝汤锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_16_chopsticks.png', name: '干饭锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_17_full.png', name: '饱饱锅仔' },
  ],
  afternoon: [
    { img: STATIC_BASE_URL + '/static/guozai/action_10_thinking.png', name: '思考锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_14_clap.png', name: '鼓掌锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_12_heart.png', name: '比心锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_20_panda.png', name: '熊猫锅仔' },
  ],
  evening: [
    { img: STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png', name: '庆祝锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_18_cheer.png', name: '加油锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_06_glasses.png', name: '学者锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_19_kungfu.png', name: '功夫锅仔' },
  ],
  late: [
    { img: STATIC_BASE_URL + '/static/guozai/action_08_peek.png', name: '探头锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_15_sleepy.png', name: '困困锅仔' },
    { img: STATIC_BASE_URL + '/static/guozai/action_07_empty.png', name: '空空锅仔' },
  ],
}
const weekCN = ['日', '一', '二', '三', '四', '五', '六']
function getPeriod(hour: number) {
  return hour < 5 ? 'late' : hour < 11 ? 'morning' : hour < 15 ? 'noon' : hour < 18 ? 'afternoon' : hour < 22 ? 'evening' : 'late'
}
</script>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { ensureLogin } from '../../utils/login'
import { toastError } from '../../utils/toast'
import { fetchCompanionMessage, fetchRecordsByMonth, type CompanionMessage, type RecordItem } from '../../api/records'
import { fetchRecipeQuota, type UsageQuotaView } from '../../api/recipes'
import { fetchDailyBoard, refreshDailyBoard, type DailyBoard } from '../../api/dailyMenu'
import { fetchFridgeSummary, type FridgeSummary } from '../../api/fridge'
import { saveCookingDraft } from '../../utils/cookingDraft'

definePage({ name: 'home', layout: 'tabbar', style: { navigationStyle: 'custom', navigationBarTitleText: '首页' } })
const router = useRouter()
const isBouncing = ref(false)
function bounceGuozai() {
  isBouncing.value = true
  setTimeout(() => {
    isBouncing.value = false
    if (companion.value.actionTarget === 'meal-agent') {
      router.push({ name: 'meal-agent', query: { prompt: companion.value.actionPrompt || companion.value.actionText } })
      return
    }
    router.push({ name: 'mood' })
  }, 400)
}

/** 当前时间，每次 onShow 刷新，避免跨天/跨月后日期与日历停留在打开时刻 */
const now = ref(new Date())
const dateTitle = computed(() => {
  const d = now.value
  return `${d.getMonth() + 1}月${d.getDate()}日 周${weekCN[d.getDay()]}`
})
const currentMonth = computed(() => `${now.value.getFullYear()}-${String(now.value.getMonth() + 1).padStart(2, '0')}`)
/** 初始按时间段选一个锅仔，找到它在全局池中的索引 */
const initialPeriodPool = HERO_GUOZAI_BY_PERIOD[getPeriod(now.value.getHours())] || HERO_GUOZAI_BY_PERIOD.morning
const initialGuozai = initialPeriodPool[now.value.getDate() % initialPeriodPool.length]
const initialGlobalIndex = Math.max(0, ALL_HERO_GUOZAI.findIndex(g => g.img === initialGuozai.img))
const heroGuozaiIndex = ref(initialGlobalIndex)
const heroGuozai = computed(() => ALL_HERO_GUOZAI[heroGuozaiIndex.value % ALL_HERO_GUOZAI.length])
const isHeroCycling = ref(false)
function cycleHeroGuozai() {
  if (isHeroCycling.value) return
  isHeroCycling.value = true
  heroGuozaiIndex.value = (heroGuozaiIndex.value + 1) % ALL_HERO_GUOZAI.length
  setTimeout(() => { isHeroCycling.value = false }, 400)
}
const monthRecords = ref<RecordItem[]>([])
const companion = ref<CompanionMessage>(localCompanion(now.value.getHours()))
const recipeQuota = ref<UsageQuotaView>()
const fridgeSummary = ref<FridgeSummary>()
const recipeQuotaText = computed(() => {
  const quota = recipeQuota.value
  if (!quota) return '抽一道今日治愈菜'
  return quota.member ? `会员今日还可推荐 ${quota.remaining} 次` : `今日还可推荐 ${quota.remaining}/3 次`
})
// 同日历一致：一天可能有多条记录，存为数组而非单条（避免多余记录被静默覆盖）
const monthRecordMap = computed(() => {
  const map = new Map<number, RecordItem[]>()
  monthRecords.value.forEach((record) => {
    const day = Number.parseInt(record.recordDate?.split('-')[2] || '0', 10)
    if (day > 0) {
      const list = map.get(day)
      if (list) list.push(record)
      else map.set(day, [record])
    }
  })
  return map
})
const currentMonthLabel = computed(() => `${now.value.getMonth() + 1}月食光`)
const latestMonthRecord = computed(() => [...monthRecords.value].sort((a, b) => b.recordDate.localeCompare(a.recordDate))[0])
const calendarMemory = computed(() => {
  const record = latestMonthRecord.value
  if (!record) return '这个月的第一顿，锅仔等你留下来。'
  const day = Number.parseInt(record.recordDate.split('-')[2] || '0', 10)
  return `${day}号的${record.dishName}，锅仔还记得。`
})
const calCells = computed(() => {
  const year = now.value.getFullYear()
  const month = now.value.getMonth()
  const firstDay = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells: ({ d: number; isToday: boolean; records: RecordItem[]; count: number } | null)[] = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    const rs = monthRecordMap.value.get(d) || []
    cells.push({ d, isToday: d === now.value.getDate(), records: rs, count: rs.length })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
})
const loading = ref(false)
async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    await ensureLogin()
    // 关键数据（决定「食光坐标」数量）先独立加载；quota/同伴语/今日餐板的失败必须隔离，
    // 不能因为任何一项 400 就把已加载的本月记录清空（曾经因 /recipes/quota 400 导致首页显示 0 个坐标）
    monthRecords.value = await fetchRecordsByMonth(currentMonth.value)
    fetchRecipeQuota().then((q) => { recipeQuota.value = q }).catch(() => {})
    fetchFridgeSummary().then((value) => { fridgeSummary.value = value }).catch(() => {})
    loadCompanion().catch(() => {})
    loadTodayBoard().catch(() => {})
  } catch (e: any) {
    if (e?.message !== 'NOT_LOGGED_IN')
      toastError(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function localCompanion(hour: number): CompanionMessage {
  if (hour >= 5 && hour < 11) return { greeting: '早上好，先把自己照顾好', message: '早饭不用复杂，热乎顺口就很好。', insight: '锅仔会慢慢记住你的口味', actionText: '告诉我现在的心情' }
  if (hour < 15) return { greeting: '到饭点了，别让肚子等太久', message: '忙归忙，午饭还是要认真吃。', insight: '每次选择，都让我更懂你', actionText: '告诉我现在的心情' }
  if (hour < 18) return { greeting: '下午好，想想今晚吃什么', message: '提前选好，饿的时候就不用匆忙决定。', insight: '锅仔正在学习你的饭点', actionText: '看看今天吃什么' }
  if (hour < 22) return { greeting: '晚上好，今天辛苦啦', message: '先用一顿合胃口的饭，把自己稳稳接住。', insight: '你的口味和忌口，我都会记得', actionText: '告诉我现在的心情' }
  return { greeting: '夜深了，吃点轻松温暖的', message: '选点清淡省事的，吃完也早点休息。', insight: '晚睡的时候，锅仔也在', actionText: '看看适合夜晚的菜' }
}

async function loadCompanion() {
  const current = new Date()
  const hour = current.getHours()
  const period = hour < 5 ? 'late' : hour < 11 ? 'morning' : hour < 15 ? 'noon' : hour < 18 ? 'afternoon' : hour < 22 ? 'evening' : 'late'
  const cacheKey = `festival-v1-${current.getFullYear()}-${current.getMonth() + 1}-${current.getDate()}-${period}`
  try {
    const cached = uni.getStorageSync('mrc_companion_message')
    if (cached?.key === cacheKey) {
      companion.value = cached.value
      return
    }
    const localDate = `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, '0')}-${String(current.getDate()).padStart(2, '0')}`
    const value = await fetchCompanionMessage(hour, localDate)
    companion.value = value
    uni.setStorageSync('mrc_companion_message', { key: cacheKey, value })
  } catch {
    companion.value = localCompanion(hour)
  }
}
/** 今日菜单（TodayBoard）：打开即有的零输入答案；加载失败静默，不影响首屏其余内容。 */
const todayBoard = ref<DailyBoard>()
const boardRefreshing = ref(false)
async function loadTodayBoard() {
  try {
    todayBoard.value = await fetchDailyBoard()
  }
  catch {
    todayBoard.value = undefined
  }
}
async function refreshBoard() {
  if (boardRefreshing.value) return
  boardRefreshing.value = true
  try {
    todayBoard.value = await refreshDailyBoard()
  }
  catch (e: any) {
    toastError(e, '今天没有别的菜可换啦')
  }
  finally {
    boardRefreshing.value = false
  }
}
function goCookToday() {
  const board = todayBoard.value
  if (!board?.recipe?.id)
    return
  // 今日菜单已经是一道确定的菜，直接把它交给做菜智能体，不能再次进入推荐页。
  saveCookingDraft(board.recipe, '满足')
  router.push({ name: 'cooking' })
}

onShow(() => {
  now.value = new Date()
  loadData()
})
const MOODS = ['开心', '平静', '疲惫', '焦虑', '难过', '嘴馋', '低落', '想家', '期待', '满足', '得意', '害羞']
function gotoLucky() { router.push({ name: 'recipe', params: { mood: MOODS[Math.floor(Math.random() * MOODS.length)], random: '1' } }) }
function goto(name: string, q?: Record<string, string>) { router.push({ name, query: q || {} }) }
function openFridge() { router.push({ name: 'fridge' }) }
function openCalendarCell(cell: { d: number; records: RecordItem[] }) {
  if (cell.records.length) router.push({ name: 'calendar', params: { day: String(cell.d) } })
  else router.pushTab({ name: 'record' })
}
</script>

<template>
  <view class="home mrc-hero">
    <wd-navbar title="心情菜谱日历" safe-area-inset-top  custom-style="background-color: transparent !important;" />
    <!-- 首页不展示整屏缺省图：直接渲染真实内容，数据就绪后响应式更新 -->
      <view class="home-hero" :class="{ 'home-hero--festival': companion.scene && companion.scene !== 'DAILY' }" role="button" :aria-label="companion.actionText" @click="bounceGuozai">
        <view class="home-hero__intro">
          <text class="home-hero__eyebrow">{{ dateTitle }}</text>
          <text class="home-hero__title">{{ companion.greeting }}</text>
          <text class="home-hero__copy">{{ companion.message }}</text>
          <view class="home-hero__memory">
            <view class="home-hero__memory-dot" />
            <view>
              <text class="home-hero__memory-title">{{ heroGuozai.name }}</text>
              <text class="home-hero__memory-copy">{{ companion.insight }}</text>
            </view>
          </view>
        </view>
        <image class="home-hero__img" :class="{ 'guozai-breathe': !isBouncing && !isHeroCycling, 'guozai-bounce': isBouncing, 'guozai-cycle': isHeroCycling }" :src="heroGuozai.img" :key="heroGuozai.img" mode="aspectFit" role="button" aria-label="点击切换锅仔形象" @click.stop="cycleHeroGuozai" />
        <view class="home-hero__profile" role="button" aria-label="打开我的页面" @click.stop="goto('profile')">
          <image :src="STATIC_BASE_URL + '/static/guozai/mood_01_happy.png'" mode="aspectFit" />
        </view>
        <view class="home-hero__bubble"><text>{{ companion.actionText }}</text><text class="home-hero__bubble-arrow">›</text></view>
        <view class="home-hero__spark home-hero__spark--one" /><view class="home-hero__spark home-hero__spark--two" />
      </view>

      <!-- 今日菜单（TodayBoard）：打开即有的零输入每日答案 -->
      <view v-if="todayBoard" class="home-board" role="button" :aria-label="`锅仔今天留了${todayBoard.recipe.name}，打开看做法`" @click="goCookToday">
        <view class="home-board__text">
          <text class="home-board__eyebrow">锅仔今天给你留了</text>
          <text class="home-board__name">{{ todayBoard.recipe.name }}</text>
          <text class="home-board__line">{{ todayBoard.guozaiLine }}</text>
          <view class="home-board__meta">
            <text v-if="todayBoard.recipe.cookingTime">{{ todayBoard.recipe.cookingTime }} 分钟</text>
            <text v-if="todayBoard.recipe.difficulty">· {{ todayBoard.recipe.difficulty }}</text>
          </view>
          <view class="home-board__ops">
            <view class="home-board__swap" role="button" aria-label="换一道" @click.stop="refreshBoard">
              <text>{{ boardRefreshing ? '锅仔再想想…' : '换一道' }}</text>
            </view>
            <view class="home-board__cook" role="button" aria-label="去做这道菜" @click.stop="goCookToday">
              <text>去做这道菜</text><text class="home-board__cook-arrow">›</text>
            </view>
          </view>
        </view>
        <image v-if="todayBoard.recipe.image" class="home-board__img" :src="todayBoard.recipe.image" mode="aspectFill" />
        <image v-else class="home-board__img home-board__img--fallback" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
      </view>

      <view v-if="fridgeSummary" class="home-fridge" role="button" aria-label="打开我的冰箱" @click="openFridge">
        <view class="home-fridge__copy"><text class="home-fridge__eyebrow">锅仔的新记忆</text><text class="home-fridge__title">冰箱里还有什么？</text><text class="home-fridge__sub">{{ fridgeSummary.soon ? `今天优先消耗 ${fridgeSummary.soon} 项食材` : `已记录 ${fridgeSummary.total} 项食材` }}</text></view>
        <view class="home-fridge__right"><text>{{ fridgeSummary.soon }}</text><text>临期</text><text class="home-fridge__arrow">›</text></view>
      </view>

      <view class="home-lucky" role="button" aria-label="让锅仔随机推荐一道菜" @click="gotoLucky">
        <view class="home-lucky__content">
          <image class="home-lucky__guozai" :src="STATIC_BASE_URL + '/static/guozai/action_10_thinking.png'" mode="aspectFit" />
          <view><text class="home-lucky__eyebrow">没想法的时候</text><text class="home-lucky__main">让锅仔替你决定</text><text class="home-lucky__sub">{{ recipeQuotaText }}</text></view>
        </view>
        <view class="home-lucky__arrow">›</view>
      </view>

      <view class="home-actions">
        <view class="home-actions__item home-actions__item--recipe" role="button" aria-label="查看今日三餐" @click="goto('daily-meal-plan')">
          <view class="home-actions__text"><text class="home-actions__label">一日三餐</text><text class="home-actions__name">今天怎么吃</text></view>
          <image class="home-actions__guozai" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
        </view>
        <view class="home-actions__item home-actions__item--recipe" role="button" aria-label="再推荐一道菜" @click="gotoLucky">
          <view class="home-actions__text"><text class="home-actions__label">今日菜单</text><text class="home-actions__name">再来一道</text></view>
          <image class="home-actions__guozai" :src="STATIC_BASE_URL + '/static/guozai/action_02_soup.png'" mode="aspectFit" />
        </view>
        <view class="home-actions__item home-actions__item--record" role="button" aria-label="记录一餐" @click="router.pushTab({ name: 'record' })">
          <view class="home-actions__text"><text class="home-actions__label">吃过什么</text><text class="home-actions__name">记录一餐</text></view>
          <image class="home-actions__guozai" :src="STATIC_BASE_URL + '/static/guozai/action_03_camera.png'" mode="aspectFit" />
        </view>
      </view>

      <view class="home-cal">
        <view class="home-cal__header">
          <view><text class="home-cal__kicker">{{ currentMonthLabel }}</text><text class="home-cal__date">留下了 {{ monthRecordMap.size }} 个食光坐标</text></view>
          <image class="home-cal__sticker" :src="STATIC_BASE_URL + '/static/guozai/action_08_peek.png'" mode="aspectFit" aria-label="探头的锅仔" />
        </view>
        <view class="home-cal__main"><view class="home-cal__main-grid">
          <text v-for="w in weekCN" :key="w" class="home-cal__main-w">{{ w }}</text>
          <block v-for="(c, i) in calCells" :key="i">
            <view
              v-if="c"
              class="home-cal__main-cell"
              :class="{ 'home-cal__main-cell--today': c.isToday, 'home-cal__main-cell--warm': c.records.length }"
              role="button"
              :aria-label="c.records.length ? `${c.d}日，${c.records[0].dishName}${c.records.length > 1 ? `等${c.records.length}餐` : ''}，打开记录` : `${c.d}日，记录一餐`"
              @click="openCalendarCell(c)"
            >
              <image v-if="c.records[0]?.imageUrl" class="home-cal__dish" :src="c.records[0].imageUrl" mode="aspectFill" />
              <text class="home-cal__day">{{ c.d }}</text>
              <view v-if="c.records.length && !c.records[0].imageUrl" class="home-cal__record-mark" />
              <text v-if="c.count > 1" class="home-cal__record-count">{{ c.count }}</text>
            </view>
            <view v-else class="home-cal__main-cell" />
          </block>
        </view></view>
        <view class="home-cal__memory">
          <image :src="STATIC_BASE_URL + '/static/guozai/action_12_heart.png'" mode="aspectFit" />
          <text>{{ calendarMemory }}</text>
        </view>
        <view class="home-cal__cta" role="button" aria-label="打开完整食光月历" @click="goto('calendar')">
          <text>打开食光月历</text><text aria-hidden="true">›</text>
        </view>
      </view>
      <view class="home-slogan"><view class="home-slogan__line" /><text class="home-slogan__text">好好吃饭，也好好生活</text><view class="home-slogan__line" /></view>
  </view>
</template>

<style lang="scss" scoped>
.home { min-height: 100vh; padding: 0 32rpx; padding-bottom: calc(120rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
/* 锅仔是首页主舞台、心情入口与角色化引导，而不是独立装饰图。 */
.home-hero { position: relative; min-height: 500rpx; margin: 8rpx -8rpx 28rpx; overflow: hidden; border: 2rpx solid var(--mrc-border); border-radius: 40rpx; background: radial-gradient(circle at 78% 18%, rgba(255, 197, 61, 0.22) 0, rgba(255, 197, 61, 0) 28%), linear-gradient(145deg, var(--mrc-surface) 0%, var(--mrc-surface-peach) 100%); box-shadow: var(--mrc-shadow-lift), var(--mrc-gloss); }
.home-hero--festival { background: radial-gradient(circle at 78% 18%, rgba(255, 197, 61, .34) 0, rgba(255, 197, 61, 0) 30%), linear-gradient(145deg, var(--mrc-surface-sun) 0%, var(--mrc-surface-peach) 100%); }
.home-hero__intro { position: relative; z-index: 2; display: flex; flex-direction: column; width: 72%; padding: 40rpx 0 0 36rpx; }
.home-hero__eyebrow, .home-cal__kicker { color: var(--mrc-accent); font-size: 22rpx; font-weight: 700; letter-spacing: 2rpx; }
.home-hero__title { margin-top: 12rpx; color: var(--mrc-text-strong); font-size: 40rpx; font-weight: 700; line-height: 1.3; }
.home-hero__copy { margin-top: 12rpx; color: var(--mrc-text-sub); font-size: 24rpx; }
.home-hero__memory { display: flex; align-items: center; align-self: flex-start; gap: 14rpx; max-width: 330rpx; margin-top: 22rpx; padding: 16rpx 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 22rpx; background: var(--mrc-surface-sun); box-shadow: var(--mrc-shadow-sm); }
.home-hero__memory-dot { width: 16rpx; height: 16rpx; flex-shrink: 0; border: 5rpx solid var(--mrc-accent-soft); border-radius: 50%; background: var(--mrc-accent); }
.home-hero__memory-title, .home-hero__memory-copy { display: block; }
.home-hero__memory-title { color: var(--mrc-text-deep); font-size: 23rpx; font-weight: 800; }
.home-hero__memory-copy { margin-top: 4rpx; color: var(--mrc-text-sub); font-size: 19rpx; line-height: 1.35; }
.home-hero__img { position: absolute; z-index: 1; right: -18rpx; bottom: -12rpx; width: 438rpx; height: 438rpx; animation: guozai-hero-in 0.45s ease; }
@keyframes guozai-hero-in { from { opacity: 0; transform: scale(0.88) translateY(12rpx); } to { opacity: 1; transform: scale(1) translateY(0); } }
.guozai-cycle { animation: guozai-cycle-pop 0.4s ease !important; }
@keyframes guozai-cycle-pop { 0% { transform: scale(1) rotate(0); } 40% { transform: scale(1.12) rotate(-6deg); } 70% { transform: scale(0.95) rotate(3deg); } 100% { transform: scale(1) rotate(0); } }
.home-hero__bubble { position: absolute; z-index: 3; left: 34rpx; bottom: 40rpx; display: flex; align-items: center; gap: 16rpx; max-width: 460rpx; min-height: 88rpx; box-sizing: border-box; padding: 16rpx 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 28rpx 28rpx 28rpx 8rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); color: var(--mrc-text-deep); font-size: 25rpx; font-weight: 700; }
.home-hero__bubble-arrow { color: var(--mrc-accent); font-size: 42rpx; line-height: 24rpx; }
.home-hero__spark { position: absolute; z-index: 0; width: 18rpx; height: 18rpx; border-radius: 50%; background: var(--mrc-pop); }
.home-hero__spark--one { top: 178rpx; right: 72rpx; }.home-hero__spark--two { right: 310rpx; bottom: 98rpx; width: 12rpx; height: 12rpx; background: var(--mrc-primary); }
/* 我的入口：hero 右上角圆形锅仔头像（小程序端 navbar 右侧被胶囊遮挡，故移入内容区） */
.home-hero__profile { position: absolute; z-index: 4; top: 24rpx; right: 24rpx; width: 88rpx; height: 88rpx; border: 2rpx solid var(--mrc-border); border-radius: 50%; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-sm); display: flex; align-items: center; justify-content: center; overflow: hidden; }
.home-hero__profile image { width: 58rpx; height: 58rpx; }

/* 今日菜单（TodayBoard）：白卡弱于 lucky 强 CTA，避免同屏抢眼；点击整卡进做法页。 */
.home-board { display: flex; align-items: stretch; gap: 20rpx; margin-bottom: 24rpx; padding: 26rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); box-sizing: border-box; }
.home-board__text { display: flex; flex: 1; min-width: 0; flex-direction: column; }
.home-board__eyebrow { color: var(--mrc-text-sub); font-size: 20rpx; }
.home-board__name { margin-top: 6rpx; color: var(--mrc-text-deep); font-size: 34rpx; font-weight: 800; }
.home-board__line { margin-top: 8rpx; color: var(--mrc-text); font-size: 22rpx; line-height: 1.4; }
.home-board__meta { display: flex; gap: 8rpx; margin-top: 8rpx; color: var(--mrc-text-sub); font-size: 20rpx; }
.home-board__ops { display: flex; align-items: center; gap: 20rpx; margin-top: auto; padding-top: 16rpx; }
.home-board__swap { padding: 10rpx 22rpx; border: 2rpx solid var(--mrc-border); border-radius: 999rpx; color: var(--mrc-text-deep); font-size: 22rpx; font-weight: 600; }
.home-board__swap:active { opacity: 0.7; }
.home-board__cook { display: flex; align-items: center; color: var(--mrc-primary); font-size: 22rpx; font-weight: 700; }
.home-board__cook-arrow { margin-left: 4rpx; font-size: 30rpx; line-height: 20rpx; }
.home-board__img { width: 176rpx; height: 176rpx; flex-shrink: 0; border-radius: 24rpx; background: var(--mrc-surface-2); }
.home-board__img--fallback { background: var(--mrc-surface-peach); }
.home-fridge { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin: 0 0 24rpx; padding: 22rpx 24rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; background: var(--mrc-surface-mint); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }.home-fridge__copy { display: flex; min-width: 0; flex-direction: column; gap: 6rpx; }.home-fridge__eyebrow { color: var(--mrc-mint); font-size: 19rpx; font-weight: 800; letter-spacing: 1px; }.home-fridge__title { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 800; }.home-fridge__sub { color: var(--mrc-text-sub); font-size: 20rpx; }.home-fridge__right { display: flex; align-items: center; gap: 4rpx; flex: 0 0 auto; color: var(--mrc-mint); }.home-fridge__right text:first-child { font-size: 34rpx; font-weight: 800; }.home-fridge__right text:nth-child(2) { align-self: flex-end; margin-bottom: 8rpx; font-size: 18rpx; }.home-fridge__arrow { margin-left: 8rpx; font-size: 40rpx; line-height: 1; }.home-fridge:active { opacity: .78; transform: scale(.985); }

/* 唯一强 CTA，减少一页内互相抢眼的高饱和元素。 */
.home-lucky { display: flex; align-items: center; justify-content: space-between; min-height: 156rpx; padding: 24rpx 32rpx; border-radius: 32rpx; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); box-sizing: border-box; }
.home-lucky__content { display: flex; align-items: center; gap: 20rpx; }.home-lucky__guozai { width: 88rpx; height: 88rpx; flex-shrink: 0; filter: drop-shadow(0 4rpx 8rpx rgba(0,0,0,0.12)); }
.home-lucky__eyebrow, .home-lucky__main, .home-lucky__sub { display: block; }.home-lucky__eyebrow { margin-bottom: 4rpx; color: rgba(255, 255, 255, 0.82); font-size: 20rpx; }.home-lucky__main { color: #fff; font-size: 32rpx; font-weight: 700; }.home-lucky__sub { margin-top: 4rpx; color: rgba(255, 255, 255, 0.9); font-size: 24rpx; }.home-lucky__arrow { margin-left: 12rpx; color: rgba(255, 255, 255, 0.95); font-size: 56rpx; font-weight: 300; }

.home-actions { display: flex; gap: 16rpx; margin: 24rpx 0 32rpx; }.home-actions__item { display: flex; flex: 1; align-items: center; justify-content: space-between; min-width: 0; min-height: 128rpx; padding: 0 20rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); box-sizing: border-box; }.home-actions__item--recipe { background: var(--mrc-surface-peach); }.home-actions__item--record { overflow: hidden; background: var(--mrc-surface-mint); }.home-actions__text { z-index: 1; display: flex; flex-direction: column; gap: 8rpx; }.home-actions__label { color: var(--mrc-text-sub); font-size: 21rpx; }.home-actions__name { color: var(--mrc-text-deep); font-size: 27rpx; font-weight: 700; white-space: nowrap; }.home-actions__guozai { width: 126rpx; height: 126rpx; margin-right: -16rpx; }

.home-cal { padding: 28rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 32rpx; background: var(--mrc-surface); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); }
.home-cal__header { display: flex; align-items: center; justify-content: space-between; min-height: 96rpx; margin-bottom: 20rpx; }
.home-cal__date { display: block; margin-top: 6rpx; color: var(--mrc-text-deep); font-size: 32rpx; font-weight: 700; }
.home-cal__sticker { width: 112rpx; height: 112rpx; margin: -16rpx 4rpx -12rpx 0; filter: drop-shadow(0 6rpx 10rpx rgba(96, 52, 28, 0.12)); }
.home-cal__main { border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; padding: 14rpx 12rpx 16rpx; background: linear-gradient(180deg, var(--mrc-surface-2), var(--mrc-surface-peach)); }
.home-cal__main-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6rpx; text-align: center; }
.home-cal__main-w { padding: 8rpx 0 10rpx; color: var(--mrc-text-sub); font-size: 20rpx; font-weight: 600; }
.home-cal__main-cell { position: relative; display: flex; align-items: center; justify-content: center; min-width: 0; min-height: 66rpx; overflow: hidden; border: 2rpx solid transparent; border-radius: 16rpx; color: var(--mrc-text); font-size: 22rpx; box-sizing: border-box; transition: transform 0.18s ease, opacity 0.18s ease; }
.home-cal__main-cell:active { opacity: 0.78; transform: scale(0.94); }
.home-cal__main-cell--today { border-color: var(--mrc-accent); background: var(--mrc-accent-soft); color: var(--mrc-accent); font-weight: 800; }
.home-cal__main-cell--warm { background: var(--mrc-surface-peach); color: var(--mrc-text-deep); font-weight: 700; }
.home-cal__dish { position: absolute; inset: 0; width: 100%; height: 100%; }
.home-cal__main-cell--warm .home-cal__day { position: absolute; right: 5rpx; bottom: 5rpx; z-index: 1; display: flex; align-items: center; justify-content: center; min-width: 30rpx; height: 30rpx; padding: 0 5rpx; border-radius: 15rpx; background: rgba(45, 24, 14, 0.72); color: #fff; font-size: 18rpx; box-sizing: border-box; }
.home-cal__record-mark { width: 12rpx; height: 12rpx; margin-left: 6rpx; border-radius: 50%; background: var(--mrc-accent); }
.home-cal__record-count { position: absolute; top: 4rpx; right: 5rpx; z-index: 2; min-width: 26rpx; height: 26rpx; padding: 0 5rpx; border-radius: 9rpx; background: var(--mrc-accent); color: #fff; font-size: 15rpx; font-weight: 800; text-align: center; line-height: 26rpx; box-sizing: border-box; }
.home-cal__memory { display: flex; align-items: center; gap: 14rpx; min-height: 82rpx; margin-top: 18rpx; padding: 10rpx 18rpx; border-radius: 20rpx; background: var(--mrc-surface-sun); color: var(--mrc-text-deep); font-size: 23rpx; line-height: 1.45; box-sizing: border-box; }
.home-cal__memory image { width: 64rpx; height: 64rpx; flex: 0 0 auto; }
.home-cal__cta { display: flex; align-items: center; justify-content: space-between; min-height: 88rpx; margin-top: 8rpx; padding: 0 8rpx 0 12rpx; color: var(--mrc-accent); font-size: 24rpx; font-weight: 700; }
.home-cal__cta text:last-child { font-size: 40rpx; font-weight: 400; }
.home-cal__cta:active { opacity: 0.68; }
.home-slogan { display: flex; align-items: center; gap: 16rpx; padding: 36rpx 12rpx 44rpx; }.home-slogan__text { flex: 0 0 auto; color: var(--mrc-text-light); font-size: 24rpx; letter-spacing: 2rpx; }.home-slogan__line { flex: 1; height: 2rpx; background: var(--mrc-border); }.home-lucky:active, .home-actions__item:active, .home-hero:active { transform: scale(0.985); }
</style>
