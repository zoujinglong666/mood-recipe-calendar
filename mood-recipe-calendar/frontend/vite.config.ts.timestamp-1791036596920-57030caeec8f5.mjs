// vite.config.ts
import Uni from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+plugin-uni@0.1.0_@dcloudio+vite-plugin-uni@3.0.0-4080520251106001_@vueuse+core@11_hhjrojghlwgkdbems7xijfkgsy/node_modules/@uni-helper/plugin-uni/src/index.js";
import { isMpWeixin } from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+uni-env@0.2.0/node_modules/@uni-helper/uni-env/dist/index.js";
import UniHelperComponents from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+vite-plugin-uni-components@0.2.6_rollup@4.53.2/node_modules/@uni-helper/vite-plugin-uni-components/dist/index.mjs";
import UniHelperLayouts from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+vite-plugin-uni-layouts@0.1.11_rollup@4.53.2/node_modules/@uni-helper/vite-plugin-uni-layouts/dist/index.mjs";
import UniHelperManifest from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+vite-plugin-uni-manifest@0.2.12_vite@5.2.8_@types+node@20.19.25_sass@1.99.0_terser@5.31.6_/node_modules/@uni-helper/vite-plugin-uni-manifest/dist/index.mjs";
import UniHelperPages from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+vite-plugin-uni-pages@0.3.23_vite@5.2.8_@types+node@20.19.25_sass@1.99.0_terser@5.31.6_/node_modules/@uni-helper/vite-plugin-uni-pages/dist/index.mjs";
import Optimization from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-ku+bundle-optimizer@2.1.0_@vueuse+core@11.0.3_vue@3.4.38_typescript@5.5.4___chokidar@3.6_id4czrlujqe2vfmmdhz4utufyi/node_modules/@uni-ku/bundle-optimizer/dist/index.mjs";
import { UniEchartsResolver } from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/uni-echarts@2.2.5_echarts@6.0.0_vue@3.4.38_typescript@5.5.4_/node_modules/uni-echarts/dist-resolver/index.mjs";
import { UniEcharts } from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/uni-echarts@2.2.5_echarts@6.0.0_vue@3.4.38_typescript@5.5.4_/node_modules/uni-echarts/dist-vite/index.mjs";
import UnoCSS from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/unocss@66.0.0_postcss@8.5.10_vite@5.2.8_@types+node@20.19.25_sass@1.99.0_terser@5.31.6__vue@3.4.38_typescript@5.5.4_/node_modules/unocss/dist/vite.mjs";
import AutoImport from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/unplugin-auto-import@0.18.2_@vueuse+core@11.0.3_vue@3.4.38_typescript@5.5.4___rollup@4.53.2/node_modules/unplugin-auto-import/dist/vite.js";
import { defineConfig } from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/vite@5.2.8_@types+node@20.19.25_sass@1.99.0_terser@5.31.6/node_modules/vite/dist/node/index.js";

// src/resolver/index.ts
import { kebabCase } from "file:///Users/zou/WebstormProjects/mood-recipe-calendar/mood-recipe-calendar/frontend/node_modules/.pnpm/@uni-helper+vite-plugin-uni-components@0.2.6_rollup@4.53.2/node_modules/@uni-helper/vite-plugin-uni-components/dist/index.mjs";
function WotResolver() {
  return {
    type: "component",
    resolve: (name) => {
      if (name.match(/^Wd[A-Z]/)) {
        const compName = kebabCase(name);
        return {
          name,
          from: `@wot-ui/ui/components/${compName}/${compName}.vue`
        };
      }
    }
  };
}

