export interface TabbarItem {
  name: string
  value?: number
  active: boolean
  title: string
  icon: string
  activeIcon: string
  inactiveIcon: string
}

const tabbarItems = ref<TabbarItem[]>([
  {
    name: 'home',
    active: true,
    title: '首页',
    icon: 'home',
    activeIcon: '/static/guozai/action_01_bowl.png',
    inactiveIcon: '/static/guozai/action_01_bowl.png',
  },
  {
    name: 'record',
    active: false,
    title: '记录',
    icon: 'camera',
    activeIcon: '/static/guozai/action_03_camera.png',
    inactiveIcon: '/static/guozai/action_03_camera.png',
  },
  {
    name: 'profile',
    active: false,
    title: '我的',
    icon: 'user',
    activeIcon: '/static/guozai/action_06_glasses.png',
    inactiveIcon: '/static/guozai/action_06_glasses.png',
  },
])

export function useTabbar() {
  const tabbarList = computed(() => tabbarItems.value)

  const activeTabbar = computed(() => {
    const item = tabbarItems.value.find(item => item.active)
    return item || tabbarItems.value[0]
  })

  const getTabbarItemValue = (name: string) => {
    const item = tabbarItems.value.find(item => item.name === name)
    return item?.value
  }

  const setTabbarItem = (name: string, value: number) => {
    const tabbarItem = tabbarItems.value.find(item => item.name === name)
    if (tabbarItem) {
      tabbarItem.value = value
    }
  }

  const setTabbarItemActive = (name: string) => {
    tabbarItems.value.forEach((item) => {
      if (item.name === name) {
        item.active = true
      }
      else {
        item.active = false
      }
    })
  }

  return {
    tabbarList,
    activeTabbar,
    getTabbarItemValue,
    setTabbarItem,
    setTabbarItemActive,
  }
}
