/**
 * 增长钩子静态数据：干饭人格 / 默契测试。
 *
 * 全部为前端静态内容，无后端依赖；文案保持锅仔 IP 口吻
 * （亲切、口语、贪吃但不油腻，与转盘的「锅仔说话算数」一脉相承）。
 *
 * 诚实原则：人格结果由真实答案计分（argmax）算出，默契度由两人答案逐题比对算出——绝不假随机。
 */
/* ================= 干饭人格（12 题 × 4 选项 · AI 语义判定 · 16 型） ================= */

/** 四个维度：味觉灵魂 / 干饭场面 / 烹饪姿态 / 点菜哲学——每维 3 题，选项前两项 A 极、后两项 B 极 */
export type DimensionKey = 'TASTE' | 'SCENE' | 'KITCHEN' | 'ORDER' | 'MISE' | 'FLAVOR' | 'TIDY' | 'PURPOSE'

export interface QuizOption {
  label: string
  emoji: string
}

export interface QuizQuestion {
  emoji: string
  text: string
  dim: DimensionKey
  /** 锅仔针对这道题的定制吐槽（答题页气泡）；缺省时页面回退通用台词池 */
  quip?: string
  /** 恒为 4 项：[A 极, A 极变体, B 极, B 极变体] */
  options: QuizOption[]
}

export interface DimensionPole {
  letter: string
  label: string
  line: string
}

export interface Dimension {
  key: DimensionKey
  name: string
  poles: [DimensionPole, DimensionPole]
}

export const DIMENSIONS: Dimension[] = [
  {
    key: 'TASTE',
    name: '味觉灵魂',
    poles: [
      { letter: 'X', label: '猎奇派', line: '你的舌头永远朝着没吃过的方向伸' },
      { letter: 'C', label: '恋旧派', line: '你的胃里有一张写满旧味道的地图' },
    ],
  },
  {
    key: 'SCENE',
    name: '干饭场面',
    poles: [
      { letter: 'R', label: '热闹局', line: '人多筷子才够快，抢菜是干饭的灵魂' },
      { letter: 'S', label: '独食家', line: '一个人的餐桌，是世界上最诚实的幸福' },
    ],
  },
  {
    key: 'KITCHEN',
    name: '烹饪姿态',
    poles: [
      { letter: 'F', label: '灶台派', line: '锅气是你的勋章，灶台是你的主场' },
      { letter: 'W', label: '外卖派', line: '会点外卖，也是一种了不起的生活能力' },
    ],
  },
  {
    key: 'ORDER',
    name: '点菜哲学',
    poles: [
      { letter: 'P', label: '讲究派', line: '你的点菜清单，比论文还严谨' },
      { letter: 'Y', label: '随缘派', line: '随缘点菜，缘到了都是好菜' },
    ],
  },
]

