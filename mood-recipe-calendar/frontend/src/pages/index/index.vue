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
import { computed, nextTick, ref, watch } from 'vue'
import Icon from '../../components/common/Icon.vue'
import { ensureLogin } from '../../utils/login'
import { toast, toastError } from '../../utils/toast'
import { haptic } from '../../utils/haptic'
import { acceptShareInvite } from '../../api/share'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchCompanionMessage, fetchRecordsByMonth, type CompanionMessage, type RecordItem } from '../../api/records'
import { fetchRecipePool, fetchRecipeQuota, fetchRecipeDetail, type RecipeItem, type UsageQuotaView } from '../../api/recipes'
import { fetchDailyBoard, refreshDailyBoard, generateDailyMenuCover, splitComboDishName, type DailyBoard } from '../../api/dailyMenu'
import { fetchWeeklyTheme, type WeeklyTheme } from '../../api/weeklyTheme'
import { saveCookingDraft } from '../../utils/cookingDraft'
import { navLocked } from '../../utils/nav'
import { imgThumb, imgList, imgHero } from '../../utils/imageUrl'
import GuozaiFlip from '../../components/guozai/GuozaiFlip.vue'
import MealSheet from '../../components/guozai/MealSheet.vue'
import GuozaiOnboardingSheet from '../../components/guozai/GuozaiOnboardingSheet.vue'
import { fetchFoodPreference } from '../../api/preferences'
import PlanModeSheet from '../../components/guozai/PlanModeSheet.vue'
import { useUserStore } from '../../stores/user'
import { funnel } from '../../utils/funnel'

/** 老黄历钩子卡文案：当日「宜」第一条（按日期种子轮换，当天恒定） */

definePage({ name: 'home', layout: 'tabbar', style: { navigationStyle: 'custom', navigationBarTitleText: '首页', enablePullDownRefresh: true } })
const userStore = useUserStore()
const router = useRouter()
const isBouncing = ref(false)
function bounceGuozai() {
  haptic('light')
  isBouncing.value = true
  setTimeout(() => {
    isBouncing.value = false
    if (companion.value.actionTarget === 'meal-agent') {
      navLocked(() => router.push({ name: 'meal-agent', params: { prompt: companion.value.actionPrompt || companion.value.actionText } }))
      return
    }
    navLocked(() => router.push({ name: 'mood' }))
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
  haptic('light')
  isHeroCycling.value = true
  heroGuozaiIndex.value = (heroGuozaiIndex.value + 1) % ALL_HERO_GUOZAI.length
  setTimeout(() => { isHeroCycling.value = false }, 400)
}
const monthRecords = ref<RecordItem[]>([])
const companion = ref<CompanionMessage>(localCompanion(now.value.getHours()))
const recipeQuota = ref<UsageQuotaView>()
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

/* ---- 每周主题运营位：按周轮换的运营钩子，点一下带预设去周计划 ---- */
const weeklyTheme = ref<WeeklyTheme | null>(null)
const THEME_BANNER_KEY = 'mrc_theme_banner_week'
function weekIndexOf(): number {
  const d = new Date()
  return d.getFullYear() * 100 + Math.ceil(((+d - +new Date(d.getFullYear(), 0, 1)) / 86400000 + 1) / 7)
}
async function loadWeeklyTheme() {
  const theme = await fetchWeeklyTheme()
  weeklyTheme.value = theme
}
function dismissThemeBanner() {
  try { uni.setStorageSync(THEME_BANNER_KEY, String(weekIndexOf())) } catch {}
  weeklyTheme.value = null
}
const showThemeBanner = computed(() => {
  if (!weeklyTheme.value) return false
  try { return Number(uni.getStorageSync(THEME_BANNER_KEY) || 0) < weekIndexOf() } catch { return true }
})
function goWithTheme(theme: WeeklyTheme) {
  try {
    uni.setStorageSync('mrc_theme_preset', JSON.stringify({
      code: theme.code, title: theme.title, emoji: theme.emoji,
      healthGoal: theme.healthGoal, budget: theme.budget, notes: theme.notes,
    }))
  } catch {}
  navLocked(() => router.push({ name: 'weekly-plan' }))
}

/** 本月记录本地缓存：冷启动首屏先渲染缓存再等网络，避免「食光坐标」白等一轮请求。
 *  与 loadCompanion 同款模式；写入仅在请求成功后，读取按月份校验，跨月自动失效。 */
const RECORDS_CACHE_KEY = 'mrc_records_month'
function readRecordsCache(month: string): RecordItem[] | null {
  try {
    const cached = uni.getStorageSync(RECORDS_CACHE_KEY)
    if (cached?.month === month && Array.isArray(cached.items))
      return cached.items
  }
  catch {}
  return null
}
function writeRecordsCache(month: string, items: RecordItem[]) {
  try {
    uni.setStorageSync(RECORDS_CACHE_KEY, { month, items })
  }
  catch {}
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    await ensureLogin()
    // 关键数据（决定「食光坐标」数量）先独立加载；quota/同伴语/今日餐板的失败必须隔离，
    // 不能因为任何一项 400 就把已加载的本月记录清空（曾经因 /recipes/quota 400 导致首页显示 0 个坐标）
    // 先用上月缓存即时渲染首屏，网络回来后静默覆盖（冷启动不等 /records/month）
    const cachedRecords = readRecordsCache(currentMonth.value)
    if (cachedRecords)
      monthRecords.value = cachedRecords
    monthRecords.value = await fetchRecordsByMonth(currentMonth.value)
    writeRecordsCache(currentMonth.value, monthRecords.value)
    fetchRecipeQuota().then((q) => { recipeQuota.value = q }).catch(() => {})
    loadCompanion().catch(() => {})
    loadTodayBoard().catch(() => {})
    loadWeeklyTheme().catch(() => {})
    // 菜池提前加载：转盘/九宫格/翻牌点开即用，不等点击后再拉。
    // 推迟到首屏渲染后执行：预热大图的解码避开冷启动内存峰值（低端机 Wasm OOM 的缓解手段）；
    // 用户手更快时 recipePool() 仍有按需拉取兜底，不影响开抽。
    nextTick(() => { setTimeout(() => { void preloadRecipePool() }, 0) })
  } catch (e: any) {
    if (e?.message !== 'NOT_LOGGED_IN')
      toastError(e, '加载失败，请稍后重试')
  } finally {
    loading.value = false
    lastHomeLoadAt.value = Date.now()
  }
}

function localCompanion(hour: number): CompanionMessage {
  if (hour >= 5 && hour < 11) return { greeting: '早上好，先把自己照顾好', message: '早饭不用复杂，热乎顺口就很好。', insight: '锅仔会慢慢记住你的口味', actionText: '告诉我现在的心情' }
  if (hour < 15) return { greeting: '到饭点了，别让肚子等太久', message: '忙归忙，午饭还是要认真吃。', insight: '每次选择，都让我更懂你', actionText: '告诉我现在的心情' }
  if (hour < 18) return { greeting: '下午好，想想今晚吃什么', message: '提前选好，饿的时候就不用匆忙决定。', insight: '锅仔正在学习你的饭点', actionText: '让锅仔替你挑一道' }
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
/** 今日菜单（TodayBoard）：打开即有的零输入答案；加载失败静默，不影响首屏其余内容。
 *  [2026-10-23] 打磨项⑤：按天缓存——冷启动先用当天缓存即时渲染整块三餐卡，
 *  网络回来后静默覆盖；有缓存兜底时请求失败不再显示失败态（画面上已有内容）。
 *  recipe_delivered 埋点只在「本次拿到的菜与缓存里的不同」时上报，避免冷启动双报。 */
const TODAY_BOARD_CACHE_KEY = 'mrc_today_board'
function todayKey() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
async function loadTodayBoard() {
  let cachedBoard: DailyBoard | undefined
  try {
    const cached = uni.getStorageSync(TODAY_BOARD_CACHE_KEY)
    if (cached?.key === todayKey() && cached.board)
      cachedBoard = cached.board as DailyBoard
  }
  catch { /* 缓存坏了当没有 */ }
  const hasCache = Boolean(cachedBoard)
  if (hasCache)
    todayBoard.value = cachedBoard
  boardLoading.value = !hasCache
  boardLoadFailed.value = false
  try {
    todayBoard.value = await withTimeout(fetchDailyBoard(), BOARD_LOAD_TIMEOUT, '今日菜单加载超时')
    if (todayBoard.value?.recipe && (!hasCache || todayBoard.value.recipe.name !== cachedBoard?.recipe?.name))
      funnel('recipe_delivered', { name: todayBoard.value.recipe.name })
    backfillBoardCover()
    if (todayBoard.value) {
      try { uni.setStorageSync(TODAY_BOARD_CACHE_KEY, { key: todayKey(), board: todayBoard.value }) }
      catch { /* 存储满等异常忽略，缓存只是加速手段 */ }
    }
  }
  catch {
    if (!hasCache) {
      todayBoard.value = undefined
      boardLoadFailed.value = true
    }
  }
  finally {
    boardLoading.value = false
  }
}

/** 失败卡点击重试 */
function retryBoard() {
  loadTodayBoard().catch(() => {})
}

/** 今日菜懒补专属封面：没有真实图（锅仔插画兜底）时后台补拍，成功后就地替换；失败保持兜底。 */
async function backfillBoardCover() {
  if (!todayBoard.value?.recipe || todayBoard.value.recipe.image) return
  try {
    const board = await generateDailyMenuCover()
    if (board?.recipe?.image) todayBoard.value = board
  }
  catch { /* 静默：保留锅仔插画兜底 */ }
}

/** 早餐组合菜「主食 · 配饮品」拆开展示：标题显示主食，饮品作为小标签。 */
const boardNameParts = computed(() => splitComboDishName(todayBoard.value?.recipe?.name || ''))

/** 今日菜牌上下滑换菜（微创新）：跟手阻尼位移 + 松手过阈值按方向切换。
 *  上滑换新菜（与转盘同源接口，本地压栈记历史）；下滑弹栈回看上一道；
 *  入场/离场方向跟随手势（上滑内容从下浮入，下滑从上浮入）；动画期间防抖。 */
/** 今日餐板数据：打开即有的零输入答案；加载失败静默，不影响首屏其余内容 */
const todayBoard = ref<DailyBoard>()
const boardSwitching = ref(false)
const boardLeaving = ref(false)
const boardDragging = ref(false)
const boardDragY = ref(0)
/** 换菜方向：1=上滑换新，-1=下滑回看，决定入场/离场动画方向 */
const boardDir = ref<1 | -1>(1)
/** 回看历史栈：栈顶是上一道；上限 10 道防内存膨胀 */
const boardHistory = ref<DailyBoard[]>([])
/** 骨架屏：首载与上滑换菜数据未到时占位，杜绝「空白卡」 */
const boardLoading = ref(false)
const boardSkeleton = ref(false)
/** 首载失败（含超时）：显示「点我重试」卡，骨架屏绝不无限转圈 */
const boardLoadFailed = ref(false)
let boardSkeletonTimer: ReturnType<typeof setTimeout> | null = null
const BOARD_LOAD_TIMEOUT = 10_000
/** 换菜走后端菜谱池缓存路径（毫秒级）；12s 只为弱网留余量，超时即报错并给重试卡 */
const BOARD_SWITCH_TIMEOUT = 12_000

/** 看门狗：请求层无超时配置，连接挂起时 Promise 永不落定会把骨架屏卡死——这里兜底强制失败 */
function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(label)), ms)
    promise.then(
      (value) => { clearTimeout(timer); resolve(value) },
      (error) => { clearTimeout(timer); reject(error) },
    )
  })
}
let boardTouchStartY: number | null = null
let boardRawDy = 0
const BOARD_SWITCH_THRESHOLD = 36

