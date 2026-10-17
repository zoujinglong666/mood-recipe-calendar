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
import { toastError, toastSuccess } from '../../utils/toast'
import { acceptShareInvite } from '../../api/share'
import { onLoad } from '@dcloudio/uni-app'
import { fetchCompanionMessage, fetchRecordsByMonth, type CompanionMessage, type RecordItem } from '../../api/records'
import { fetchRecipePool, fetchRecipeQuota, type RecipeItem, type UsageQuotaView } from '../../api/recipes'
import { fetchDailyBoard, refreshDailyBoard, generateDailyMenuCover, splitComboDishName, type DailyBoard } from '../../api/dailyMenu'
import { saveCookingDraft } from '../../utils/cookingDraft'
import GuozaiWheel from '../../components/guozai/GuozaiWheel.vue'
import GuozaiNine from '../../components/guozai/GuozaiNine.vue'
import GuozaiFlip from '../../components/guozai/GuozaiFlip.vue'
import { useUserStore } from '../../stores/user'
import { funnel } from '../../utils/funnel'

definePage({ name: 'home', layout: 'tabbar', style: { navigationStyle: 'custom', navigationBarTitleText: '首页' } })
const userStore = useUserStore()
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
    loadCompanion().catch(() => {})
    loadTodayBoard().catch(() => {})
    // 菜池提前加载：转盘/九宫格/翻牌点开即用，不等点击后再拉
    void preloadRecipePool()
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
/** 今日菜单（TodayBoard）：打开即有的零输入答案；加载失败静默，不影响首屏其余内容。 */
const todayBoard = ref<DailyBoard>()
async function loadTodayBoard() {
  try {
    todayBoard.value = await fetchDailyBoard()
    if (todayBoard.value?.recipe) funnel('recipe_delivered', { name: todayBoard.value.recipe.name })
    backfillBoardCover()
  }
  catch {
    todayBoard.value = undefined
  }
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

/** 菜池预加载：转盘/九宫格/翻牌共用。onShow 即拉取并预热图片，打开弹层时秒开不白屏。 */
const recipePoolCache = ref<RecipeItem[]>([])
const recipePoolAt = ref(0)
const RECIPE_POOL_TTL = 5 * 60 * 1000
let poolPreloading = false
async function preloadRecipePool(force = false) {
  if (poolPreloading) return
  if (!force && recipePoolCache.value.length && Date.now() - recipePoolAt.value < RECIPE_POOL_TTL)
    return
  poolPreloading = true
  try {
    const pool = await fetchRecipePool()
    recipePoolCache.value = pool
    recipePoolAt.value = Date.now()
    // 预热前 24 张封面：抽样大多落在其中，开抽时图片已在本地缓存，不再「转完了图才出来」
    pool.slice(0, 24).forEach((item) => {
      if (!item.image)
        return
      uni.getImageInfo({ src: item.image, fail: () => {} })
    })
  }
  catch { /* 预加载失败静默：打开弹层时还有兜底拉取 */ }
  finally {
    poolPreloading = false
  }
}
/** 取菜池：缓存新鲜直接返回；否则现拉（预加载通常已备好）。 */
function recipePool(): Promise<RecipeItem[]> {
  if (recipePoolCache.value.length && Date.now() - recipePoolAt.value < RECIPE_POOL_TTL)
    return Promise.resolve(recipePoolCache.value)
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

/** 锅仔转盘：扇区图源=全库 AI 推荐菜随机抽样；结果永远是真实接口返回的那道菜。 */
const showWheel = ref(false)
const wheelPool = ref<{ name: string, image?: string }[]>([])
const wheelRef = ref<InstanceType<typeof GuozaiWheel>>()
async function openWheel() {
  showWheel.value = true
  try {
    const pool = await recipePool()
    // 当前这道菜固定占一格，其余 7 格去重 + 同类限流抽样
    const current = todayBoard.value?.recipe
    const others = sampleDiverseDishes(pool, 7, current?.name)
    wheelPool.value = [
      ...(current?.name ? [{ name: current.name, image: current.image }] : []),
      ...others.map(item => ({ name: item.name, image: item.image })),
    ]
  }
  catch {
    wheelPool.value = []
  }
}
async function onWheelSpin() {
  try {
    const board = await refreshDailyBoard()
    todayBoard.value = board
    wheelRef.value?.land(board.recipe.name, board.recipe.image)
  }
  catch (e: any) {
    toastError(e, '今天没有别的菜可换啦')
    wheelRef.value?.fail()
  }
}

/** 锅仔九宫格（再来一道）：外圈 8 格=真实 AI 菜池抽样，停在即所得，「就做这道」直达做菜页。 */
const showNine = ref(false)
const nineItems = ref<{ name: string, image?: string }[]>([])
const nineRecipes = ref<RecipeItem[]>([])
const ninePick = ref<RecipeItem>()
const nineRef = ref<InstanceType<typeof GuozaiNine>>()
async function openNine() {
  showNine.value = true
  try {
    const pool = await recipePool()
    const sampled = sampleDiverseDishes(pool, 8)
    nineRecipes.value = sampled
    nineItems.value = sampled.map(item => ({ name: item.name, image: item.image }))
  }
  catch {
    nineRecipes.value = []
    nineItems.value = []
  }
}
function onNineStart() {
  // 先定结果再驱动灯带：停在的格子就是真实可做的那道菜
  if (!nineRecipes.value.length)
    return
  const index = Math.floor(Math.random() * nineRecipes.value.length)
  ninePick.value = nineRecipes.value[index]
  nineRef.value?.land(index)
}
function onNineCook(index: number) {
  const recipe = ninePick.value && nineRecipes.value[index] === ninePick.value ? ninePick.value : nineRecipes.value[index]
  if (!recipe) {
    toastError(new Error('这一格是锅仔的彩蛋，再翻一次吧'), '翻到装饰格了')
    return
  }
  showNine.value = false
  saveCookingDraft(recipe, '满足')
  router.push({ name: 'cooking' })
}

/** 锅仔翻牌（squarenine 复刻）：洗牌后任选一张，翻开的即真实抽中的那道菜。 */
const showFlip = ref(false)
const flipItems = ref<{ name: string, image?: string }[]>([])
const flipPool = ref<RecipeItem[]>([])
const flipPick = ref<RecipeItem>()
const flipRef = ref<InstanceType<typeof GuozaiFlip>>()
async function openFlip() {
  showFlip.value = true
  try {
    const pool = await recipePool()
    flipPool.value = pool
    flipItems.value = flipBoardItems(pool)
  }
  catch {
    flipPool.value = []
    flipItems.value = []
  }
}
/** 翻牌牌面：有图优先抽样，不够 8 张用真实菜补齐（哪怕暂时无图），不再拿彩蛋词充数。 */
function flipBoardItems(pool: RecipeItem[]): { name: string, image?: string }[] {
  const sampled = sampleDiverseDishes(pool, 8)
  const items = sampled.map(item => ({ name: item.name, image: item.image }))
  for (const item of pool) {
    if (items.length >= 8)
      break
    if (item?.name && !items.some(f => f.name === item.name))
      items.push({ name: item.name, image: item.image })
  }
  return items
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
    flipItems.value = flipBoardItems(flipPool.value)
  }
  // 先定中奖菜，再抽「其余 7 张」——排除中奖菜同名，保证全翻开后 8 格不重样
  // sampleDiverseDishes 只取有图菜；菜池全部无图时回退任意一道，保证 deal() 一定被调用
  const winners = sampleDiverseDishes(flipPool.value, 1)
  const pick = winners[0]
    || flipPool.value.find(item => item?.name && item.name !== '锅仔牌')
  if (!pick) {
    toastError(new Error('菜谱还没备好，稍后再来试试'), '翻一翻')
    flipRef.value?.fail()
    return
  }
  const others = sampleDiverseDishes(flipPool.value, 7, pick.name)
  flipPick.value = pick
  flipItems.value = [
    ...others.map(item => ({ name: item.name, image: item.image })),
    { name: '锅仔牌' },
  ]
  flipRef.value?.deal({ name: pick.name, image: pick.image })
}
function onFlipCook() {
  const recipe = flipPick.value
  if (!recipe)
    return
  showFlip.value = false
  saveCookingDraft(recipe, '满足')
  router.push({ name: 'cooking' })
}
function goCookToday() {
  const board = todayBoard.value
  if (!board?.recipe?.id)
    return
  // 今日菜单已经是一道确定的菜，直接把它交给做菜智能体，不能再次进入推荐页。
  saveCookingDraft(board.recipe, '满足')
  router.push({ name: 'cooking' })
}

/** 分享回流承接：好友通过分享卡打开 → 双方各 +1 次对话额度；带上好友分享的那道菜名做欢迎 */
const friendDish = ref('')
const inviteBonus = ref(false)
onLoad((query) => {
  const sharer = String(query?.sharer || '')
  const dish = String(query?.dish || '')
  if (dish)
    friendDish.value = dish
  if (!sharer || sharer === userStore.openid)
    return
  acceptShareInvite(sharer)
    .then((res) => {
      if (res?.grantedBonus) {
        inviteBonus.value = true
        toastSuccess('好友分享的福利：今日对话 +1 次')
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

onShow(() => {
  now.value = new Date()
  loadData()
  maybeShowFavTip()
  funnel('home_view')
})

/** 转发给好友/群：必须定义 onShareAppMessage 胶囊「···」里的转发才可用 */
onShareAppMessage(() => {
  const sharer = userStore.openid
  // 分享卡带菜：好友点开直接看到这道菜 + 你的邀请，回流后双方各得 1 次对话额度
  const dish = todayBoard.value?.recipe?.name || ''
  const params = [
    sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
    dish ? `dish=${encodeURIComponent(dish)}` : '',
  ].filter(Boolean).join('&')
  const path = `/pages/index/index${params ? `?${params}` : ''}`
  const title = dish ? `我用锅仔安排了「${dish}」，也替你留了一份` : '锅仔 · 按心情帮你决定今天吃什么'
  // 记录分享：发放「分享家」徽章 + 非会员当日 +1 次对话激励（失败静默，不影响转发）
  void recordShare('home').catch(() => {})
  return { title, path }
})
/** 分享到朋友圈：必须定义 onShareTimeline 才可用 */
onShareTimeline(() => {
  const sharer = userStore.openid
  return {
    title: '锅仔 · 按心情帮你决定今天吃什么',
    query: sharer ? `sharer=${encodeURIComponent(sharer)}` : '',
  }
})
const MOODS = ['开心', '平静', '疲惫', '焦虑', '难过', '嘴馋', '低落', '想家', '期待', '满足', '得意', '害羞']
/** 「让锅仔替你决定」点击展开餐别选择器：选一顿后带上餐别去推荐页，让推荐有《膳食指南》可依 */
const showMealSheet = ref(false)
function openMealSheet() { showMealSheet.value = true }
function pickMeal(type: 'BREAKFAST' | 'LUNCH' | 'DINNER') {
  showMealSheet.value = false
  funnel('recommend_click', { mealType: type })
  const mood = MOODS[Math.floor(Math.random() * MOODS.length)]
  router.push({ name: 'recipe', params: { mood }, query: { mood, mealType: type } })
}
const TABBAR_NAMES = ['home', 'record', 'profile']
function goto(name: string, q?: Record<string, string>) {
  // tabBar 页必须用 switchTab(pushTab)，用 navigateTo(push) 会报 can not navigate to a tab bar page
  if (TABBAR_NAMES.includes(name)) router.pushTab({ name })
  else router.push({ name, query: q || {} })
}
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

      <!-- 分享回流欢迎条：好友点开的落地页，先看到这道菜和福利，再自然进入今日菜牌 -->
      <view v-if="friendDish || inviteBonus" class="share-welcome">
        <text class="share-welcome__emoji">🍲</text>
        <text v-if="friendDish" class="share-welcome__text">好友用锅仔安排了「{{ friendDish }}」</text>
        <text v-if="inviteBonus" class="share-welcome__bonus">你和好友各得 1 次对话额度</text>
      </view>

      <!-- 今日菜单（TodayBoard）：今日菜牌——菜图右侧通栏出血，信息在左 -->
      <view v-if="todayBoard" class="home-entry home-board" role="button" :aria-label="`锅仔今天留了${todayBoard.recipe.name}，打开看做法`" @click="goCookToday">
        <view class="home-board__text">
          <view class="home-board__eyebrow"><text class="home-board__eyebrow-dot" /><text>锅仔今天给你留了</text></view>
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
          <image v-if="todayBoard.recipe.image" class="home-board__img" :src="todayBoard.recipe.image" mode="aspectFill" />
          <image v-else class="home-board__img home-board__img--fallback" :src="STATIC_BASE_URL + '/static/guozai/action_01_bowl.png'" mode="aspectFit" />
          <view class="home-board__stamp"><text>今</text><text>日</text></view>
        </view>
      </view>

      <!-- P1：引导加"我的小程序"。气泡指向右上角 ⋯，前 3 次首页可见，点"知道了"永久关闭 -->
      <view v-if="showFavTip" class="fav-tip">
        <view class="fav-tip__arrow" />
        <view class="fav-tip__body">
          <text class="fav-tip__emoji">⭐</text>
          <view class="fav-tip__text">
            <text class="fav-tip__title">点右上角 ⋯ → 收藏</text>
            <text class="fav-tip__sub">明天打开不迷路，锅仔每天给你留菜</text>
          </view>
          <text class="fav-tip__close" role="button" aria-label="关闭提示" @click="dismissFavTip">知道了</text>
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
        <view class="home-bento__card home-bento__card--agent" role="button" aria-label="打开锅仔管饭，对话式安排一整周" @click="goto('meal-agent')">
          <view class="home-bento__hot">HOT</view>
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_06_glasses.png'" mode="aspectFit" />
          <text class="home-bento__name">锅仔管饭</text>
          <text class="home-bento__hint">安排一整周</text>
        </view>
        <view class="home-bento__card home-bento__card--wheel" role="button" aria-label="转盘抽一道菜" @click="openWheel">
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_08_peek.png'" mode="aspectFit" />
          <text class="home-bento__name">转一转</text>
        </view>
        <view class="home-bento__card home-bento__card--nine" role="button" aria-label="九宫格跑马灯抽一道菜" @click="openNine">
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_09_celebrate.png'" mode="aspectFit" />
          <text class="home-bento__name">跑一圈</text>
        </view>
        <view class="home-bento__card home-bento__card--flip" role="button" aria-label="翻牌抽一道菜" @click="openFlip">
          <image class="home-bento__img" :src="STATIC_BASE_URL + '/static/guozai/action_02_soup.png'" mode="aspectFit" />
          <text class="home-bento__name">翻一翻</text>
        </view>
        <view class="home-bento__card home-bento__card--library" role="button" aria-label="打开菜谱库，搜索和分类浏览菜谱" @click="goto('recipe-library')">
          <view class="home-bento__record-copy">
            <text class="home-bento__name">菜谱库</text>
            <text class="home-bento__sub">搜菜·按心情挑</text>
          </view>
          <image class="home-bento__record-img" :src="STATIC_BASE_URL + '/static/guozai/action_06_glasses.png'" mode="aspectFit" />
        </view>
      </view>

      <wd-popup v-model="showMealSheet" position="bottom" :safe-area-inset-bottom="true" custom-style="border-radius: 40rpx 40rpx 0 0; overflow: hidden; background: var(--mrc-surface);">
        <view class="meal-sheet">
          <view class="meal-sheet__head">
            <text class="meal-sheet__title">今天吃什么</text>
            <text class="meal-sheet__sub">选一顿，锅仔按《中国居民膳食指南》帮你搭配</text>
          </view>
          <view class="meal-sheet__options">
            <button class="meal-option" @click="pickMeal('BREAKFAST')">
              <text class="meal-option__emoji">🌅</text>
              <text class="meal-option__name">早餐</text>
              <text class="meal-option__desc">唤醒一天的元气</text>
            </button>
            <button class="meal-option" @click="pickMeal('LUNCH')">
              <text class="meal-option__emoji">🍚</text>
              <text class="meal-option__name">午餐</text>
              <text class="meal-option__desc">给下午充好电</text>
            </button>
            <button class="meal-option" @click="pickMeal('DINNER')">
              <text class="meal-option__emoji">🌙</text>
              <text class="meal-option__name">晚餐</text>
              <text class="meal-option__desc">好好收尾这一天</text>
            </button>
          </view>
          <button class="meal-sheet__cancel" @click="showMealSheet = false">取消</button>
        </view>
      </wd-popup>

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
    <GuozaiWheel ref="wheelRef" :visible="showWheel" :items="wheelPool" @spin="onWheelSpin" @close="showWheel = false" />
    <GuozaiNine ref="nineRef" :visible="showNine" :items="nineItems" @start="onNineStart" @cook="onNineCook" @close="showNine = false" />
    <GuozaiFlip ref="flipRef" :visible="showFlip" :items="flipItems" @start="onFlipStart" @cook="onFlipCook" @close="showFlip = false" />
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
.share-welcome {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 12rpx 24rpx 0;
  padding: 16rpx 24rpx;
  background: var(--mrc-surface-sun, #FFF6E0);
  border: 2rpx solid var(--mrc-border-light);
  border-radius: 24rpx;
}
.share-welcome__emoji { font-size: 30rpx; }
.share-welcome__text { font-size: 24rpx; color: var(--mrc-text-deep); font-weight: 700; }
.share-welcome__bonus { margin-left: auto; font-size: 21rpx; color: var(--mrc-accent); font-weight: 700; }

.home-entry { border-radius: 30rpx; box-sizing: border-box; }
/* 今日菜牌（TodayBoard）：图占满右侧全高、杂志式出血；左侧信息三级（眉标/菜名/标签），CTA 为实心药丸。 */
.home-board { position: relative; display: flex; align-items: stretch; height: 228rpx; margin-bottom: 16rpx; border: 2rpx solid var(--mrc-border-light); border-radius: 30rpx; background: linear-gradient(120deg, var(--mrc-surface-peach) 0%, var(--mrc-surface) 62%); box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); overflow: hidden; }
.home-board__text { display: flex; flex: 1; min-width: 0; flex-direction: column; justify-content: center; gap: 10rpx; padding: 22rpx 6rpx 22rpx 30rpx; }
.home-board__eyebrow { display: flex; align-items: center; gap: 10rpx; color: var(--mrc-accent); font-size: 19rpx; font-weight: 800; letter-spacing: 2rpx; }
.home-board__eyebrow-dot { flex: 0 0 auto; width: 10rpx; height: 10rpx; border-radius: 50%; background: var(--mrc-accent); box-shadow: 0 0 0 6rpx var(--mrc-accent-soft); }
.home-board__name-row { display: flex; align-items: center; gap: 12rpx; }
.home-board__name { color: var(--mrc-text-strong); font-size: 37rpx; font-weight: 800; line-height: 1.22; letter-spacing: -.5rpx; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; overflow: hidden; }
.home-board__drink { flex-shrink: 0; padding: 4rpx 14rpx; border: 1rpx solid var(--mrc-border-light); border-radius: 999rpx; background: var(--mrc-accent-soft); color: var(--mrc-accent); font-size: 18rpx; font-weight: 700; }
.home-board__meta { display: flex; flex-wrap: wrap; gap: 10rpx; }
.home-board__chip { padding: 4rpx 16rpx; border: 1rpx solid var(--mrc-border-light); border-radius: 999rpx; background: rgba(255, 255, 255, .8); color: var(--mrc-text-sub); font-size: 18rpx; font-weight: 600; }
.home-board__cook { align-self: flex-start; display: inline-flex; align-items: center; gap: 4rpx; margin-top: 2rpx; padding: 11rpx 26rpx; border-radius: 999rpx; background: var(--mrc-primary-deep); color: #fff; font-size: 21rpx; font-weight: 700; box-shadow: 0 6rpx 16rpx rgba(255, 107, 91, .32); transition: transform .15s ease; }
.home-board__cook:active { transform: scale(.95); }
.home-board__cook-arrow { font-size: 26rpx; line-height: 1; }
.home-board__media { position: relative; display: flex; margin: 12rpx; width: 248rpx; flex: 0 0 248rpx; border-radius: 22rpx; overflow: hidden; box-shadow: 0 6rpx 18rpx rgba(96, 52, 28, .12); }
.home-board__img { width: 100%; height: 100%; }
.home-board__img--fallback { padding: 34rpx; box-sizing: border-box; background: #FFFFFF; }
/* 朱红印章：竖排「今日」，微旋转的盖章感 */
.home-board__stamp { position: absolute; top: 12rpx; left: 12rpx; display: flex; flex-direction: column; align-items: center; padding: 10rpx 9rpx 12rpx; border-radius: 10rpx; background: linear-gradient(160deg, #FF7A5C 0%, #E14E34 100%); color: #FFFFFF; font-size: 23rpx; font-weight: 800; line-height: 1.18; letter-spacing: 0; box-shadow: 0 4rpx 12rpx rgba(180, 52, 28, .38); transform: rotate(-5deg); }
.home-board__stamp text { display: block; }
/* P1 收藏引导 */ .fav-tip { position: relative; margin: 16rpx 0 0; padding: 0 24rpx; } .fav-tip__arrow { position: absolute; top: -10rpx; right: 80rpx; width: 0; height: 0; border-left: 12rpx solid transparent; border-right: 12rpx solid transparent; border-bottom: 12rpx solid #FFF7E6; } .fav-tip__body { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 20rpx; border-radius: 20rpx; background: #FFF7E6; border: 2rpx solid #FFE4B5; box-shadow: 0 4rpx 12rpx rgba(255,180,80,0.15); } .fav-tip__emoji { font-size: 36rpx; } .fav-tip__text { flex: 1; display: flex; flex-direction: column; gap: 4rpx; min-width: 0; } .fav-tip__title { font-size: 26rpx; font-weight: 700; color: #8B5A00; } .fav-tip__sub { font-size: 22rpx; color: #B07930; line-height: 1.4; } .fav-tip__close { flex-shrink: 0; padding: 8rpx 18rpx; border-radius: 999rpx; background: #FFB98C; color: #fff; font-size: 22rpx; font-weight: 600; } /* 组件墙：4 列网格，入口分层——三餐主卡(2×2 HOT) / 抽奖小卡(1×1) / 双宽卡(2×1) */
.home-bento { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); grid-auto-rows: 158rpx; gap: 14rpx; margin: 24rpx 0 32rpx; }
.home-bento__card { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; overflow: hidden; min-width: 0; border: 2rpx solid var(--mrc-border-light); border-radius: 28rpx; box-shadow: var(--mrc-shadow-soft), var(--mrc-gloss); box-sizing: border-box; transition: transform .15s ease; }
.home-bento__card:active { transform: scale(.97); }
.home-bento__hot { position: absolute; right: 12rpx; top: 12rpx; z-index: 2; padding: 3rpx 12rpx; border-radius: 999rpx; background: linear-gradient(135deg, #FFB98C, #EF5A3C); color: #fff; font-size: 17rpx; font-weight: 800; letter-spacing: 1rpx; box-shadow: 0 4rpx 10rpx rgba(239, 90, 60, 0.35); }
.home-bento__card--guanfan { grid-column: span 2; grid-row: span 2; align-items: flex-start; justify-content: flex-start; gap: 9rpx; padding: 26rpx 28rpx; border-color: transparent; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }
.home-bento__card--guanfan .home-bento__label { margin-top: 14rpx; color: rgba(255, 255, 255, 0.82); font-size: 20rpx; }
.home-bento__card--guanfan .home-bento__title { color: #fff; font-size: 40rpx; font-weight: 800; letter-spacing: 1rpx; }
.home-bento__card--guanfan .home-bento__hint { color: rgba(255, 255, 255, 0.9); font-size: 20rpx; }
.home-bento__hero-img { position: absolute; right: 0; bottom: 0; width: 172rpx; height: 172rpx; filter: drop-shadow(0 8rpx 14rpx rgba(120, 40, 20, 0.22)); }
.home-bento__card--wheel { background: var(--mrc-surface); }
.home-bento__card--nine { background: var(--mrc-surface); }
.home-bento__card--flip { background: var(--mrc-surface); }
.home-bento__card--agent { border-color: transparent; background: linear-gradient(135deg, var(--mrc-surface-mint), var(--mrc-surface)); }
.home-bento__img { width: 78rpx; height: 78rpx; filter: drop-shadow(0 4rpx 8rpx rgba(96, 52, 28, 0.10)); }
.home-bento__name { color: var(--mrc-text-deep); font-size: 24rpx; font-weight: 800; }
.home-bento__sub { color: var(--mrc-text-sub); font-size: 18rpx; }
.home-bento__hint { color: var(--mrc-text-sub); font-size: 18rpx; line-height: 1.3; }
.home-bento__card--lucky { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 22rpx 0 26rpx; border-color: transparent; background: var(--mrc-primary-grad); box-shadow: var(--mrc-shadow-coral), var(--mrc-gloss); }
.home-bento__lucky-copy { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 6rpx; }
.home-bento__lucky-main { color: #fff; font-size: 27rpx; font-weight: 800; }
.home-bento__lucky-sub { color: rgba(255, 255, 255, 0.85); font-size: 18rpx; }
.home-bento__lucky-img { width: 88rpx; height: 88rpx; flex-shrink: 0; margin-right: 2rpx; filter: drop-shadow(0 4rpx 8rpx rgba(120, 40, 20, 0.18)); }
.home-bento__lucky-arrow { flex-shrink: 0; color: rgba(255, 255, 255, 0.95); font-size: 44rpx; font-weight: 300; }
.home-bento__card--record { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 20rpx 0 26rpx; background: var(--mrc-surface-mint); }
.home-bento__card--library { grid-column: span 2; flex-direction: row; justify-content: space-between; padding: 0 20rpx 0 26rpx; border-color: transparent; background: linear-gradient(135deg, var(--mrc-surface-sun), var(--mrc-surface-peach)); }
/* 与右侧今日三餐宽卡对齐标题字号字重（通用 name 24rpx 是给小卡用的） */
.home-bento__card--library .home-bento__name { font-size: 27rpx; }
.home-bento__record-copy { display: flex; min-width: 0; flex-direction: column; align-items: flex-start; gap: 6rpx; }
.home-bento__record-img { width: 92rpx; height: 92rpx; flex-shrink: 0; filter: drop-shadow(0 4rpx 8rpx rgba(96, 52, 28, 0.10)); }

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