export const QUIZ: QuizQuestion[] = [
  {
    emoji: '', text: '楼下新开一家店，和常去的那家只能选一个？', dim: 'TASTE',
    quip: '新店旧店打架，锅仔搬小板凳看',
    options: [
      { label: '排队也要试新店', emoji: '' },
      { label: '先看探店笔记再决定', emoji: '' },
      { label: '毫不犹豫老地方', emoji: '' },
      { label: '等风评稳定再说', emoji: '' },
    ],
  },
  {
    emoji: '', text: '打开菜单第一眼，先找什么？', dim: 'TASTE',
    quip: '锅仔点菜全靠鼻子，你靠什么？',
    options: [
      { label: '没吃过的菜名', emoji: '' },
      { label: '今日限定与创新档', emoji: '' },
      { label: '闭眼点的老三样', emoji: '' },
      { label: '人手一份的招牌菜', emoji: '' },
    ],
  },
  {
    emoji: '', text: '刷到一道从没见过的做法，你的手是？', dim: 'TASTE',
    quip: '收藏夹吃灰大赛，锅仔也是选手',
    options: [
      { label: '已经收藏三遍了', emoji: '' },
      { label: '立刻研究食材替代', emoji: '' },
      { label: '转发给会做的人', emoji: '' },
      { label: '看看就好，还是那几样香', emoji: '' },
    ],
  },
  {
    emoji: '', text: '周五晚上理想中的一顿是？', dim: 'SCENE',
    quip: '周五的胃，值得被认真对待',
    options: [
      { label: '一桌人抢菜才香', emoji: '' },
      { label: '组局涮火锅', emoji: '' },
      { label: '一个人吃到扶墙', emoji: '' },
      { label: '小锅配剧谁也别理我', emoji: '' },
    ],
  },
  {
    emoji: '', text: '对火锅，你的真实态度是？', dim: 'SCENE',
    quip: '锅仔涮什么都好吃，主要是氛围',
    options: [
      { label: '人多才涮得开', emoji: '' },
      { label: '鸳鸯锅也要坐大桌', emoji: '' },
      { label: '一人小火锅最治愈', emoji: '' },
      { label: '涮什么不重要，别跟我抢', emoji: '' },
    ],
  },
  {
    emoji: '', text: '吃到好吃的第一反应是？', dim: 'SCENE',
    quip: '锅仔的第一反应是再吃一口',
    options: [
      { label: '立刻发给饭搭子馋TA', emoji: '' },
      { label: '拍照配文发一圈', emoji: '' },
      { label: '默默多吃两口就好', emoji: '' },
      { label: '收进自己的私藏清单', emoji: '' },
    ],
  },
  {
    emoji: '', text: '深夜十一点，冰箱里有蛋有面，你？', dim: 'KITCHEN',
    quip: '深夜的锅仔，比你还馋',
    options: [
      { label: '起锅烧油，说做就做', emoji: '' },
      { label: '翻出收藏的菜谱试一手', emoji: '' },
      { label: '外卖软件已经打开了', emoji: '' },
      { label: '泡面加蛋也很幸福', emoji: '' },
    ],
  },
  {
    emoji: '', text: '刷到一款新厨具，你会？', dim: 'KITCHEN',
    quip: '锅仔家最不缺的就是锅',
    options: [
      { label: '手痒，感觉总能用上', emoji: '' },
      { label: '已经在比价了', emoji: '' },
      { label: '看看就好，锅还够用', emoji: '' },
      { label: '外卖都吃不过来还做什么饭', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做菜大翻车之后，你？', dim: 'KITCHEN',
    quip: '锅仔翻车时会先把锅藏起来',
    options: [
      { label: '洗锅，下次再来', emoji: '' },
      { label: '认真复盘哪一步错了', emoji: '' },
      { label: '默默点了个外卖', emoji: '' },
      { label: '这厨子我是当不了了', emoji: '' },
    ],
  },
  {
    emoji: '', text: '点奶茶的时候，糖度冰量？', dim: 'ORDER',
    quip: '锅仔全糖去冰，别学我',
    options: [
      { label: '固定配方，永不更改', emoji: '' },
      { label: '按当天状态精确计算', emoji: '' },
      { label: '看心情换着来', emoji: '' },
      { label: '店员随便，都行', emoji: '' },
    ],
  },
  {
    emoji: '', text: '一顿饭的荤素搭配，你有讲究吗？', dim: 'ORDER',
    quip: '锅仔的搭配是：肉，和更多的肉',
    options: [
      { label: '营养配比必须安排', emoji: '' },
      { label: '主食蛋白蔬菜缺一不可', emoji: '' },
      { label: '好吃就行，管那么多', emoji: '' },
      { label: '想吃什么什么有理', emoji: '' },
    ],
  },
  {
    emoji: '', text: '被一家店伤过心之后，你？', dim: 'ORDER',
    quip: '伤过心的胃，要靠好菜哄回来',
    options: [
      { label: '记进小本本永不回头', emoji: '' },
      { label: '差评写满三百字', emoji: '' },
      { label: '过阵子说不定就好了', emoji: '' },
      { label: '下次换道菜也许就惊艳', emoji: '' },
    ],
  },
]

export interface DimensionScore {
  key: DimensionKey
  name: string
  /** 百分比：0 = 全 A 极，100 = 全 B 极 */
  percent: number
  letter: string
  left: DimensionPole
  right: DimensionPole
}

export interface PersonalityType {
  code: string
  name: string
  emoji: string
  tagline: string
  /** 本地兜底点评（AI 判定成功时由服务端语义点评替代） */
  line: string
}

/** 16 型人格档案：4 字母代码 → 称号与兜底点评 */
export const PERSONALITY_TYPES: Record<string, PersonalityType> = {
  XRFP: { code: 'XRFP', name: '探味灶主', emoji: '', tagline: '菜单上没见过的，都要亲手试一遍', line: '锅仔最服你这种：新菜谱敢试、食材敢搭，讲究起来连摆盘都不糊弄。' },
  XRFY: { code: 'XRFY', name: '猎奇快炒侠', emoji: '', tagline: '想到什么炒什么，锅气就是惊喜', line: '你的厨房没有固定菜单，全靠灵感大火收汁——翻车率与惊喜率齐飞。' },
  XRWP: { code: 'XRWP', name: '探店研究员', emoji: '', tagline: '没吃过的店，先做功课再进门', line: '你点菜像做课题：查评分、看笔记、列清单，讲究得让老板发慌。' },
  XRWY: { code: 'XRWY', name: '百味漂流家', emoji: '', tagline: '外卖红点全点亮，全城都是你的菜单', line: '全城的没吃过的店都是你的地图，随缘下单却总能捞到宝藏。' },
  XSFP: { code: 'XSFP', name: '独食私厨', emoji: '', tagline: '一个人的饭，做起来最讲究', line: '你的小锅里有大学问：一人份也要荤素到位，锅仔敬你是认真的。' },
  XSFY: { code: 'XSFY', name: '深夜实验厨', emoji: '', tagline: '深夜的厨房，是猎奇者的实验室', line: '夜越深你越敢——剩菜加个蛋都能被你玩出新菜名。' },
  XSWP: { code: 'XSWP', name: '独行美食家', emoji: '', tagline: '一个人也要吃得清清楚楚', line: '一个人点餐从不将就：想吃的、没吃过的，一样都不落下。' },
  XSWY: { code: 'XSWY', name: '随缘尝鲜客', emoji: '', tagline: '闭眼点单，开盲盒式干饭', line: '你的外卖软件像个盲盒机，锅仔建议搭配健胃消食片食用。' },
  CRFP: { code: 'CRFP', name: '老味道主理人', emoji: '', tagline: '家常菜的天花板，由你亲手撑起', line: '你把熟悉的味道做到极致，讲究到火候——锅仔想拜你为师。' },
  CRFY: { code: 'CRFY', name: '家传快手王', emoji: '', tagline: '老三样在手，热闹桌上不慌', line: '你的拿手菜在饭桌上永远最先光盘，管它讲不讲究，好吃就是道理。' },
  CRWP: { code: 'CRWP', name: '怀旧收藏家', emoji: '', tagline: '老店清单比通讯录还长', line: '你记得每家老店的招牌，讲究到连老板都以为你是来盘店的。' },
  CRWY: { code: 'CRWY', name: '温柔守味人', emoji: '', tagline: '熟悉的滋味，永远值得再点一次', line: '点单从不纠结，旧味道永远第一顺位——锅仔觉得这叫长情。' },
  CSFP: { code: 'CSFP', name: '一人食大厨', emoji: '', tagline: '一个人的锅，煮最认真的饭', line: '你的单人餐桌就是米其林：量刚刚好，摆盘不含糊。' },
  CSFY: { code: 'CSFY', name: '深夜泡面禅', emoji: '', tagline: '泡面加蛋，就是我的满汉全席', line: '深夜的一碗面被你吃出了仪式感，加不加肠全看当天修行。' },
  CSWP: { code: 'CSWP', name: '独食治愈家', emoji: '', tagline: '点外卖也要给自己配好青菜', line: '一个人点单也有完整搭配，锅仔发现你是隐藏的营养学家。' },
  CSWY: { code: 'CSWY', name: '躺平干饭仙', emoji: '', tagline: '能躺着吃到，绝不起来做饭', line: '你把「随便」点出了境界——锅仔负责在你饿之前把菜想好。' },
}

export interface PersonalityResult {
  code: string
  type: PersonalityType
  /** 四个维度的倾向：percent 越大越靠 B 极 */
  dimensions: DimensionScore[]
  /** 幸运菜心情标签：从判定维度推导，接菜谱库出口 */
  luckyMood: string
  /** 本地兜底长文案（AI 判定成功时被服务端语义点评替代） */
  reading: string
}

/* ================= 做饭人格（cook） ================= */

export interface DimensionCook {
  key: DimensionKey
  name: string
  poles: [DimensionPole, DimensionPole]
}

export const DIMENSIONS_COOK: DimensionCook[] = [
  {
    key: 'MISE',
    name: '备菜哲学',
    poles: [
      { letter: 'P', label: '计划派', line: '你的战斗在开火前就打赢了' },
      { letter: 'I', label: '即兴派', line: '你的灵感只在开火后才上线' },
    ],
  },
  {
    key: 'FLAVOR',
    name: '口味掌控',
    poles: [
      { letter: 'R', label: '照谱派', line: '菜谱是你的合同，一条不改' },
      { letter: 'B', label: '乱改派', line: '菜谱只是起点，锅才是你的画布' },
    ],
  },
  {
    key: 'TIDY',
    name: '厨房秩序',
    poles: [
      { letter: 'T', label: '洁癖派', line: '你的灶台干净得像没人做过饭' },
      { letter: 'W', label: '战场派', line: '你的战场越乱，菜越香' },
    ],
  },
  {
    key: 'PURPOSE',
    name: '做饭初心',
    poles: [
      { letter: 'G', label: '炫技派', line: '掌声是你最好的调味料' },
      { letter: 'H', label: '治愈派', line: '你的菜，是做给自己的拥抱' },
    ],
  },
]

export const QUIZ_COOK: QuizQuestion[] = [
  {
    emoji: '', text: '明天要请朋友来家里吃饭，今晚的你？', dim: 'MISE',
    quip: '请客的菜，锅仔都替你紧张',
    options: [
      { label: '列好菜单和采购单', emoji: '' },
      { label: '食材提前洗切分装', emoji: '' },
      { label: '明天醒来再说', emoji: '' },
      { label: '想到什么买什么', emoji: '' },
    ],
  },
  {
    emoji: '', text: '你家冰箱里的食材，通常是？', dim: 'MISE',
    quip: '锅仔的冰箱里住着半成品',
    options: [
      { label: '按菜单采购刚好够', emoji: '' },
      { label: '分类收纳像仓库', emoji: '' },
      { label: '有啥用啥', emoji: '' },
      { label: '经常翻出过期的', emoji: '' },
    ],
  },
  {
    emoji: '', text: '开火前的灶台，是什么状态？', dim: 'MISE',
    quip: '灶台越整齐，锅仔越想蹭饭',
    options: [
      { label: '食材调料一字排开', emoji: '' },
      { label: '按步骤预处理好', emoji: '' },
      { label: '边做边翻找', emoji: '' },
      { label: '锅热了才想起解冻', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做一道没做过的新菜，你会？', dim: 'FLAVOR',
    quip: '菜谱和锅仔，你至少得听一个',
    options: [
      { label: '精确到克照谱执行', emoji: '' },
      { label: '先把视频完整看三遍', emoji: '' },
      { label: '谱只是参考，手比谱快', emoji: '' },
      { label: '凭感觉放调料', emoji: '' },
    ],
  },
  {
    emoji: '', text: '菜谱里写的用量，你？', dim: 'FLAVOR',
    quip: '「适量」，是厨艺的谜语',
    options: [
      { label: '电子秤伺候', emoji: '' },
      { label: '勺子量杯安排上', emoji: '' },
      { label: '适量就是我的量', emoji: '' },
      { label: '抓一把就是一勺', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做到一半发现缺一味料，你？', dim: 'FLAVOR',
    quip: '缺的那味，往往是锅仔的私藏',
    options: [
      { label: '出门也要买齐', emoji: '' },
      { label: '找替代方案严格对冲', emoji: '' },
      { label: '少就少吧不影响', emoji: '' },
      { label: '加点别的也许更香', emoji: '' },
    ],
  },
  {
    emoji: '', text: '一道菜出锅之后，灶台？', dim: 'TIDY',
    quip: '锅仔擦锅的时候最温柔',
    options: [
      { label: '锅净台净马上擦', emoji: '' },
      { label: '边角料即时归位', emoji: '' },
      { label: '做完三道一起收拾', emoji: '' },
      { label: '战况惨烈但很快乐', emoji: '' },
    ],
  },
  {
    emoji: '', text: '切菜产生的垃圾，你？', dim: 'TIDY',
    quip: '垃圾桶的位置决定厨房的命运',
    options: [
      { label: '顺手进垃圾桶', emoji: '' },
      { label: '台面永远只有一块砧板的活', emoji: '' },
      { label: '堆着最后一起处理', emoji: '' },
      { label: '在哪切就在哪放', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做饭时你家水槽的状态？', dim: 'TIDY',
    quip: '水槽的状态，就是厨师的内心戏',
    options: [
      { label: '基本干净随用随洗', emoji: '' },
      { label: '碗筷即时清洗', emoji: '' },
      { label: '泡着等着', emoji: '' },
      { label: '像刚打过仗', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做了一桌好菜，你最想？', dim: 'PURPOSE',
    quip: '锅仔拍照永远在菜凉之前',
    options: [
      { label: '发朋友圈等赞', emoji: '' },
      { label: '拍视频记录下来', emoji: '' },
      { label: '自己先吃为敬', emoji: '' },
      { label: '谁来都不给看', emoji: '' },
    ],
  },
  {
    emoji: '', text: '做饭时你最享受的时刻？', dim: 'PURPOSE',
    quip: '锅仔最爱听的就是那声「哇」',
    options: [
      { label: '别人夹第一筷子时的惊叹', emoji: '' },
      { label: '上桌那一刻的掌声', emoji: '' },
      { label: '独自试味的瞬间', emoji: '' },
      { label: '厨房里一个人的安静', emoji: '' },
    ],
  },
  {
    emoji: '', text: '累了一天的晚上，还开火吗？', dim: 'PURPOSE',
    quip: '累的时候，锅仔替你看着火',
    options: [
      { label: '做道硬菜发出来犒劳自己', emoji: '' },
      { label: '精心摆盘拍张照', emoji: '' },
      { label: '煮碗面条暖暖的', emoji: '' },
      { label: '随便吃点，舒服就行', emoji: '' },
    ],
  },
]

export const PERSONALITY_TYPES_COOK: Record<string, PersonalityType> = {
  PRTG: { code: 'PRTG', name: '厨房参谋长', emoji: '', tagline: '一场家宴，一次作战', line: '菜单、采购、动线全在你的计划里，锅仔想给你发工资。' },
  PRTH: { code: 'PRTH', name: '家宴总指挥', emoji: '', tagline: '菜单采购摆盘，三天前就绪', line: '你的家宴从来没有意外——只有按剧本上演的美味。' },
  PRBG: { code: 'PRBG', name: '菜谱卫道士', emoji: '', tagline: '克普克量，一克不差', line: '你守护的不只是菜谱，是这道菜的尊严。' },
  PRBH: { code: 'PRBH', name: '温柔执行者', emoji: '', tagline: '照谱慢煮，煮给自己', line: '一页菜谱一口小锅，你把日子煮得有据可依。' },
  PRWG: { code: 'PRWG', name: '豪放主帅', emoji: '', tagline: '计划归计划，出锅照样浪', line: '备菜一丝不苟，出手大开大合——反差就是你的魅力。' },
  PRWH: { code: 'PRWH', name: '烟火战术家', emoji: '', tagline: '流程在手，战场放飞', line: '计划让你安心，混乱让你兴奋，你的厨房两样都有。' },
  IRTG: { code: 'IRTG', name: '临场抄谱员', emoji: '', tagline: '灵感来了，现学现卖', line: '你相信临场发挥也尊重前人智慧，稳中带皮。' },
  IRTH: { code: 'IRTH', name: '安分灵感家', emoji: '', tagline: '小小发挥，不越雷池', line: '你的即兴都长在菜谱的延长线上——聪明的偷懒。' },
  IRBG: { code: 'IRBG', name: '即兴改造王', emoji: '', tagline: '上锅三分钟，菜谱全推翻', line: '菜谱到你手里只是草稿，成品反而常常惊艳。' },
  IRBH: { code: 'IRBH', name: '随手煮意人', emoji: '', tagline: '灵感到哪，饭到哪', line: '你不追求完美，只追求那一口刚好对的此刻。' },
  IRWG: { code: 'IRWG', name: '野路子主厨', emoji: '', tagline: '没有菜谱能活着离开我的锅', line: '你的厨房没有规则，但有奇迹。' },
  IRWH: { code: 'IRWH', name: '战场艺术家', emoji: '', tagline: '锅气是我的画布', line: '越乱越有灵感，你的厨房是一幅动态画。' },
  PBTG: { code: 'PBTG', name: '谋定后浪', emoji: '', tagline: '看似随手，心中有谱', line: '你以为在随缘，其实手里的菜谱早已背熟。' },
  PBTH: { code: 'PBTH', name: '慢炖哲学家', emoji: '', tagline: '慢一点，味道就深一点', line: '你有计划但不着急——时间才是你最好的调料。' },
  IBWG: { code: 'IBWG', name: '自由风暴厨', emoji: '', tagline: '锅铲一响，规矩全忘', line: '你的厨房是风暴中心，也是奇迹现场。' },
  IBWH: { code: 'IBWH', name: '躺平烹饪仙', emoji: '', tagline: '煮熟就是胜利', line: '你对做饭的要求只有两个字：能吃。锅仔替你多操心。' },
}

/* ================= 测试注册表：干饭（eat）/ 做饭（cook） ================= */

export type TestKey = 'eat' | 'cook'

export interface TestConfig {
  quiz: QuizQuestion[]
  dims: { key: DimensionKey, name: string, poles: [DimensionPole, DimensionPole] }[]
  types: Record<string, PersonalityType>
  /** 幸运菜心情取自该维度（下标）的胜极字母 */
  luckyDimIndex: number
  luckyMoods: Record<string, string>
}

export const TESTS: Record<TestKey, TestConfig> = {
  eat: {
    quiz: QUIZ, dims: DIMENSIONS, types: PERSONALITY_TYPES,
    luckyDimIndex: 0, luckyMoods: { X: '期待', C: '想家' },
  },
  cook: {
    quiz: QUIZ_COOK, dims: DIMENSIONS_COOK, types: PERSONALITY_TYPES_COOK,
    luckyDimIndex: 3, luckyMoods: { G: '得意', H: '满足' },
  },
}

/**
 * 本地确定性计分（AI 判定的兜底，支持 eat/cook 双测试）：每题前两项 = A 极、后两项 = B 极，
 * 每维 3 题计多数极——无平局，结果完全由答案决定。线上优先走 AI 语义判定。
 */
export function scorePersonality(answers: number[], test: TestKey = 'eat'): PersonalityResult {
  const cfg = TESTS[test]
  const bCounts: Record<string, number> = {}
  cfg.dims.forEach((d) => { bCounts[d.key] = 0 })
  answers.forEach((opt, qi) => {
    const q = cfg.quiz[qi]
    if (!q || opt < 0 || opt > 3) return
    if (opt >= 2) bCounts[q.dim] += 1
  })

  const dimensions: DimensionScore[] = cfg.dims.map((dim) => {
    const b = bCounts[dim.key] || 0
    const percent = Math.round((b / 3) * 100)
    const winner = b >= 2 ? dim.poles[1] : dim.poles[0]
    return {
      key: dim.key,
      name: dim.name,
      percent,
      letter: winner.letter,
      left: dim.poles[0],
      right: dim.poles[1],
    }
  })

  const code = dimensions.map(d => d.letter).join('')
  const type = cfg.types[code] || Object.values(cfg.types)[0]!
  const reading = dimensions
    .map((d, i) => d.letter === cfg.dims[i]!.poles[1].letter ? cfg.dims[i]!.poles[1].line : cfg.dims[i]!.poles[0].line)
    .join('') + type.line
  const luckyDim = dimensions[cfg.luckyDimIndex]!

  return {
    code,
    type,
    dimensions,
    luckyMood: cfg.luckyMoods[luckyDim.letter] || '满足',
    reading,
  }
}

/* 答案编码：12 题各 0-3，base-4 一位一字，拼成 12 位串塞进分享链接，零后端传答案 */

export function encodeAnswers(answers: number[]): string {
  return answers.map(a => (a >= 0 && a <= 3 ? a.toString(4) : '0')).join('')
}

export function decodeAnswers(raw: string | undefined): number[] | null {
  if (!raw || !/^[0-3]{12}$/.test(raw))
    return null
  return raw.split('').map(ch => Number.parseInt(ch, 4))
}

/* ================= 默契度 ================= */

export interface CompatItem {
  q: string
  host: string
  guest: string
  level: 'exact' | 'diff'
}

export interface CompatResult {
  score: number
  sameCount: number
  total: number
  tier: { name: string, line: string }
  per: CompatItem[]
}

/** 默契计分：12 题逐题比对，同侧 +8 分（满分 96），另送开场 4 分保底体面 */
export function compatOf(host: number[], guest: number[]): CompatResult {
  const per: CompatItem[] = host.map((h, i) => {
    const ho = QUIZ[i]?.options[h]
    const go = QUIZ[i]?.options[guest[i]]
    const same = h === guest[i]
    return {
      q: QUIZ[i]?.text || '',
      host: ho?.label || '',
      guest: go?.label || '',
      level: same ? 'exact' : 'diff',
    }
  })
  const sameCount = per.filter(p => p.level === 'exact').length
  const score = Math.min(100, sameCount * 8 + 4)
  return { score, sameCount, total: per.length, tier: compatTier(score), per }
}

function compatTier(score: number): { name: string, line: string } {
  if (score >= 90)
    return { name: '灵魂饭搭子', line: '一个锅都不用分——点菜从来不需要商量' }
  if (score >= 70)
    return { name: '桌上好搭子', line: '口味大同小异，剩下的分歧留给甜咸豆腐脑' }
  if (score >= 40)
    return { name: '一桌两派', line: '能坐一桌，但各点各的——刚好互相尝一口' }
  return { name: '各吃各的', line: '你吃你的山珍，我炫我的海味——互相尊重，饭照吃' }
}