function onBoardTouchStart(e: { touches?: Array<{ clientY?: number }> }) {
  if (boardSwitching.value) return
  boardDragging.value = true
  boardTouchStartY = e.touches?.[0]?.clientY ?? null
  boardRawDy = 0
}

function onBoardTouchMove(e: { touches?: Array<{ clientY?: number }> }) {
  if (boardTouchStartY == null || boardSwitching.value) return
  boardRawDy = (e.touches?.[0]?.clientY ?? boardTouchStartY) - boardTouchStartY
  // 上下都跟手，1/3 阻尼且封顶 24px——顶牌只微微抬起，不做大幅位移；松手由 CSS transition 回弹
  boardDragY.value = Math.max(-24, Math.min(24, boardRawDy / 3))
}

async function onBoardTouchEnd() {
  const start = boardTouchStartY
  boardDragging.value = false
  boardTouchStartY = null
  const dy = boardRawDy
  boardRawDy = 0
  boardDragY.value = 0
  if (start == null || boardSwitching.value) return
  if (dy < -BOARD_SWITCH_THRESHOLD) await switchBoardDish(1)
  else if (dy > BOARD_SWITCH_THRESHOLD) await switchBoardDish(-1)
}

async function switchBoardDish(direction: 1 | -1) {
  if (boardSwitching.value) return
  haptic('light')
  const current = todayBoard.value
  if (!current) return
  // 下滑回看：弹栈恢复上一道（本地数据即时到位，直接切入场动画）；无历史则轻提示后原样回弹
  if (direction < 0) {
    const prev = boardHistory.value.pop()
    if (!prev) {
      toast('已经是最早那道啦')
      return
    }
    boardDir.value = -1
    boardSwitching.value = true
    todayBoard.value = prev
    setTimeout(() => { boardSwitching.value = false }, 420)
    return
  }
  // 上滑换新：换成功的当前菜压栈，供下滑回看
  boardSwitching.value = true
  boardLeaving.value = true
  boardDir.value = 1
  // 离场淡出（.26s）结束后仍无数据才上骨架屏，避免快速返回时闪烁
  if (boardSkeletonTimer) clearTimeout(boardSkeletonTimer)
  boardSkeleton.value = false
  boardSkeletonTimer = setTimeout(() => { boardSkeleton.value = true }, 300)
  try {
    const board = await withTimeout(refreshDailyBoard(), BOARD_SWITCH_TIMEOUT, '换菜超时')
    boardHistory.value.push(current)
    if (boardHistory.value.length > 10) boardHistory.value.shift()
    todayBoard.value = board
    if (boardSkeletonTimer) clearTimeout(boardSkeletonTimer)
    boardSkeleton.value = false
    boardLeaving.value = false
    void backfillBoardCover()
  }
  catch (e: any) {
    boardSkeleton.value = false
    boardLeaving.value = false
    toastError(e, '今天没有别的菜可换啦')
  }
  finally {
    setTimeout(() => { boardSwitching.value = false }, 420)
  }
}

/** 菜池预加载：转盘/九宫格/翻牌共用。onShow 即拉取并预热图片，打开弹层时秒开不白屏。 */
const recipePoolCache = ref<RecipeItem[]>([])
const recipePoolAt = ref(0)
const RECIPE_POOL_TTL = 5 * 60 * 1000
/** 进行中的预拉取 Promise：并发调用复用同一请求，避免「先开的拿到空池、后开的才填好」的竞态
 *  （旧实现用 poolPreloading 布尔早退，并发时第二个调用会直接 resolve 成 []，导致九宫格/翻牌偶发全彩蛋词或死局）。 */
let poolPromise: Promise<void> | null = null
function preloadRecipePool(force = false): Promise<void> {
  const fresh = recipePoolCache.value.length && Date.now() - recipePoolAt.value < RECIPE_POOL_TTL
  if (poolPromise)
    return poolPromise
  if (!force && fresh)
    return Promise.resolve()
  poolPromise = (async () => {
    try {
      const pool = await fetchRecipePool()
      recipePoolCache.value = pool
      recipePoolAt.value = Date.now()
      // 预热前 8 张封面（与翻牌卡面同一 imgList 变体——同 URL 才能命中同一份缓存）：
      // 抽样大多落在其中，开抽时图片已在本地缓存，不再「转完了图才出来」。
      // 控制在 8 张：800px WebP 单张约 60KB（原 1.2MB 全尺寸 PNG 的 1/20），
      // 既省 CDN 流量也避开冷启动期解码大图的内存峰值（低端机 OOM 的历史压力源）。
      pool.slice(0, 8).forEach((item) => {
        if (!item.image)
          return
        uni.getImageInfo({ src: imgList(item.image), fail: () => {} })
      })
    }
    catch { /* 预加载失败静默：打开弹层时还有兜底拉取 */ }
    finally {
      poolPromise = null
    }
  })()
  return poolPromise
}
/** 取菜池：复用同一进行中的请求，保证并发调用都拿到已填好的池子。 */
function recipePool(): Promise<RecipeItem[]> {
  return preloadRecipePool().then(() => recipePoolCache.value)
}

/** 从菜池抽样：按菜名去重 + 同前缀（前 2 字）最多 2 道，避免「番茄炒蛋×3」这类刷屏；可排除某道菜（如中奖菜）。 */
function sampleDiverseDishes(pool: RecipeItem[], count: number, excludeName = ''): RecipeItem[] {
  const seen = new Set<string>()
  const unique: RecipeItem[] = []
  for (const item of pool) {
    if (!item?.name || !item.image || item.name === excludeName || seen.has(item.name))
      continue
    seen.add(item.name)
    unique.push(item)
  }
  const shuffled = [...unique].sort(() => Math.random() - 0.5)
  const picked: RecipeItem[] = []
  const prefixCount = new Map<string, number>()
  for (const item of shuffled) {
    const key = item.name.slice(0, 2)
    const used = prefixCount.get(key) || 0
    if (used >= 2)
      continue
    prefixCount.set(key, used + 1)
    picked.push(item)
    if (picked.length >= count)
      break
  }
  // 多样性过滤后不够数（菜池太小时），用剩余去重菜补齐
  for (const item of shuffled) {
    if (picked.length >= count)
      break
    if (!picked.includes(item))
      picked.push(item)
  }
  return picked
}

