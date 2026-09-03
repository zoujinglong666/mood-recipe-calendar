<script lang="ts" setup>
const router = useRouter()

const route = useRoute()

const { activeTabbar, getTabbarItemValue, setTabbarItemActive, tabbarList } = useTabbar()

function handleTabbarChange({ value }: { value: string }) {
  setTabbarItemActive(value)
  router.pushTab({ name: value })
}

onMounted(() => {
  // #ifdef APP
  uni.hideTabBar()
  // #endif
  nextTick(() => {
    if (route.name && route.name !== activeTabbar.value.name) {
      setTabbarItemActive(route.name)
    }
  })
})
</script>

<script lang="ts">
export default {
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <slot />
  <wd-gap safe-area-bottom height="var(--wot-tabbar-height, 50px)" />
  <wd-tabbar
    :model-value="activeTabbar.name"
    safe-area-inset-bottom
    fixed
    class="mrc-tabbar"
    @change="handleTabbarChange"
  >
    <wd-tabbar-item
      v-for="(item, index) in tabbarList"
      :key="index"
      :name="item.name"
      :value="getTabbarItemValue(item.name)"
      :title="item.title"
    >
      <template #icon="{ active }">
        <image
          :src="active ? item.activeIcon : item.inactiveIcon"
          class="mrc-tabbar__icon"
          :class="{ 'mrc-tabbar__icon--active': active, 'mrc-tabbar__icon--inactive': !active }"
          mode="aspectFit"
        />
      </template>
    </wd-tabbar-item>
  </wd-tabbar>
</template>

<style lang="scss">
.mrc-tabbar {
  --wot-tabbar-bg-color: var(--mrc-white);
  --wot-tabbar-title-color: var(--mrc-text-sub);
  --wot-tabbar-title-active-color: var(--mrc-accent);
  --wot-tabbar-border-color: var(--mrc-border);
}
.mrc-tabbar__icon {
  width: 48rpx;
  height: 48rpx;
  transition: transform 0.2s ease, filter 0.2s ease;
}
.mrc-tabbar__icon--active {
  filter: none;
  transform: scale(1.1);
}
.mrc-tabbar__icon--inactive {
  filter: grayscale(0.6) opacity(0.6);
  transform: scale(0.9);
}
</style>
