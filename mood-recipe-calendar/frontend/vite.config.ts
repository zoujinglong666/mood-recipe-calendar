import Uni from '@uni-helper/plugin-uni'
import { isMpWeixin } from '@uni-helper/uni-env'
import UniHelperComponents from '@uni-helper/vite-plugin-uni-components'
import UniHelperLayouts from '@uni-helper/vite-plugin-uni-layouts'
import UniHelperManifest from '@uni-helper/vite-plugin-uni-manifest'
import UniHelperPages from '@uni-helper/vite-plugin-uni-pages'
import Optimization from '@uni-ku/bundle-optimizer'
import { UniEchartsResolver } from 'uni-echarts/resolver'
import { UniEcharts } from 'uni-echarts/vite'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { defineConfig } from 'vite'
import { WotResolver } from './src/resolver'
// https://vitejs.dev/config/
export default defineConfig({
  base: './',
  optimizeDeps: {
    exclude: ['@wot-ui/ui', 'uni-echarts'],
  },
  plugins: [
    // https://github.com/uni-helper/vite-plugin-uni-manifest
    UniHelperManifest(),
    // https://github.com/uni-helper/vite-plugin-uni-pages
    UniHelperPages({
      dts: 'src/uni-pages.d.ts',
      exclude: ['**/components/**/*.*'],
      // 分包：把次要页面移出主包，规避微信主包 2MB 体积上限（P1-2，2026-10-26 扩展）。
      // 此前只分出 8 个轻页面（gallery/album/report/timeline/feedback/about/privacy/almanac），
      // 主包仍 1.34MB；现按域扩展为三个分包：内容 / 规划 / 设置。
      // 注意：tabBar 页面与核心转化页（recipe）、记录链路（record）必须留在主包；
      // 相关跳转路径已从 /pages/X 同步改为 /subpkg-*/X（含后端订阅消息深链）。
      subPackages: [
        'src/subpkg-content',
        'src/subpkg-planning',
        'src/subpkg-settings',
        'src/pages/about',
        'src/pages/privacy',
      ],
    }),
    // https://github.com/uni-helper/vite-plugin-uni-layouts
    UniHelperLayouts(),
    // https://github.com/uni-helper/vite-plugin-uni-components
    UniHelperComponents({
      resolvers: [WotResolver(), UniEchartsResolver()],
      dts: 'src/components.d.ts',
      dirs: ['src/components', 'src/business'],
      directoryAsNamespace: true,
    }),
    // https://uni-echarts.xiaohe.ink
    UniEcharts(),
    // https://uni-helper.cn/plugin-uni
    Uni(),
    // https://github.com/uni-ku/bundle-optimizer
    Optimization({
      enable: isMpWeixin,
      logger: false,
    }),
    // https://github.com/antfu/unplugin-auto-import
    AutoImport({
      imports: ['vue', '@vueuse/core', 'pinia', 'uni-app', {
        from: '@wot-ui/router',
        imports: ['createRouter', 'useRouter', 'useRoute'],
      }, {
        from: '@wot-ui/ui',
        imports: ['useToast', 'useDialog', 'useNotify', 'CommonUtil'],
      }],
      dts: 'src/auto-imports.d.ts',
      dirs: ['src/composables', 'src/stores', 'src/utils', 'src/api'],
      vueTemplate: true,
    }),
    // https://github.com/antfu/unocss
    // see unocss.config.ts for config
    UnoCSS(),
  ],
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['legacy-js-api'],
      },
    },
  },
})