/** 锅仔翻牌（squarenine 复刻）：洗牌后任选一张，翻开的即真实抽中的那道菜。 */
const showFlip = ref(false)
const flipItems = ref<{ name: string, image?: string }[]>([])
const flipPool = ref<RecipeItem[]>([])
const flipPick = ref<RecipeItem>()
/** 翻牌的 8 张牌对应的完整 RecipeItem（与 flipItems 同序），用户翻第几张就取第几道真实菜 */
const flipCards = ref<RecipeItem[]>([])
const flipRef = ref<InstanceType<typeof GuozaiFlip>>()
async function openFlip() {
  haptic('light')
  showFlip.value = true
  flipPick.value = undefined
  try {
    const pool = await recipePool()
    flipPool.value = pool
    flipCards.value = buildFlipCards(pool)
    flipItems.value = flipCards.value.map(item => ({ name: item.name, image: item.image }))
  }
  catch {
    flipPool.value = []
    flipCards.value = []
    flipItems.value = []
  }
}
/** 抽 8 道真实菜作为翻牌的 8 张牌（去重 + 同前缀限流；不足 8 道用余量补齐，仍不足再退化为彩蛋词）。 */
function buildFlipCards(pool: RecipeItem[]): RecipeItem[] {
  const cards = sampleDiverseDishes(pool, 8)
  for (const item of pool) {
    if (cards.length >= 8)
      break
    if (item?.name && !cards.some(c => c.name === item.name))
      cards.push(item)
  }
  return cards
}
async function onFlipStart() {
  // 菜池未就绪（预加载失败/被清）时先补拉：不能让卡片永远停在「洗牌中」
  if (!flipPool.value.length) {
    try {
      flipPool.value = await recipePool()
    }
    catch {
      flipPool.value = []
    }
    if (!flipPool.value.length) {
      toastError(new Error('菜谱还没备好，稍后再来试试'), '翻一翻')
      flipRef.value?.fail()
      return
    }
  }
  // 真选择：8 张牌各自背一道真实菜，用户翻哪张就吃哪张（不再预设中奖菜）
  flipCards.value = buildFlipCards(flipPool.value)
  flipItems.value = flipCards.value.map(item => ({ name: item.name, image: item.image }))
  flipRef.value?.deal()
}
/** 用户翻开真实菜牌：序号即真实菜在 flipCards 中的位置，喂给「就做这道」做菜链路 */
function onFlipPick(index: number) {
  const r = flipCards.value[index]
  if (r && r.name)
    flipPick.value = r
}
/** 翻到彩蛋装饰格：轻提示 + 组件原地重洗，不再弹错误 toast */
function onFlipDecoy() {
  toast('翻到锅仔的彩蛋啦，重新洗一局~')
  flipRef.value?.reshuffle?.()
}
function onFlipCook() {
  const recipe = flipPick.value
  if (!recipe)
    return
  showFlip.value = false
  saveCookingDraft(recipe, '满足')
  navLocked(() => router.push({ name: 'cooking' }))
}
function goCookToday() {
  const board = todayBoard.value
  if (!board?.recipe?.id)
    return
  haptic('light')
  // 今日菜单已经是一道确定的菜，直接把它交给做菜智能体，不能再次进入推荐页。
  saveCookingDraft(board.recipe, '满足')
  navLocked(() => router.push({ name: 'cooking' }))
}

/** 分享回流承接：好友通过分享卡打开 → 双方各 +1 次对话额度；带上好友分享的那道菜名和封面图做欢迎 */
const friendDish = ref('')
const friendDishImage = ref('')
const friendDishImgFailed = ref(false)
const inviteBonus = ref(false)
const isShowShareCurtain = ref(false)
/** 分享链接带的菜谱 id：幕帘「看做法」深链到这道菜（recipe 页已支持 ?recipeId= 直达） */
const sharedRecipeId = ref(0)
/** 幕帘翻转卡：正面好友的菜（大图），背面食材清单；轻点翻面，整体居中构图 */
const curtainFlipped = ref(false)
const curtainIngredients = ref<string[]>([])
const curtainChips = ref<{ time?: number, difficulty?: string }>({})
function toggleCurtainFlip() {
  curtainFlipped.value = !curtainFlipped.value
  haptic('light')
}
/** 食材串兼容三种形态：JSON 数组 / 顿号分隔 / 逗号分隔（历史数据与模型输出都有） */
function parseIngredientList(value?: string): string[] {
  const raw = (value || '').trim()
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed))
      return parsed.map(String).map(item => item.trim()).filter(Boolean)
  }
  catch {}
  return raw.split(/[、,，;；]/).map(item => item.trim()).filter(Boolean)
}
/** 幕帘食材拉的代数令牌：迟到的响应不得写回已关闭/已重开的幕帘 */
let curtainFetchToken = 0
watch(isShowShareCurtain, (open) => {
  const token = ++curtainFetchToken
  if (!open) {
    curtainFlipped.value = false
    return
  }
  // 每次打开都回到正面，并重拉食材（分享的可能是一周前的那道菜）
  curtainFlipped.value = false
  curtainIngredients.value = []
  curtainChips.value = {}
  const id = sharedRecipeId.value
  if (!id) return
  // 首页 loadData 已 ensureLogin；未登录时静默失败，背面走兜底文案，不打断欢迎。
  // 关键：幕帘常比网络先关（点「看做法」即关即跳），root-portal 的节点随关闭被摘除，
  // 迟到的结果若写 ref，Vue 会向不存在的原生父节点插子节点——
  // 微信报 insertTextView/insertImageView:fail parent not found 级联错。故令牌+开态双保险丢弃。
  fetchRecipeDetail(id)
    .then((recipe) => {
      if (token !== curtainFetchToken || !isShowShareCurtain.value) return
      curtainIngredients.value = parseIngredientList(recipe?.ingredients)
      curtainChips.value = { time: recipe?.cookingTime, difficulty: recipe?.difficulty }
    })
    .catch(() => {})
})
function openSharedDish() {
  if (!sharedRecipeId.value) return
  // 点了主 CTA 视为已欢迎：先关幕帘（触发 @close 打频控标记），再跳菜谱，
  // 避免从菜谱返回首页时幕帘还挂在屏幕上
  isShowShareCurtain.value = false
  // 坑位：@wot-ui/router 的 name 导航只认 params（resolve 里 finalQuery = to.params，query 字段被丢弃），
  // 传 query 会静默丢参数导致菜谱页收不到 recipeId。传 params，stringifyQuery 会拼进 URL，onLoad 正常收到。
  navLocked(() => router.push({ name: 'recipe', params: { recipeId: String(sharedRecipeId.value) } }))
}
/** 幕帘「我也要安排」：给分享来的新用户第一条主动路径（选心情→推荐） */
function goMoodFromShare() {
  isShowShareCurtain.value = false
  navLocked(() => router.push({ name: 'mood' }))
}

/**
 * 分享幕帘频控：同一好友的同一道菜只欢迎一次（key = sharer:recipeId/dish）。
 * 不按"每天一次"设计——好友这周分享三道不同的菜，每道都值得一次欢迎；
 * 重复进入同一道菜（连点分享卡片、杀进程重开）才安静落地。
 * 关闭时才写标记：用户主动关掉 = 确实看过了；直接杀小程序不标记，下次进仍欢迎。
 */
const CURTAIN_SEEN_KEY = 'mrc_share_curtain_seen'
const shareCurtainKey = ref('')
function curtainSeen(key: string): boolean {
  try {
    const seen: string[] = uni.getStorageSync(CURTAIN_SEEN_KEY) || []
    return seen.includes(key)
  }
  catch {
    return false
  }
}
function markCurtainSeen(key: string) {
  if (!key) return
  try {
    const seen: string[] = uni.getStorageSync(CURTAIN_SEEN_KEY) || []
    if (!seen.includes(key))
      uni.setStorageSync(CURTAIN_SEEN_KEY, [...seen, key].slice(-20)) // 只留最近 20 条，防无限增长
  }
  catch {}
}
function onCurtainClosed() {
  markCurtainSeen(shareCurtainKey.value)
}
/**
 * 小程序转发链路会对 path query 再做一次整体编码：我们 encodeURIComponent 过的中文
 * 到 onLoad 时变成了 %25E8…（% 被二次编码），表现为「%25E8%2599…」乱码。
 * 这里循环解码到没有 %XX 转义为止（最多 3 层，防止异常输入死循环）。
 */