// vite.config.ts
var vite_config_default = defineConfig({
  base: "./",
  optimizeDeps: {
    exclude: ["@wot-ui/ui", "uni-echarts"]
  },
  plugins: [
    // https://github.com/uni-helper/vite-plugin-uni-manifest
    UniHelperManifest(),
    // https://github.com/uni-helper/vite-plugin-uni-pages
    UniHelperPages({
      dts: "src/uni-pages.d.ts",
      exclude: ["**/components/**/*.*"],
      // 分包：把非 tabBar 页面编译进独立分包，缩小主包体积、加快每个页面启动。
      // tabBar 三页（index/record/profile）及其子页必须留在主包，故不列入。
      subPackages: [
        "src/pages/about",
        "src/pages/album",
        "src/pages/calendar",
        "src/pages/feedback",
        "src/pages/gallery",
        "src/pages/mood",
        "src/pages/preferences",
        "src/pages/privacy",
        "src/pages/recipe",
        "src/pages/report",
        "src/pages/settings",
        "src/pages/timeline",
        "src/pages/cooking",
        "src/pages/daily-meal-plan",
        "src/pages/login",
        "src/pages/meal-agent",
        "src/pages/membership",
        "src/pages/weekly-plan",
        "src/pages/recommendation-history",
        "src/pages/fridge"
      ]
    }),
    // https://github.com/uni-helper/vite-plugin-uni-layouts
    UniHelperLayouts(),
    // https://github.com/uni-helper/vite-plugin-uni-components
    UniHelperComponents({
      resolvers: [WotResolver(), UniEchartsResolver()],
      dts: "src/components.d.ts",
      dirs: ["src/components", "src/business"],
      directoryAsNamespace: true
    }),
    // https://uni-echarts.xiaohe.ink
    UniEcharts(),
    // https://uni-helper.cn/plugin-uni
    Uni(),
    // https://github.com/uni-ku/bundle-optimizer
    Optimization({
      enable: isMpWeixin,
      logger: false
    }),
    // https://github.com/antfu/unplugin-auto-import
    AutoImport({
      imports: ["vue", "@vueuse/core", "pinia", "uni-app", {
        from: "@wot-ui/router",
        imports: ["createRouter", "useRouter", "useRoute"]
      }, {
        from: "@wot-ui/ui",
        imports: ["useToast", "useDialog", "useNotify", "CommonUtil"]
      }],
      dts: "src/auto-imports.d.ts",
      dirs: ["src/composables", "src/store", "src/utils", "src/api"],
      vueTemplate: true
    }),
    // https://github.com/antfu/unocss
    // see unocss.config.ts for config
    UnoCSS()
  ],
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern-compiler",
        silenceDeprecations: ["legacy-js-api"]
      }
    }
  }
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiLCAic3JjL3Jlc29sdmVyL2luZGV4LnRzIl0sCiAgInNvdXJjZXNDb250ZW50IjogWyJjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZGlybmFtZSA9IFwiL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCIvVXNlcnMvem91L1dlYnN0b3JtUHJvamVjdHMvbW9vZC1yZWNpcGUtY2FsZW5kYXIvbW9vZC1yZWNpcGUtY2FsZW5kYXIvZnJvbnRlbmQvdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kL3ZpdGUuY29uZmlnLnRzXCI7aW1wb3J0IFVuaSBmcm9tICdAdW5pLWhlbHBlci9wbHVnaW4tdW5pJ1xuaW1wb3J0IHsgaXNNcFdlaXhpbiB9IGZyb20gJ0B1bmktaGVscGVyL3VuaS1lbnYnXG5pbXBvcnQgVW5pSGVscGVyQ29tcG9uZW50cyBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktY29tcG9uZW50cydcbmltcG9ydCBVbmlIZWxwZXJMYXlvdXRzIGZyb20gJ0B1bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1sYXlvdXRzJ1xuaW1wb3J0IFVuaUhlbHBlck1hbmlmZXN0IGZyb20gJ0B1bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1tYW5pZmVzdCdcbmltcG9ydCBVbmlIZWxwZXJQYWdlcyBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktcGFnZXMnXG5pbXBvcnQgT3B0aW1pemF0aW9uIGZyb20gJ0B1bmkta3UvYnVuZGxlLW9wdGltaXplcidcbmltcG9ydCB7IFVuaUVjaGFydHNSZXNvbHZlciB9IGZyb20gJ3VuaS1lY2hhcnRzL3Jlc29sdmVyJ1xuaW1wb3J0IHsgVW5pRWNoYXJ0cyB9IGZyb20gJ3VuaS1lY2hhcnRzL3ZpdGUnXG5pbXBvcnQgVW5vQ1NTIGZyb20gJ3Vub2Nzcy92aXRlJ1xuaW1wb3J0IEF1dG9JbXBvcnQgZnJvbSAndW5wbHVnaW4tYXV0by1pbXBvcnQvdml0ZSdcbmltcG9ydCB7IGRlZmluZUNvbmZpZyB9IGZyb20gJ3ZpdGUnXG5pbXBvcnQgeyBXb3RSZXNvbHZlciB9IGZyb20gJy4vc3JjL3Jlc29sdmVyJ1xuLy8gaHR0cHM6Ly92aXRlanMuZGV2L2NvbmZpZy9cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyh7XG4gIGJhc2U6ICcuLycsXG4gIG9wdGltaXplRGVwczoge1xuICAgIGV4Y2x1ZGU6IFsnQHdvdC11aS91aScsICd1bmktZWNoYXJ0cyddLFxuICB9LFxuICBwbHVnaW5zOiBbXG4gICAgLy8gaHR0cHM6Ly9naXRodWIuY29tL3VuaS1oZWxwZXIvdml0ZS1wbHVnaW4tdW5pLW1hbmlmZXN0XG4gICAgVW5pSGVscGVyTWFuaWZlc3QoKSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktcGFnZXNcbiAgICBVbmlIZWxwZXJQYWdlcyh7XG4gICAgICBkdHM6ICdzcmMvdW5pLXBhZ2VzLmQudHMnLFxuICAgICAgZXhjbHVkZTogWycqKi9jb21wb25lbnRzLyoqLyouKiddLFxuICAgICAgLy8gXHU1MjA2XHU1MzA1XHVGRjFBXHU2MjhBXHU5NzVFIHRhYkJhciBcdTk4NzVcdTk3NjJcdTdGMTZcdThCRDFcdThGREJcdTcyRUNcdTdBQ0JcdTUyMDZcdTUzMDVcdUZGMENcdTdGMjlcdTVDMEZcdTRFM0JcdTUzMDVcdTRGNTNcdTc5RUZcdTMwMDFcdTUyQTBcdTVGRUJcdTZCQ0ZcdTRFMkFcdTk4NzVcdTk3NjJcdTU0MkZcdTUyQThcdTMwMDJcbiAgICAgIC8vIHRhYkJhciBcdTRFMDlcdTk4NzVcdUZGMDhpbmRleC9yZWNvcmQvcHJvZmlsZVx1RkYwOVx1NTNDQVx1NTE3Nlx1NUI1MFx1OTg3NVx1NUZDNVx1OTg3Qlx1NzU1OVx1NTcyOFx1NEUzQlx1NTMwNVx1RkYwQ1x1NjU0NVx1NEUwRFx1NTIxN1x1NTE2NVx1MzAwMlxuICAgICAgc3ViUGFja2FnZXM6IFtcbiAgICAgICAgJ3NyYy9wYWdlcy9hYm91dCcsXG4gICAgICAgICdzcmMvcGFnZXMvYWxidW0nLFxuICAgICAgICAnc3JjL3BhZ2VzL2NhbGVuZGFyJyxcbiAgICAgICAgJ3NyYy9wYWdlcy9mZWVkYmFjaycsXG4gICAgICAgICdzcmMvcGFnZXMvZ2FsbGVyeScsXG4gICAgICAgICdzcmMvcGFnZXMvbW9vZCcsXG4gICAgICAgICdzcmMvcGFnZXMvcHJlZmVyZW5jZXMnLFxuICAgICAgICAnc3JjL3BhZ2VzL3ByaXZhY3knLFxuICAgICAgICAnc3JjL3BhZ2VzL3JlY2lwZScsXG4gICAgICAgICdzcmMvcGFnZXMvcmVwb3J0JyxcbiAgICAgICAgJ3NyYy9wYWdlcy9zZXR0aW5ncycsXG4gICAgICAgICdzcmMvcGFnZXMvdGltZWxpbmUnLFxuICAgICAgICAnc3JjL3BhZ2VzL2Nvb2tpbmcnLFxuICAgICAgICAnc3JjL3BhZ2VzL2RhaWx5LW1lYWwtcGxhbicsXG4gICAgICAgICdzcmMvcGFnZXMvbG9naW4nLFxuICAgICAgICAnc3JjL3BhZ2VzL21lYWwtYWdlbnQnLFxuICAgICAgICAnc3JjL3BhZ2VzL21lbWJlcnNoaXAnLFxuICAgICAgICAnc3JjL3BhZ2VzL3dlZWtseS1wbGFuJyxcbiAgICAgICAgJ3NyYy9wYWdlcy9yZWNvbW1lbmRhdGlvbi1oaXN0b3J5JyxcbiAgICAgICAgJ3NyYy9wYWdlcy9mcmlkZ2UnLFxuICAgICAgXSxcbiAgICB9KSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktbGF5b3V0c1xuICAgIFVuaUhlbHBlckxheW91dHMoKSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktY29tcG9uZW50c1xuICAgIFVuaUhlbHBlckNvbXBvbmVudHMoe1xuICAgICAgcmVzb2x2ZXJzOiBbV290UmVzb2x2ZXIoKSwgVW5pRWNoYXJ0c1Jlc29sdmVyKCldLFxuICAgICAgZHRzOiAnc3JjL2NvbXBvbmVudHMuZC50cycsXG4gICAgICBkaXJzOiBbJ3NyYy9jb21wb25lbnRzJywgJ3NyYy9idXNpbmVzcyddLFxuICAgICAgZGlyZWN0b3J5QXNOYW1lc3BhY2U6IHRydWUsXG4gICAgfSksXG4gICAgLy8gaHR0cHM6Ly91bmktZWNoYXJ0cy54aWFvaGUuaW5rXG4gICAgVW5pRWNoYXJ0cygpLFxuICAgIC8vIGh0dHBzOi8vdW5pLWhlbHBlci5jbi9wbHVnaW4tdW5pXG4gICAgVW5pKCksXG4gICAgLy8gaHR0cHM6Ly9naXRodWIuY29tL3VuaS1rdS9idW5kbGUtb3B0aW1pemVyXG4gICAgT3B0aW1pemF0aW9uKHtcbiAgICAgIGVuYWJsZTogaXNNcFdlaXhpbixcbiAgICAgIGxvZ2dlcjogZmFsc2UsXG4gICAgfSksXG4gICAgLy8gaHR0cHM6Ly9naXRodWIuY29tL2FudGZ1L3VucGx1Z2luLWF1dG8taW1wb3J0XG4gICAgQXV0b0ltcG9ydCh7XG4gICAgICBpbXBvcnRzOiBbJ3Z1ZScsICdAdnVldXNlL2NvcmUnLCAncGluaWEnLCAndW5pLWFwcCcsIHtcbiAgICAgICAgZnJvbTogJ0B3b3QtdWkvcm91dGVyJyxcbiAgICAgICAgaW1wb3J0czogWydjcmVhdGVSb3V0ZXInLCAndXNlUm91dGVyJywgJ3VzZVJvdXRlJ10sXG4gICAgICB9LCB7XG4gICAgICAgIGZyb206ICdAd290LXVpL3VpJyxcbiAgICAgICAgaW1wb3J0czogWyd1c2VUb2FzdCcsICd1c2VEaWFsb2cnLCAndXNlTm90aWZ5JywgJ0NvbW1vblV0aWwnXSxcbiAgICAgIH1dLFxuICAgICAgZHRzOiAnc3JjL2F1dG8taW1wb3J0cy5kLnRzJyxcbiAgICAgIGRpcnM6IFsnc3JjL2NvbXBvc2FibGVzJywgJ3NyYy9zdG9yZScsICdzcmMvdXRpbHMnLCAnc3JjL2FwaSddLFxuICAgICAgdnVlVGVtcGxhdGU6IHRydWUsXG4gICAgfSksXG4gICAgLy8gaHR0cHM6Ly9naXRodWIuY29tL2FudGZ1L3Vub2Nzc1xuICAgIC8vIHNlZSB1bm9jc3MuY29uZmlnLnRzIGZvciBjb25maWdcbiAgICBVbm9DU1MoKSxcbiAgXSxcbiAgY3NzOiB7XG4gICAgcHJlcHJvY2Vzc29yT3B0aW9uczoge1xuICAgICAgc2Nzczoge1xuICAgICAgICBhcGk6ICdtb2Rlcm4tY29tcGlsZXInLFxuICAgICAgICBzaWxlbmNlRGVwcmVjYXRpb25zOiBbJ2xlZ2FjeS1qcy1hcGknXSxcbiAgICAgIH0sXG4gICAgfSxcbiAgfSxcbn0pXG4iLCAiY29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2Rpcm5hbWUgPSBcIi9Vc2Vycy96b3UvV2Vic3Rvcm1Qcm9qZWN0cy9tb29kLXJlY2lwZS1jYWxlbmRhci9tb29kLXJlY2lwZS1jYWxlbmRhci9mcm9udGVuZC9zcmMvcmVzb2x2ZXJcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIi9Vc2Vycy96b3UvV2Vic3Rvcm1Qcm9qZWN0cy9tb29kLXJlY2lwZS1jYWxlbmRhci9tb29kLXJlY2lwZS1jYWxlbmRhci9mcm9udGVuZC9zcmMvcmVzb2x2ZXIvaW5kZXgudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kL3NyYy9yZXNvbHZlci9pbmRleC50c1wiO2ltcG9ydCB0eXBlIHsgQ29tcG9uZW50UmVzb2x2ZXIgfSBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktY29tcG9uZW50cydcblxuaW1wb3J0IHsga2ViYWJDYXNlIH0gZnJvbSAnQHVuaS1oZWxwZXIvdml0ZS1wbHVnaW4tdW5pLWNvbXBvbmVudHMnXG5cbmV4cG9ydCBmdW5jdGlvbiBXb3RSZXNvbHZlcigpOiBDb21wb25lbnRSZXNvbHZlciB7XG4gIHJldHVybiB7XG4gICAgdHlwZTogJ2NvbXBvbmVudCcsXG4gICAgcmVzb2x2ZTogKG5hbWU6IHN0cmluZykgPT4ge1xuICAgICAgaWYgKG5hbWUubWF0Y2goL15XZFtBLVpdLykpIHtcbiAgICAgICAgY29uc3QgY29tcE5hbWUgPSBrZWJhYkNhc2UobmFtZSlcbiAgICAgICAgcmV0dXJuIHtcbiAgICAgICAgICBuYW1lLFxuICAgICAgICAgIGZyb206IGBAd290LXVpL3VpL2NvbXBvbmVudHMvJHtjb21wTmFtZX0vJHtjb21wTmFtZX0udnVlYCxcbiAgICAgICAgfVxuICAgICAgfVxuICAgIH0sXG4gIH1cbn1cbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBNFosT0FBTyxTQUFTO0FBQzVhLFNBQVMsa0JBQWtCO0FBQzNCLE9BQU8seUJBQXlCO0FBQ2hDLE9BQU8sc0JBQXNCO0FBQzdCLE9BQU8sdUJBQXVCO0FBQzlCLE9BQU8sb0JBQW9CO0FBQzNCLE9BQU8sa0JBQWtCO0FBQ3pCLFNBQVMsMEJBQTBCO0FBQ25DLFNBQVMsa0JBQWtCO0FBQzNCLE9BQU8sWUFBWTtBQUNuQixPQUFPLGdCQUFnQjtBQUN2QixTQUFTLG9CQUFvQjs7O0FDVDdCLFNBQVMsaUJBQWlCO0FBRW5CLFNBQVMsY0FBaUM7QUFDL0MsU0FBTztBQUFBLElBQ0wsTUFBTTtBQUFBLElBQ04sU0FBUyxDQUFDLFNBQWlCO0FBQ3pCLFVBQUksS0FBSyxNQUFNLFVBQVUsR0FBRztBQUMxQixjQUFNLFdBQVcsVUFBVSxJQUFJO0FBQy9CLGVBQU87QUFBQSxVQUNMO0FBQUEsVUFDQSxNQUFNLHlCQUF5QixRQUFRLElBQUksUUFBUTtBQUFBLFFBQ3JEO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQ0Y7OztBREhBLElBQU8sc0JBQVEsYUFBYTtBQUFBLEVBQzFCLE1BQU07QUFBQSxFQUNOLGNBQWM7QUFBQSxJQUNaLFNBQVMsQ0FBQyxjQUFjLGFBQWE7QUFBQSxFQUN2QztBQUFBLEVBQ0EsU0FBUztBQUFBO0FBQUEsSUFFUCxrQkFBa0I7QUFBQTtBQUFBLElBRWxCLGVBQWU7QUFBQSxNQUNiLEtBQUs7QUFBQSxNQUNMLFNBQVMsQ0FBQyxzQkFBc0I7QUFBQTtBQUFBO0FBQUEsTUFHaEMsYUFBYTtBQUFBLFFBQ1g7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsUUFDQTtBQUFBLFFBQ0E7QUFBQSxRQUNBO0FBQUEsTUFDRjtBQUFBLElBQ0YsQ0FBQztBQUFBO0FBQUEsSUFFRCxpQkFBaUI7QUFBQTtBQUFBLElBRWpCLG9CQUFvQjtBQUFBLE1BQ2xCLFdBQVcsQ0FBQyxZQUFZLEdBQUcsbUJBQW1CLENBQUM7QUFBQSxNQUMvQyxLQUFLO0FBQUEsTUFDTCxNQUFNLENBQUMsa0JBQWtCLGNBQWM7QUFBQSxNQUN2QyxzQkFBc0I7QUFBQSxJQUN4QixDQUFDO0FBQUE7QUFBQSxJQUVELFdBQVc7QUFBQTtBQUFBLElBRVgsSUFBSTtBQUFBO0FBQUEsSUFFSixhQUFhO0FBQUEsTUFDWCxRQUFRO0FBQUEsTUFDUixRQUFRO0FBQUEsSUFDVixDQUFDO0FBQUE7QUFBQSxJQUVELFdBQVc7QUFBQSxNQUNULFNBQVMsQ0FBQyxPQUFPLGdCQUFnQixTQUFTLFdBQVc7QUFBQSxRQUNuRCxNQUFNO0FBQUEsUUFDTixTQUFTLENBQUMsZ0JBQWdCLGFBQWEsVUFBVTtBQUFBLE1BQ25ELEdBQUc7QUFBQSxRQUNELE1BQU07QUFBQSxRQUNOLFNBQVMsQ0FBQyxZQUFZLGFBQWEsYUFBYSxZQUFZO0FBQUEsTUFDOUQsQ0FBQztBQUFBLE1BQ0QsS0FBSztBQUFBLE1BQ0wsTUFBTSxDQUFDLG1CQUFtQixhQUFhLGFBQWEsU0FBUztBQUFBLE1BQzdELGFBQWE7QUFBQSxJQUNmLENBQUM7QUFBQTtBQUFBO0FBQUEsSUFHRCxPQUFPO0FBQUEsRUFDVDtBQUFBLEVBQ0EsS0FBSztBQUFBLElBQ0gscUJBQXFCO0FBQUEsTUFDbkIsTUFBTTtBQUFBLFFBQ0osS0FBSztBQUFBLFFBQ0wscUJBQXFCLENBQUMsZUFBZTtBQUFBLE1BQ3ZDO0FBQUEsSUFDRjtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