function decodeQueryParam(value: string) {
  let result = value
  for (let i = 0; i < 3 && /%[0-9A-Fa-f]{2}/.test(result); i += 1) {
    try {
      const decoded = decodeURIComponent(result)
      if (decoded === result)
        break
      result = decoded
    }
    catch {
      break
    }
  }
  return result
}
onLoad((query) => {
  // 小程序码扫码归因：画册/报告/食谱分享物料被扫后，scene 标记来源（album/report/recipe/daily_menu/record）
  const scanScene = decodeQueryParam(String(query?.scene || ''))
  if (scanScene)
    funnel('qrcode_scan', { from: scanScene })
  const sharer = decodeQueryParam(String(query?.sharer || ''))
  const dish = decodeQueryParam(String(query?.dish || ''))
  const img = decodeQueryParam(String(query?.img || ''))
  sharedRecipeId.value = Number(query?.recipeId) || 0
  // 本次分享的频控 key：同一好友同一道菜，欢迎过一次就不再打扰
  shareCurtainKey.value = `${sharer || 'anon'}:${sharedRecipeId.value || dish || 'invite'}`
  if (dish && !curtainSeen(shareCurtainKey.value)) {
    friendDish.value = dish
    isShowShareCurtain.value = true
  }
  // 只接受 http(s) 全链或站内绝对路径，防注入；非法值忽略，弹窗回落锅仔插画
  if (/^(https?:\/\/|\/)/.test(img)) {
    friendDishImage.value = img
    friendDishImgFailed.value = false
  }
  if (!sharer || sharer === userStore.openid)
    return
  acceptShareInvite(sharer)
    .then((res) => {
      if (res?.grantedBonus) {
        inviteBonus.value = true
        // 同样过频控：本 key 欢迎过（或正在展示中）就只发奖励 toast，不再弹幕帘
        if (!curtainSeen(shareCurtainKey.value) && !isShowShareCurtain.value)
          isShowShareCurtain.value = true
      }
    })
    .catch(() => {})
})

// P1：引导加"我的小程序"。前 3 次进首页显示一次，用户点"知道了"永久关闭。
const FAV_TIP_KEY = 'mrc_fav_tip_dismissed'
const FAV_TIP_COUNT_KEY = 'mrc_fav_tip_shown_count'
const showFavTip = ref(false)
function maybeShowFavTip() {
  if (uni.getStorageSync(FAV_TIP_KEY)) return
  const n = Number(uni.getStorageSync(FAV_TIP_COUNT_KEY) || 0)
  if (n >= 3) return
  uni.setStorageSync(FAV_TIP_COUNT_KEY, n + 1)
  showFavTip.value = true
}
function dismissFavTip() {
  showFavTip.value = false
  uni.setStorageSync(FAV_TIP_KEY, '1')
}

/** 首页数据新鲜度：30s 内且未跨天视为新鲜，切后台/分享返回/扫码回来不再全量重拉，
 *  只刷新时间。下拉刷新（onPullDownRefresh）与 RECORDS_CHANGED 事件仍强制刷新。 */
const HOME_STALE_MS = 30 * 1000
const lastHomeLoadAt = ref(0)
function homeDataStale(): boolean {
  if (!lastHomeLoadAt.value)
    return true
  const last = new Date(lastHomeLoadAt.value)
  const now = new Date()
  if (last.toDateString() !== now.toDateString())
    return true
  return Date.now() - lastHomeLoadAt.value > HOME_STALE_MS
}

onShow(() => {
  now.value = new Date()
  if (homeDataStale())
    loadData()
  maybeShowFavTip()
  funnel('home_view')
})

/** 新记录落库（记录页/做菜页打卡）后即使没超时也强制刷新，避免日历计数短时陈旧 */
function onRecordsChanged() {
  lastHomeLoadAt.value = 0
  if (!loading.value)
    loadData()
}
bus.on(MRC_EVENTS.RECORDS_CHANGED, onRecordsChanged)
onUnmounted(() => bus.off(MRC_EVENTS.RECORDS_CHANGED, onRecordsChanged))

/** 下拉刷新：重新拉取今日菜、本月食光坐标等，结束后收起原生下拉圈 */
onPullDownRefresh(() => {
  loadData().finally(() => uni.stopPullDownRefresh())
})

/** 转发给好友/群：必须定义 onShareAppMessage 胶囊「···」里的转发才可用 */
onShareAppMessage(() => {
  funnel('share_click', { from: 'home' })
  const sharer = userStore.openid
  // 分享卡带菜带图：好友点开直接看到这道菜和它的封面，回流后双方各得 1 次对话额度
  const dish = todayBoard.value?.recipe?.name || ''
  const dishImage = todayBoard.value?.recipe?.image || ''
  const params = [
    sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
    dish ? `dish=${encodeURIComponent(dish)}` : '',
    dishImage ? `img=${encodeURIComponent(dishImage)}` : '',
    todayBoard.value?.recipe?.id ? `recipeId=${todayBoard.value.recipe.id}` : '',
  ].filter(Boolean).join('&')
  const path = `/pages/index/index${params ? `?${params}` : ''}`
  const title = dish ? `我用锅仔安排了「${dish}」，也替你留了一份` : '锅仔 · 按心情帮你决定今天吃什么'
  // 记录分享：发放「分享家」徽章 + 非会员当日 +1 次对话激励（失败静默，不影响转发）
  void recordShare('home').catch(() => {})
  // 分享卡封面用菜图（无图时省略，微信默认截屏首页）
  return dishImage ? { title, path, imageUrl: dishImage } : { title, path }
})
/** 分享到朋友圈：必须定义 onShareTimeline 才可用 */
onShareTimeline(() => {
  const sharer = userStore.openid
  const dishImage = todayBoard.value?.recipe?.image || ''
  const query = [
    sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
    dishImage ? `img=${encodeURIComponent(dishImage)}` : '',
    todayBoard.value?.recipe?.id ? `recipeId=${todayBoard.value.recipe.id}` : '',
  ].filter(Boolean).join('&')
  // 朋友圈卡封面用菜图（无图时省略，微信默认用小程序截图）
  const payload: { title: string, query: string, imageUrl?: string }
    = { title: '锅仔 · 按心情帮你决定今天吃什么', query }
  if (dishImage)
    payload.imageUrl = dishImage
  return payload
})
const MOODS = ['开心', '平静', '疲惫', '焦虑', '难过', '嘴馋', '低落', '想家', '期待', '满足', '得意', '害羞']
/** 「让锅仔替你决定」点击展开餐别选择器：选一顿后带上餐别去推荐页，让推荐有《膳食指南》可依 */
const showMealSheet = ref(false)
/** 冷启动画像问卷：与 mood 页共享同一份 asked 标记，两个入口合计只弹一次（展示时落标记） */
const showOnboarding = ref(false)
async function openMealSheet() {
  // [问卷契约] 弹出条件 = 新用户且没填写过。本地标记缺失时先静默向服务端核实
  // onboardingCompleted（换设备/清缓存/调试提交过的场景），填写过就同步标记、绝不弹问卷。
  // 与 mood 页 onConfirm 同一套契约；非阻塞，查询失败按未填写走原判定。
  if (!uni.getStorageSync('mrc_preference_onboarded') && userStore.isLoggedIn) {
    try {
      const preference = await fetchFoodPreference()
      if (preference?.onboardingCompleted) uni.setStorageSync('mrc_preference_onboarded', '1')
    }
    catch {
      // 核实失败不拦截：按未填写继续走本地标记判定
    }
  }
  if (!uni.getStorageSync('mrc_preference_onboarded') && !uni.getStorageSync('mrc_onboarding_asked')) {
    uni.setStorageSync('mrc_onboarding_asked', '1')
    funnel('onboarding_shown', { from: 'home' })
    showOnboarding.value = true
    return
  }
  showMealSheet.value = true
}
function onOnboardingDone(result: { completed: boolean }) {
  showOnboarding.value = false
  funnel(result.completed ? 'onboarding_completed' : 'onboarding_skipped', {})
  if (result.completed) uni.setStorageSync('mrc_preference_onboarded', '1')
  showMealSheet.value = true
}


/** 锅仔管饭单一入口：弹层里选排菜方式（智能体对话式 / 普通填口味自动排） */
const showPlanMode = ref(false)
function openPlanMode() {
  haptic('light')
  showPlanMode.value = true
}
function onPlanModePick(mode: 'agent' | 'plan') {
  showPlanMode.value = false
  navLocked(() => router.push({ name: mode === 'agent' ? 'meal-agent' : 'weekly-plan' }))
}
function pickMeal(type: 'BREAKFAST' | 'LUNCH' | 'DINNER', options?: { quick?: boolean }) {
  funnel('recommend_click', { mealType: type })
  funnel('meal_sheet_pick', { meal: type, from: 'home', quick: options?.quick ? 1 : 0 })
  const mood = MOODS[Math.floor(Math.random() * MOODS.length)]
  // 坑位：name 导航只认 params（query 会被路由器丢弃），mood/mealType 都走 params
  const quick: Record<string, string> = options?.quick ? { quick: '1' } : {}
  navLocked(() => router.push({ name: 'recipe', params: { mood, mealType: type, ...quick }, query: { ...quick } }))
}
const TABBAR_NAMES = ['home', 'record', 'profile']
function goto(name: string, q?: Record<string, string>) {
  // tabBar 页必须用 switchTab(pushTab)，用 navigateTo(push) 会报 can not navigate to a tab bar page
  haptic('light')
  if (TABBAR_NAMES.includes(name)) navLocked(() => router.pushTab({ name }))
  // 同 openSharedDish 坑位注释：name 导航只认 params，query 会被路由器丢弃
  else navLocked(() => router.push({ name, params: q || {} }))
}
function openCalendarCell(cell: { d: number; records: RecordItem[] }) {
  if (cell.records.length) navLocked(() => router.push({ name: 'calendar', params: { day: String(cell.d) } }))
  else navLocked(() => router.pushTab({ name: 'record' }))
}
</script>

<template>
  <view class="home mrc-hero">
    <wd-navbar title="心情菜谱日历" safe-area-inset-top  custom-style="background-color: transparent !important;" />
    <!-- P1：引导加"我的小程序"。气泡指向右上角 ⋯，前 3 次首页可见，点"知道了"永久关闭 -->
    <view v-if="showFavTip" class="fav-tip">
      <view class="fav-tip__arrow" />
      <view class="fav-tip__body">
        <view class="fav-tip__text">
          <text class="fav-tip__title">点右上角 ⋯ → 收藏</text>
          <text class="fav-tip__sub">明天打开不迷路，锅仔每天给你留菜</text>
        </view>
        <text class="fav-tip__close" role="button" aria-label="关闭提示" @click="dismissFavTip">知道了</text>
      </view>
    </view>
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

      <!-- 分享回流欢迎幕：好友的菜以居中翻转卡呈现——正面大图 + 菜名 + 时长/难度，
           轻点翻面看食材清单；下方主 CTA 给新用户第一条主动路径，「看做法」保留原深链 -->
      <wd-popup
        v-model="isShowShareCurtain"
        position="center"
        root-portal
        :z-index="100"
        :close-on-click-modal="true"
        custom-style="background: transparent; overflow: visible;"
        modal-style="background: rgba(48, 26, 14, 0.82);"
        @close="onCurtainClosed"
      >
        <view class="share-welcome__stage">
          <view class="share-welcome__glow share-welcome__glow--warm" />
          <view class="share-welcome__glow share-welcome__glow--cool" />

          <view
            class="flip"
            :class="{ 'is-flipped': curtainFlipped }"
            role="button"
            :aria-label="curtainFlipped ? '翻回看菜品大图' : '翻面查看食材清单'"
            @click.stop="toggleCurtainFlip"
          >
            <view class="flip__inner">
              <!-- 正面：好友的菜 -->
              <view class="flip__face flip__face--front">
                <image
                  class="flip__img"
                  :src="friendDishImage && !friendDishImgFailed ? imgHero(friendDishImage) : STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'"
                  mode="aspectFill"
                  @error="friendDishImgFailed = true"
                />
                <view class="flip__shade" />
                <view class="flip__caption">
                  <text class="flip__kicker">好友用锅仔安排了</text>
                  <text class="flip__dish">{{ friendDish || '一道好菜' }}</text>
                  <view v-if="curtainChips.time || curtainChips.difficulty" class="flip__chips">
                    <text v-if="curtainChips.time" class="flip__chip">{{ curtainChips.time }} 分钟</text>
                    <text v-if="curtainChips.difficulty" class="flip__chip">{{ curtainChips.difficulty }}</text>
                  </view>
                </view>
                <view class="flip__hint"><text class="flip__hint-icon">↻</text><text>轻点翻面 · 看看食材</text></view>
              </view>
              <!-- 背面：食材清单 -->
              <view class="flip__face flip__face--back">
                <view class="flip__back-head">
                  <text class="flip__back-title">食材清单</text>
                  <text class="flip__back-sub">{{ friendDish || '这道菜' }}</text>
                  <view class="flip__back-rule" />
                </view>
                <scroll-view class="flip__back-scroll" scroll-y :show-scrollbar="false">
                  <view v-for="(item, index) in curtainIngredients" :key="`${item}-${index}`" class="flip__ing">
                    <text class="flip__ing-dot" /><text class="flip__ing-text">{{ item }}</text>
                  </view>
                  <view v-if="!curtainIngredients.length" class="flip__back-empty">锅仔还没拿到食材清单，点下方「看做法」去菜谱里瞧瞧</view>
                </scroll-view>
                <view class="flip__hint flip__hint--back"><text>再轻点一次 · 翻回看菜</text></view>
              </view>
            </view>
          </view>

          <view class="share-curtain__info">
            <text v-if="inviteBonus" class="share-curtain__bonus">你和好友各得 1 次对话额度</text>
          </view>
          <view class="share-curtain__actions">
            <view class="share-curtain__cta" role="button" aria-label="也让锅仔给我安排一道" @click="goMoodFromShare">我也要安排</view>
          </view>
          <view class="share-welcome__subactions">
            <text v-if="sharedRecipeId" class="share-welcome__link" role="button" aria-label="查看这道菜的做法" @click="openSharedDish">看做法</text>
            <view v-if="sharedRecipeId" class="share-welcome__sep" />
            <text class="share-welcome__link share-welcome__link--muted" role="button" aria-label="关闭欢迎弹窗" @click="isShowShareCurtain = false">我知道啦</text>
          </view>
        </view>
      </wd-popup>
      <!-- 今日菜单（TodayBoard）：今日菜牌——菜图右侧通栏出血，信息在左；上下滑换牌（牌堆式微动效，顶牌动、垫牌不动） -->
      <view class="home-board-deck">
      <view
        v-if="todayBoard && !boardSkeleton"
        class="home-entry home-board"
        :class="{ 'is-leaving': boardLeaving, 'is-dragging': boardDragging, 'is-prev': boardDir < 0 }"
        :style="boardDragY ? `transform: translateY(${boardDragY}px)` : ''"
        role="button"
        :aria-label="`锅仔今天留了${todayBoard.recipe.name}，打开看做法，上下滑动可换菜`"
        @click="goCookToday"
        @touchstart="onBoardTouchStart"
        @touchmove.stop.prevent="onBoardTouchMove"
        @touchend="onBoardTouchEnd"
        @touchcancel="onBoardTouchEnd"
      >
        <view :key="todayBoard.recipe.id ?? todayBoard.recipe.name" class="home-board__text">
          <view class="home-board__eyebrow">
            <text class="home-board__eyebrow-dot" /><text>锅仔今天给你留了</text>
            <text class="home-board__hint">上下滑换一道</text>
          </view>
          <view class="home-board__name-row">
            <text class="home-board__name">{{ boardNameParts.main }}</text>
            <text v-if="boardNameParts.drink" class="home-board__drink">配{{ boardNameParts.drink }}</text>
          </view>
          <view class="home-board__meta">
            <text v-if="todayBoard.recipe.cookingTime" class="home-board__chip">{{ todayBoard.recipe.cookingTime }} 分钟</text>
            <text v-if="todayBoard.recipe.difficulty" class="home-board__chip">{{ todayBoard.recipe.difficulty }}</text>
          </view>
          <view class="home-board__cook" role="button" aria-label="去做这道菜" @click.stop="goCookToday">
            <text>去做这道菜</text><text class="home-board__cook-arrow">›</text>
          </view>
        </view>
        <view class="home-board__media">
          <image :key="todayBoard.recipe.image || todayBoard.recipe.name" v-if="todayBoard.recipe.image" class="home-board__img" :src="imgList(todayBoard.recipe.image)" mode="aspectFill" />
          <image v-else class="home-board__img home-board__img--fallback" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
          <view class="home-board__stamp"><text>今</text><text>日</text></view>
        </view>
      </view>
      <!-- 菜牌骨架屏：首载与换菜等待期占位，杜绝「空白卡」；微光扫过表示锅仔正在想 -->
      <view v-else-if="boardSkeleton || boardLoading" class="home-entry home-board home-board--skeleton" aria-label="锅仔正在想今天吃什么" aria-busy="true">
        <view class="home-board__text">
          <view class="home-board__eyebrow"><view class="sk sk--dot" /><view class="sk sk--eyebrow" /></view>
          <view class="sk sk--name" />
          <view class="home-board__meta"><view class="sk sk--chip" /><view class="sk sk--chip sk--chip-short" /></view>
          <view class="sk sk--cta" />
        </view>
        <view class="home-board__media">
          <view class="sk sk--img" />
        </view>
      </view>
      <!-- 首载失败（含超时）：给可点的重试卡，绝不无限转圈 -->
      <view v-else-if="boardLoadFailed" class="home-entry home-board home-board--failed" role="button" aria-label="重新加载今日推荐" @click="retryBoard">
        <view class="home-board__text">
          <view class="home-board__eyebrow"><text class="home-board__eyebrow-dot" /><text>锅仔走神了</text></view>
          <view class="home-board__name-row"><text class="home-board__name">今天的菜没拿到</text></view>
          <view class="home-board__meta"><text class="home-board__chip">网络可能开小差了</text></view>
          <view class="home-board__cook"><text>点我再试一次</text><text class="home-board__cook-arrow">›</text></view>
        </view>
        <view class="home-board__media">
          <image class="home-board__img home-board__img--fallback" :src="STATIC_BASE_URL + '/static/guozai/action_07_empty.png'" mode="aspectFit" />
        </view>
      </view>
      </view>

      <!-- 组件墙：决策收敛——今日三餐是每日主入口（打开即答案），心情推荐降为宽卡，抽奖为子玩法垫底 -->
      <view class="home-bento">
        <view class="home-bento__card home-bento__card--guanfan" role="button" aria-label="打开今日三餐，锅仔已搭配好早午晚" @click="goto('daily-meal-plan')">
          <view class="home-bento__hot">每日主推</view>
          <text class="home-bento__label">打开就有答案</text>
          <text class="home-bento__title">今日三餐</text>
          <text class="home-bento__hint">早午晚已搭配好 · 可换菜</text>
          <image class="home-bento__hero-img" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
        </view>
        <view class="home-bento__card home-bento__card--lucky" role="button" aria-label="选一顿让锅仔按心情推荐" @click="openMealSheet">
          <view class="home-bento__lucky-copy">
            <text class="home-bento__lucky-main">今天吃什么</text>
            <text class="home-bento__lucky-sub">{{ recipeQuotaText }}</text>
          </view>
          <image class="home-bento__lucky-img" :src="STATIC_BASE_URL + '/static/guozai/action_10_thinking.png'" mode="aspectFit" />
          <text class="home-bento__lucky-arrow">›</text>
        </view>
        <!-- 锅仔管饭：单一入口，智能体/普通排法二选一在弹层里完成 -->
        <view class="home-bento__card home-bento__card--agent" role="button" aria-label="打开锅仔管饭，选择排菜方式" @click="openPlanMode">
          <view class="home-bento__hot">HOT</view>
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_06_glasses.png'" mode="aspectFit" />
          <text class="home-bento__name">锅仔管饭</text>
          <text class="home-bento__hint">两种排法随你挑</text>
        </view>
        <view class="home-bento__card home-bento__card--flip" role="button" aria-label="翻牌抽一道菜" @click="openFlip">
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_02_soup.png'" mode="aspectFit" />
          <text class="home-bento__name">翻一翻</text>
        </view>
        <view class="home-bento__card home-bento__card--library" role="button" aria-label="打开锅仔藏菜阁，翻找藏起来的好菜" @click="goto('recipe-library')">
          <view class="home-bento__record-copy">
            <text class="home-bento__name">锅仔藏菜阁</text>
            <text class="home-bento__sub">好菜都藏在阁里</text>
          </view>
          <image class="home-bento__record-img" :src="STATIC_BASE_URL + '/static/guozai/action_06_glasses.png'" mode="aspectFit" />
        </view>
        <!-- 干饭人格（裂变钩子），与上方增长卡填满 4×4 网格 -->
        <view class="home-bento__card home-bento__card--quiz" role="button" aria-label="测试你的干饭人格" @click="goto('personality')">
          <view class="home-bento__quiz-deco" aria-hidden="true">？</view>
          <view class="home-bento__record-copy">
            <text class="home-bento__name">干饭人格</text>
            <text class="home-bento__sub">5 道题测出你的干饭人格</text>
            <view class="home-bento__dots" aria-hidden="true">
              <view v-for="i in 5" :key="i" class="home-bento__dot" />
            </view>
          </view>
          <image class="home-bento__record-img home-bento__record-img--quiz" :src="STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png'" mode="aspectFit" />
        </view>
      </view>

      <MealSheet v-model="showMealSheet" :with-tabbar="true" @select="pickMeal" />
      <GuozaiOnboardingSheet v-model="showOnboarding" :with-tabbar="true" @done="onOnboardingDone" />


      <PlanModeSheet v-model="showPlanMode" @select="onPlanModePick" />

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
              <image v-if="c.records[0]?.imageUrl" class="home-cal__dish" :src="imgThumb(c.records[0].imageUrl)" mode="aspectFill" lazy-load />
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
    <GuozaiFlip ref="flipRef" :visible="showFlip" :items="flipItems" @start="onFlipStart" @pick="onFlipPick" @decoy="onFlipDecoy" @cook="onFlipCook" @close="showFlip = false" />
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

/* 三个首页入口共享同一高度和节奏，内容层级通过颜色区分。 */
/* ===== 每周主题运营位：米金底 + 朱红 CTA，与 bento 暖色家族同温 ===== */
.theme-banner {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 18rpx;
  padding: 20rpx 24rpx;
  border: 2rpx solid rgba(230, 160, 120, 0.22);
  border-radius: 24rpx;
  background: var(--mrc-bento-amber);
  box-shadow: var(--mrc-shadow-soft);
}
.theme-banner__emoji { flex: 0 0 auto; font-size: 40rpx; }
.theme-banner__copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4rpx; }
.theme-banner__title { color: var(--mrc-text-strong); font-size: 26rpx; font-weight: 800; }
.theme-banner__sub { overflow: hidden; color: var(--mrc-text-sub); font-size: 21rpx; white-space: nowrap; text-overflow: ellipsis; }
.theme-banner__go { flex: 0 0 auto; padding: 10rpx 22rpx; border-radius: 999rpx; background: var(--mrc-primary-grad); color: #fff; font-size: 21rpx; font-weight: 800; box-shadow: var(--mrc-shadow-coral); }
.theme-banner__close { position: absolute; top: 4rpx; right: 12rpx; padding: 6rpx 10rpx; color: var(--mrc-text-light); font-size: 30rpx; line-height: 1; }
.theme-banner__close:active { opacity: .6; }

/* 分享回流欢迎幕（wd-popup + 自绘翻转卡）。
   坑位记录：popup 经 root-portal 传送到页面根节点，scoped 类选择器（custom-class 传的类）
   匹配不到根元素——底色/遮罩必须用内联 custom-style / modal-style 传（见模板）。
   WXSS 对 var() 存渐变支持不稳，本块渐变一律写死，不引用 --mrc-* 令牌。
   遮罩是暖深褐，故文字/按钮用亮色系。 */
.share-welcome__stage {
  position: relative;
  display: flex;
  width: 640rpx;
  flex-direction: column;
  align-items: center;
  gap: 26rpx;
  padding: 20rpx 36rpx 56rpx;
  box-sizing: border-box;
}
/* 舞台氛围光：径向渐变（WXSS 对 filter: blur 支持不稳，禁用） */
.share-welcome__glow { position: absolute; border-radius: 50%; pointer-events: none; }
.share-welcome__glow--warm {
  width: 520rpx; height: 520rpx; top: -160rpx; left: -120rpx;
  background: radial-gradient(circle, rgba(255, 138, 76, .34) 0%, rgba(255, 138, 76, 0) 68%);
}
.share-welcome__glow--cool {
  width: 460rpx; height: 460rpx; bottom: -150rpx; right: -130rpx;
  background: radial-gradient(circle, rgba(255, 205, 120, .26) 0%, rgba(255, 205, 120, 0) 70%);
}

/* 翻转卡：3D 两面，轻点切换；整体随舞台垂直水平居中 */
.flip { width: 100%; height: 700rpx; perspective: 1600rpx; }
.flip__inner {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform .55s cubic-bezier(.2, .7, .3, 1);
}
.flip.is-flipped .flip__inner { transform: rotateY(180deg); }
.flip__face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  border-radius: 40rpx;
  overflow: hidden;
  border: 2rpx solid rgba(255, 240, 214, .14);
  box-shadow: 0 28rpx 70rpx rgba(30, 14, 6, .5);
}
.flip__face--front { background: #2a1a10; }
.flip__face--back {
  transform: rotateY(180deg);
  display: flex;
  flex-direction: column;
  padding: 48rpx 40rpx 36rpx;
  box-sizing: border-box;
  background: linear-gradient(165deg, #FFFDF6 0%, #FFE9C9 100%);
}
.flip__img { width: 100%; height: 100%; }
.flip__shade { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(28, 14, 6, 0) 36%, rgba(28, 14, 6, .84) 100%); }
.flip__caption { position: absolute; left: 40rpx; right: 40rpx; bottom: 64rpx; display: flex; flex-direction: column; gap: 12rpx; }
.flip__kicker { color: rgba(255, 240, 214, .82); font-size: 22rpx; font-weight: 600; letter-spacing: 4rpx; }
.flip__dish { color: #FFF9EC; font-size: 50rpx; font-weight: 900; line-height: 1.15; text-shadow: 0 4rpx 20rpx rgba(0, 0, 0, .4); }
.flip__chips { display: flex; gap: 14rpx; margin-top: 10rpx; }
.flip__chip {
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .14);
  border: 2rpx solid rgba(255, 255, 255, .26);
  color: #FFF6E8;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}
.flip__hint {
  position: absolute;
  top: 30rpx;
  right: 30rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(28, 16, 8, .45);
  border: 2rpx solid rgba(255, 240, 214, .16);
  color: #FFF6E8;
  font-size: 20rpx;
  font-weight: 600;
}
.flip__hint-icon { font-size: 26rpx; font-weight: 700; }
.flip__hint--back { top: auto; bottom: 30rpx; right: 30rpx; background: rgba(255, 255, 255, .72); border-color: rgba(154, 122, 88, .18); color: #9A7A58; }
.flip__back-head { display: flex; flex-direction: column; gap: 8rpx; margin-bottom: 6rpx; }
.flip__back-title { color: #3D2519; font-size: 40rpx; font-weight: 900; letter-spacing: 2rpx; }
.flip__back-sub { color: #A27B57; font-size: 24rpx; font-weight: 600; }
.flip__back-rule { width: 64rpx; height: 8rpx; border-radius: 999rpx; background: #FF6B5B; margin: 12rpx 0 14rpx; }
.flip__back-scroll { flex: 1; min-height: 0; }
.flip__ing { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0; border-bottom: 2rpx solid rgba(196, 148, 96, .16); }
.flip__ing-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #FF6B5B; flex-shrink: 0; }
.flip__ing-text { color: #4A3423; font-size: 27rpx; font-weight: 600; }
.flip__back-empty { color: #A27B57; font-size: 24rpx; line-height: 1.7; padding: 32rpx 0; }
.share-welcome__subactions { display: flex; align-items: center; gap: 6rpx; }
.share-welcome__link { color: rgba(255, 246, 232, .92); font-size: 25rpx; font-weight: 700; padding: 14rpx 26rpx; }
.share-welcome__link--muted { color: rgba(255, 246, 232, .55); font-weight: 600; }
.share-welcome__sep { width: 2rpx; height: 24rpx; background: rgba(255, 240, 214, .22); }

/* 奖励行：半透明暖黄药片，给「双方得额度」一个视觉落点 */
.share-curtain__info { display: flex; align-items: center; min-height: 36rpx; }
.share-curtain__bonus {
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: rgba(255, 205, 120, .12);
  border: 2rpx solid rgba(255, 205, 120, .22);
  color: rgba(255, 224, 182, .95);
  font-size: 23rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}
.share-curtain__actions { display: flex; align-items: center; gap: 20rpx; }
/* 主 CTA：WXSS 对 var() 存渐变支持不稳，渐变写死 */
.share-curtain__cta {
  display: flex;
  width: 480rpx;
  min-height: 92rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #FFB98C 0%, #FF6B5B 100%);
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
  box-shadow: 0 14rpx 32rpx rgba(255, 107, 91, .38);
}
.share-curtain__cta:active { transform: scale(.97); }

.home-entry { border-radius: 30rpx; box-sizing: border-box; }
/* 牌堆底座：菜牌下面垫两张微旋转的静止牌——换牌时只有顶牌在动，位置感稳定（堆叠卡片语言） */
.home-board-deck { position: relative; margin-bottom: 16rpx; }
.home-board-deck::before,
.home-board-deck::after { content: ''; position: absolute; left: 16rpx; right: 16rpx; top: 0; height: 100%; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: var(--mrc-surface); pointer-events: none; }
.home-board-deck::before { top: -10rpx; transform: rotate(-1.1deg); opacity: .5; }
.home-board-deck::after { top: -5rpx; transform: rotate(.7deg); opacity: .8; }
/* 今日菜牌（TodayBoard）：图占满右侧全高、杂志式出血；左侧信息三级（眉标/菜名/标签），CTA 为实心药丸。
   z-index 高于 hero 的立绘(1)/气泡(3)：滑动换牌时顶牌整张压在 hero 装饰之上，像掀牌而不是元素互相穿插。 */
.home-board { position: relative; z-index: 3; display: flex; align-items: stretch; height: 228rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: linear-gradient(120deg, var(--mrc-surface-peach) 0%, var(--mrc-surface) 62%); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); overflow: hidden; }
.home-board__text { display: flex; flex: 1; min-width: 0; flex-direction: column; justify-content: center; gap: 10rpx; padding: 22rpx 6rpx 22rpx 30rpx; }
.home-board__eyebrow { display: flex; align-items: center; gap: 10rpx; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.home-board__eyebrow-dot { flex: 0 0 auto; width: 10rpx; height: 10rpx; border-radius: 50%; background: var(--mrc-accent); box-shadow: 0 0 0 6rpx var(--mrc-accent-soft); }
.home-board__name-row { display: flex; align-items: center; gap: 12rpx; }
.home-board__name { color: var(--mrc-text-strong); font-size: 37rpx; font-weight: 800; line-height: 1.22; letter-spacing: -.5rpx; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; overflow: hidden; }
.home-board__drink { flex-shrink: 0; padding: 4rpx 14rpx; border: 1rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 18rpx; font-weight: 700; }
.home-board__meta { display: flex; flex-wrap: wrap; gap: 10rpx; }
.home-board__chip { padding: 4rpx 16rpx; border: 1rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-surface-2); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 600; }
.home-board__cook { align-self: flex-start; display: inline-flex; align-items: center; gap: 4rpx; margin-top: 2rpx; padding: 11rpx 26rpx; border-radius: 999rpx; background: var(--mrc-primary-deep); color: #fff; font-size: 21rpx; font-weight: 700; box-shadow: 0 6rpx 16rpx rgba(255, 107, 91, .32); transition: transform .15s ease; }
.home-board__cook:active { transform: scale(.95); }
.home-board__cook-arrow { font-size: 26rpx; line-height: 1; }
.home-board__media { position: relative; display: flex; margin: 12rpx; width: 248rpx; flex: 0 0 248rpx; border-radius: 22rpx; overflow: hidden; box-shadow: 0 6rpx 18rpx rgba(96, 52, 28, .12); }
.home-board__img { width: 100%; height: 100%; }
.home-board__img--fallback { padding: 34rpx; box-sizing: border-box; background: var(--mrc-surface-2); }
/* 朱红印章：竖排「今日」，微旋转的盖章感 */
.home-board__stamp { position: absolute; top: 12rpx; left: 12rpx; display: flex; flex-direction: column; align-items: center; padding: 10rpx 9rpx 12rpx; border-radius: 10rpx; background: linear-gradient(160deg, #FF7A5C 0%, #E14E34 100%); color: #FFFFFF; font-size: 23rpx; font-weight: 800; line-height: 1.18; letter-spacing: 0; box-shadow: 0 4rpx 12rpx rgba(180, 52, 28, .38); transform: rotate(-5deg); }
.home-board__stamp text { display: block; }
/* 上滑换菜微动效：跟手位移由内联 transform 负责（拖动中关闭 transition 保证跟手），
   这里管入场——文字自下而上错落浮现、图片淡入——与离场整体上浮淡出 */
.home-board { transition: transform .32s cubic-bezier(.22, .61, .36, 1); }
.home-board.is-dragging { transition: none; }
.home-board__text,
.home-board__media { transition: opacity .26s ease, transform .26s ease; }
.home-board.is-leaving .home-board__text,
.home-board.is-leaving .home-board__media { opacity: 0; transform: translateY(-16rpx); }
@keyframes board-text-in {
  from { opacity: 0; transform: translateY(26rpx); }
  to { opacity: 1; transform: translateY(0); }
}
.home-board__text > .home-board__eyebrow,
.home-board__text > .home-board__name-row,
.home-board__text > .home-board__meta,
.home-board__text > .home-board__cook {
  animation: board-text-in .48s cubic-bezier(.22, .61, .36, 1) both;
}
.home-board__text > .home-board__name-row { animation-delay: .07s; }
.home-board__text > .home-board__meta { animation-delay: .14s; }
/* CTA 只做透明度入场：text-in 的 fill 会压过 :active 的 scale 按压反馈 */
.home-board__text > .home-board__cook { animation-name: board-fade-in; }
@keyframes board-fade-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes board-img-in {
  from { opacity: 0; transform: scale(1.05); }
  to { opacity: 1; transform: scale(1); }
}
.home-board__img { animation: board-img-in .5s cubic-bezier(.22, .61, .36, 1) both; }
/* 「上滑换一道」微提示：轻浮动呼吸，学会手势后自然被忽略 */
.home-board__hint { margin-left: auto; color: var(--mrc-text-light); font-size: 18rpx; font-weight: 600; letter-spacing: 1rpx; animation: board-hint-float 2.4s ease-in-out 1.2s infinite; }
@keyframes board-hint-float {
  0%, 100% { transform: translateY(0); opacity: .55; }
  50% { transform: translateY(-4rpx); opacity: 1; }
}
/* 下滑回看：入场自上而下浮现，离场向下淡出（与上滑镜像） */
@keyframes board-text-in-down {
  from { opacity: 0; transform: translateY(-26rpx); }
  to { opacity: 1; transform: translateY(0); }
}
.home-board.is-prev.is-leaving .home-board__text,
.home-board.is-prev.is-leaving .home-board__media { transform: translateY(16rpx); }
.home-board.is-prev .home-board__text > .home-board__eyebrow,
.home-board.is-prev .home-board__text > .home-board__name-row,
.home-board.is-prev .home-board__text > .home-board__meta {
  animation-name: board-text-in-down;
}
@media (prefers-reduced-motion: reduce) {
  .home-board__text > .home-board__eyebrow,
  .home-board__text > .home-board__name-row,
  .home-board__text > .home-board__meta,
  .home-board__text > .home-board__cook,
  .home-board__img,
  .home-board__hint { animation: none; }
}
/* 菜牌骨架屏：复用 .home-board 卡壳，骨架块白色半透明 + 高光横向扫过 */
.home-board--skeleton { pointer-events: none; }
.sk { position: relative; overflow: hidden; background: rgba(255, 255, 255, .72); border-radius: 10rpx; }
.sk::after { content: ''; position: absolute; inset: 0; transform: translateX(-100%); background: linear-gradient(90deg, transparent, rgba(255, 255, 255, .95), transparent); animation: sk-sweep 1.4s ease-in-out infinite; }
@keyframes sk-sweep { to { transform: translateX(100%); } }
.sk--dot { width: 10rpx; height: 10rpx; border-radius: 50%; }
.sk--eyebrow { width: 190rpx; height: 18rpx; border-radius: 999rpx; }
.sk--name { width: 320rpx; max-width: 100%; height: 44rpx; border-radius: 12rpx; }
.sk--chip { width: 110rpx; height: 30rpx; border-radius: 999rpx; }
.sk--chip-short { width: 84rpx; }
.sk--cta { width: 200rpx; height: 52rpx; border-radius: 999rpx; }
.sk--img { width: 100%; height: 100%; border-radius: 22rpx; }
@media (prefers-reduced-motion: reduce) {
  .sk::after { animation: none; transform: translateX(0); opacity: .5; }
}
/* P1 收藏引导 */ .fav-tip { position: relative; margin: 16rpx 0 0; padding: 0 24rpx; } .fav-tip__arrow { position: absolute; top: -10rpx; right: 80rpx; width: 0; height: 0; border-left: 12rpx solid transparent; border-right: 12rpx solid transparent; border-bottom: 12rpx solid var(--mrc-tip-bg); } .fav-tip__body { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 20rpx; border-radius: 20rpx; background: var(--mrc-tip-bg); border: 2rpx solid var(--mrc-tip-border); box-shadow: 0 4rpx 12rpx rgba(255,180,80,0.15); } .fav-tip__emoji { font-size: 36rpx; } .fav-tip__text { flex: 1; display: flex; flex-direction: column; gap: 4rpx; min-width: 0; } .fav-tip__title { font-size: 26rpx; font-weight: 700; color: var(--mrc-tip-title); } .fav-tip__sub { font-size: 22rpx; color: var(--mrc-tip-sub); line-height: 1.4; } .fav-tip__close { flex-shrink: 0; padding: 8rpx 18rpx; border-radius: 999rpx; background: #FFB98C; color: #fff; font-size: 22rpx; font-weight: 600; } /* 组件墙：4 列网格，入口分层——三餐主卡(2×2 HOT) / 抽奖小卡(1×1) / 双宽卡(2×1) */
.home-bento { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); grid-auto-rows: 158rpx; gap: 14rpx; margin: 24rpx 0 32rpx; }
.home-bento__card { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; overflow: hidden; min-width: 0; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); box-sizing: border-box; transition: transform .15s ease; }
.home-bento__card:active { transform: scale(.97); }
.home-bento__hot { position: absolute; right: 12rpx; top: 12rpx; z-index: 2; padding: 3rpx 12rpx; border-radius: 999rpx; background: linear-gradient(135deg, #FFB98C, #EF5A3C); color: #fff; font-size: 17rpx; font-weight: 800; letter-spacing: 1rpx; box-shadow: 0 4rpx 10rpx rgba(239, 90, 60, 0.35); }
.home-bento__card--guanfan { grid-column: span 2; grid-row: span 2; align-items: flex-start; justify-content: flex-start; gap: 9rpx; padding: 26rpx 28rpx; border-color: transparent; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }
.home-bento__card--guanfan .home-bento__label { margin-top: 14rpx; color: rgba(255, 255, 255, 0.82); font-size: 20rpx; }
.home-bento__card--guanfan .home-bento__title { color: #fff; font-size: 40rpx; font-weight: 800; letter-spacing: 1rpx; }
.home-bento__card--guanfan .home-bento__hint { color: rgba(255, 255, 255, 0.9); font-size: 20rpx; }
.home-bento__hero-img { position: absolute; right: 0; bottom: 0; width: 172rpx; height: 172rpx; filter: drop-shadow(0 8rpx 14rpx rgba(120, 40, 20, 0.22)); }
/* 抽奖三小卡：暖色家族三段微层次（暖阳黄 / 蜜桃 / 奶油），告别灰白，与红主卡同一色温 */

.home-bento__card--flip { border-color: rgba(230, 160, 120, 0.20); background: var(--mrc-bento-flip); }
/* 锅仔管饭：薄荷绿 → 暖杏金（智能体的暖光感），HOT 红徽章与红主卡呼应 */
.home-bento__card--agent { border-color: transparent; background: var(--mrc-bento-agent); }
.home-bento__img { width: 78rpx; height: 78rpx; filter: drop-shadow(0 4rpx 8rpx rgba(96, 52, 28, 0.10)); }
.home-bento__name { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }
.home-bento__sub { color: var(--mrc-bento-text-sub); font-size: 18rpx; }
.home-bento__hint { color: var(--mrc-bento-text-sub); font-size: 18rpx; line-height: 1.3; }
.home-bento__card--lucky { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 22rpx 0 26rpx; border-color: transparent; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }
.home-bento__lucky-copy { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 6rpx; }
.home-bento__lucky-main { color: #fff; font-size: 27rpx; font-weight: 800; }
.home-bento__lucky-sub { color: rgba(255, 255, 255, 0.85); font-size: 18rpx; }
.home-bento__lucky-img { width: 88rpx; height: 88rpx; flex-shrink: 0; margin-right: 2rpx; filter: drop-shadow(0 4rpx 8rpx rgba(120, 40, 20, 0.18)); }
.home-bento__lucky-arrow { flex-shrink: 0; color: rgba(255, 255, 255, 0.95); font-size: 44rpx; font-weight: 300; }
.home-bento__card--record { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 20rpx 0 26rpx; background: var(--mrc-surface-mint); }
.home-bento__card--library { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 20rpx 0 26rpx; border-color: transparent; background: var(--mrc-bento-library); }
/* 与右侧今日三餐宽卡对齐标题字号字重（通用 name 24rpx 是给小卡用的） */
.home-bento__card--library .home-bento__name { font-size: 27rpx; }
.home-bento__record-copy { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 6rpx; }
.home-bento__record-img { width: 92rpx; height: 92rpx; flex-shrink: 0; filter: drop-shadow(0 4rpx 8rpx rgba(96, 52, 28, 0.10)); }
.home-bento__card--quiz { position: relative; overflow: hidden; grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 20rpx 0 26rpx; border-color: transparent; background: linear-gradient(135deg, #FFEEE9, #FFE0D4); }
/* 测题氛围：右上超大半透明问号锁定「测试」语义，右下淡光晕托住锅仔 */
.home-bento__quiz-deco {
  position: absolute;
  top: -30rpx;
  right: 116rpx;
  color: rgba(232, 107, 91, 0.15);
  font-size: 100rpx;
  font-weight: 900;
  line-height: 1;
  pointer-events: none;
}
.home-bento__card--quiz::after {
  content: '';
  position: absolute;
  right: -34rpx;
  bottom: -46rpx;
  width: 190rpx;
  height: 190rpx;
  border-radius: 50%;
  background: radial-gradient(closest-side, rgba(255, 107, 91, 0.16), transparent);
  pointer-events: none;
}
.home-bento__card--quiz .home-bento__name { font-size: 30rpx; font-weight: 900; }
/* 五个点重做成「答题选项」观感：白底 + 胭脂描边圆点，奇偶错落但个个清晰，不再像脏点 */
.home-bento__dots { display: flex; align-items: center; gap: 9rpx; margin-top: 4rpx; }
.home-bento__dot {
  width: 13rpx;
  height: 13rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  border: 3rpx solid rgba(232, 107, 91, 0.55);
  opacity: 1;
}
.home-bento__dot:nth-child(odd) { opacity: .75; }
.home-bento__record-img--quiz { width: 104rpx; height: 104rpx; filter: drop-shadow(0 6rpx 14rpx rgba(96, 52, 28, 0.16)); }

.meal-sheet { padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom)); }
.meal-sheet__head { padding: 8rpx 8rpx 20rpx; }
.meal-sheet__title { display: block; color: var(--mrc-text-strong); font-size: 30rpx; font-weight: 800; }
.meal-sheet__sub { display: block; margin-top: 6rpx; color: var(--mrc-text-sub); font-size: 22rpx; }
.meal-sheet__options { display: flex; flex-direction: column; gap: 14rpx; }
.meal-option { display: flex; align-items: center; gap: 18rpx; width: 100%; min-height: 116rpx; padding: 18rpx 22rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 24rpx; background: var(--mrc-surface-2); box-shadow: var(--mrc-shadow-soft); box-sizing: border-box; text-align: left; line-height: 1.3; }
.meal-option::after { border: none; }
.meal-option__emoji { font-size: 44rpx; }
.meal-option__name { color: var(--mrc-text-deep); font-size: 30rpx; font-weight: 800; }
.meal-option__desc { margin-left: auto; color: var(--mrc-text-sub); font-size: 21rpx; }
.meal-sheet__cancel { width: 100%; margin-top: 20rpx; min-height: 92rpx; border-radius: 24rpx; color: var(--mrc-text-sub); background: var(--mrc-surface-2); font-size: 27rpx; font-weight: 700; }
.meal-sheet__cancel::after { border: none; }

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
