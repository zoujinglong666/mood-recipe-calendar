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
      exclude: ["**/components/**/*.*"]
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
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiLCAic3JjL3Jlc29sdmVyL2luZGV4LnRzIl0sCiAgInNvdXJjZXNDb250ZW50IjogWyJjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZGlybmFtZSA9IFwiL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCIvVXNlcnMvem91L1dlYnN0b3JtUHJvamVjdHMvbW9vZC1yZWNpcGUtY2FsZW5kYXIvbW9vZC1yZWNpcGUtY2FsZW5kYXIvZnJvbnRlbmQvdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kL3ZpdGUuY29uZmlnLnRzXCI7aW1wb3J0IFVuaSBmcm9tICdAdW5pLWhlbHBlci9wbHVnaW4tdW5pJ1xuaW1wb3J0IHsgaXNNcFdlaXhpbiB9IGZyb20gJ0B1bmktaGVscGVyL3VuaS1lbnYnXG5pbXBvcnQgVW5pSGVscGVyQ29tcG9uZW50cyBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktY29tcG9uZW50cydcbmltcG9ydCBVbmlIZWxwZXJMYXlvdXRzIGZyb20gJ0B1bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1sYXlvdXRzJ1xuaW1wb3J0IFVuaUhlbHBlck1hbmlmZXN0IGZyb20gJ0B1bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1tYW5pZmVzdCdcbmltcG9ydCBVbmlIZWxwZXJQYWdlcyBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktcGFnZXMnXG5pbXBvcnQgT3B0aW1pemF0aW9uIGZyb20gJ0B1bmkta3UvYnVuZGxlLW9wdGltaXplcidcbmltcG9ydCB7IFVuaUVjaGFydHNSZXNvbHZlciB9IGZyb20gJ3VuaS1lY2hhcnRzL3Jlc29sdmVyJ1xuaW1wb3J0IHsgVW5pRWNoYXJ0cyB9IGZyb20gJ3VuaS1lY2hhcnRzL3ZpdGUnXG5pbXBvcnQgVW5vQ1NTIGZyb20gJ3Vub2Nzcy92aXRlJ1xuaW1wb3J0IEF1dG9JbXBvcnQgZnJvbSAndW5wbHVnaW4tYXV0by1pbXBvcnQvdml0ZSdcbmltcG9ydCB7IGRlZmluZUNvbmZpZyB9IGZyb20gJ3ZpdGUnXG5pbXBvcnQgeyBXb3RSZXNvbHZlciB9IGZyb20gJy4vc3JjL3Jlc29sdmVyJ1xuLy8gaHR0cHM6Ly92aXRlanMuZGV2L2NvbmZpZy9cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyh7XG4gIGJhc2U6ICcuLycsXG4gIG9wdGltaXplRGVwczoge1xuICAgIGV4Y2x1ZGU6IFsnQHdvdC11aS91aScsICd1bmktZWNoYXJ0cyddLFxuICB9LFxuICBwbHVnaW5zOiBbXG4gICAgLy8gaHR0cHM6Ly9naXRodWIuY29tL3VuaS1oZWxwZXIvdml0ZS1wbHVnaW4tdW5pLW1hbmlmZXN0XG4gICAgVW5pSGVscGVyTWFuaWZlc3QoKSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktcGFnZXNcbiAgICBVbmlIZWxwZXJQYWdlcyh7XG4gICAgICBkdHM6ICdzcmMvdW5pLXBhZ2VzLmQudHMnLFxuICAgICAgZXhjbHVkZTogWycqKi9jb21wb25lbnRzLyoqLyouKiddLFxuICAgIH0pLFxuICAgIC8vIGh0dHBzOi8vZ2l0aHViLmNvbS91bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1sYXlvdXRzXG4gICAgVW5pSGVscGVyTGF5b3V0cygpLFxuICAgIC8vIGh0dHBzOi8vZ2l0aHViLmNvbS91bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1jb21wb25lbnRzXG4gICAgVW5pSGVscGVyQ29tcG9uZW50cyh7XG4gICAgICByZXNvbHZlcnM6IFtXb3RSZXNvbHZlcigpLCBVbmlFY2hhcnRzUmVzb2x2ZXIoKV0sXG4gICAgICBkdHM6ICdzcmMvY29tcG9uZW50cy5kLnRzJyxcbiAgICAgIGRpcnM6IFsnc3JjL2NvbXBvbmVudHMnLCAnc3JjL2J1c2luZXNzJ10sXG4gICAgICBkaXJlY3RvcnlBc05hbWVzcGFjZTogdHJ1ZSxcbiAgICB9KSxcbiAgICAvLyBodHRwczovL3VuaS1lY2hhcnRzLnhpYW9oZS5pbmtcbiAgICBVbmlFY2hhcnRzKCksXG4gICAgLy8gaHR0cHM6Ly91bmktaGVscGVyLmNuL3BsdWdpbi11bmlcbiAgICBVbmkoKSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vdW5pLWt1L2J1bmRsZS1vcHRpbWl6ZXJcbiAgICBPcHRpbWl6YXRpb24oe1xuICAgICAgZW5hYmxlOiBpc01wV2VpeGluLFxuICAgICAgbG9nZ2VyOiBmYWxzZSxcbiAgICB9KSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vYW50ZnUvdW5wbHVnaW4tYXV0by1pbXBvcnRcbiAgICBBdXRvSW1wb3J0KHtcbiAgICAgIGltcG9ydHM6IFsndnVlJywgJ0B2dWV1c2UvY29yZScsICdwaW5pYScsICd1bmktYXBwJywge1xuICAgICAgICBmcm9tOiAnQHdvdC11aS9yb3V0ZXInLFxuICAgICAgICBpbXBvcnRzOiBbJ2NyZWF0ZVJvdXRlcicsICd1c2VSb3V0ZXInLCAndXNlUm91dGUnXSxcbiAgICAgIH0sIHtcbiAgICAgICAgZnJvbTogJ0B3b3QtdWkvdWknLFxuICAgICAgICBpbXBvcnRzOiBbJ3VzZVRvYXN0JywgJ3VzZURpYWxvZycsICd1c2VOb3RpZnknLCAnQ29tbW9uVXRpbCddLFxuICAgICAgfV0sXG4gICAgICBkdHM6ICdzcmMvYXV0by1pbXBvcnRzLmQudHMnLFxuICAgICAgZGlyczogWydzcmMvY29tcG9zYWJsZXMnLCAnc3JjL3N0b3JlJywgJ3NyYy91dGlscycsICdzcmMvYXBpJ10sXG4gICAgICB2dWVUZW1wbGF0ZTogdHJ1ZSxcbiAgICB9KSxcbiAgICAvLyBodHRwczovL2dpdGh1Yi5jb20vYW50ZnUvdW5vY3NzXG4gICAgLy8gc2VlIHVub2Nzcy5jb25maWcudHMgZm9yIGNvbmZpZ1xuICAgIFVub0NTUygpLFxuICBdLFxuICBjc3M6IHtcbiAgICBwcmVwcm9jZXNzb3JPcHRpb25zOiB7XG4gICAgICBzY3NzOiB7XG4gICAgICAgIGFwaTogJ21vZGVybi1jb21waWxlcicsXG4gICAgICAgIHNpbGVuY2VEZXByZWNhdGlvbnM6IFsnbGVnYWN5LWpzLWFwaSddLFxuICAgICAgfSxcbiAgICB9LFxuICB9LFxufSlcbiIsICJjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZGlybmFtZSA9IFwiL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kL3NyYy9yZXNvbHZlclwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiL1VzZXJzL3pvdS9XZWJzdG9ybVByb2plY3RzL21vb2QtcmVjaXBlLWNhbGVuZGFyL21vb2QtcmVjaXBlLWNhbGVuZGFyL2Zyb250ZW5kL3NyYy9yZXNvbHZlci9pbmRleC50c1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vVXNlcnMvem91L1dlYnN0b3JtUHJvamVjdHMvbW9vZC1yZWNpcGUtY2FsZW5kYXIvbW9vZC1yZWNpcGUtY2FsZW5kYXIvZnJvbnRlbmQvc3JjL3Jlc29sdmVyL2luZGV4LnRzXCI7aW1wb3J0IHR5cGUgeyBDb21wb25lbnRSZXNvbHZlciB9IGZyb20gJ0B1bmktaGVscGVyL3ZpdGUtcGx1Z2luLXVuaS1jb21wb25lbnRzJ1xuXG5pbXBvcnQgeyBrZWJhYkNhc2UgfSBmcm9tICdAdW5pLWhlbHBlci92aXRlLXBsdWdpbi11bmktY29tcG9uZW50cydcblxuZXhwb3J0IGZ1bmN0aW9uIFdvdFJlc29sdmVyKCk6IENvbXBvbmVudFJlc29sdmVyIHtcbiAgcmV0dXJuIHtcbiAgICB0eXBlOiAnY29tcG9uZW50JyxcbiAgICByZXNvbHZlOiAobmFtZTogc3RyaW5nKSA9PiB7XG4gICAgICBpZiAobmFtZS5tYXRjaCgvXldkW0EtWl0vKSkge1xuICAgICAgICBjb25zdCBjb21wTmFtZSA9IGtlYmFiQ2FzZShuYW1lKVxuICAgICAgICByZXR1cm4ge1xuICAgICAgICAgIG5hbWUsXG4gICAgICAgICAgZnJvbTogYEB3b3QtdWkvdWkvY29tcG9uZW50cy8ke2NvbXBOYW1lfS8ke2NvbXBOYW1lfS52dWVgLFxuICAgICAgICB9XG4gICAgICB9XG4gICAgfSxcbiAgfVxufVxuIl0sCiAgIm1hcHBpbmdzIjogIjtBQUE0WixPQUFPLFNBQVM7QUFDNWEsU0FBUyxrQkFBa0I7QUFDM0IsT0FBTyx5QkFBeUI7QUFDaEMsT0FBTyxzQkFBc0I7QUFDN0IsT0FBTyx1QkFBdUI7QUFDOUIsT0FBTyxvQkFBb0I7QUFDM0IsT0FBTyxrQkFBa0I7QUFDekIsU0FBUywwQkFBMEI7QUFDbkMsU0FBUyxrQkFBa0I7QUFDM0IsT0FBTyxZQUFZO0FBQ25CLE9BQU8sZ0JBQWdCO0FBQ3ZCLFNBQVMsb0JBQW9COzs7QUNUN0IsU0FBUyxpQkFBaUI7QUFFbkIsU0FBUyxjQUFpQztBQUMvQyxTQUFPO0FBQUEsSUFDTCxNQUFNO0FBQUEsSUFDTixTQUFTLENBQUMsU0FBaUI7QUFDekIsVUFBSSxLQUFLLE1BQU0sVUFBVSxHQUFHO0FBQzFCLGNBQU0sV0FBVyxVQUFVLElBQUk7QUFDL0IsZUFBTztBQUFBLFVBQ0w7QUFBQSxVQUNBLE1BQU0seUJBQXlCLFFBQVEsSUFBSSxRQUFRO0FBQUEsUUFDckQ7QUFBQSxNQUNGO0FBQUEsSUFDRjtBQUFBLEVBQ0Y7QUFDRjs7O0FESEEsSUFBTyxzQkFBUSxhQUFhO0FBQUEsRUFDMUIsTUFBTTtBQUFBLEVBQ04sY0FBYztBQUFBLElBQ1osU0FBUyxDQUFDLGNBQWMsYUFBYTtBQUFBLEVBQ3ZDO0FBQUEsRUFDQSxTQUFTO0FBQUE7QUFBQSxJQUVQLGtCQUFrQjtBQUFBO0FBQUEsSUFFbEIsZUFBZTtBQUFBLE1BQ2IsS0FBSztBQUFBLE1BQ0wsU0FBUyxDQUFDLHNCQUFzQjtBQUFBLElBQ2xDLENBQUM7QUFBQTtBQUFBLElBRUQsaUJBQWlCO0FBQUE7QUFBQSxJQUVqQixvQkFBb0I7QUFBQSxNQUNsQixXQUFXLENBQUMsWUFBWSxHQUFHLG1CQUFtQixDQUFDO0FBQUEsTUFDL0MsS0FBSztBQUFBLE1BQ0wsTUFBTSxDQUFDLGtCQUFrQixjQUFjO0FBQUEsTUFDdkMsc0JBQXNCO0FBQUEsSUFDeEIsQ0FBQztBQUFBO0FBQUEsSUFFRCxXQUFXO0FBQUE7QUFBQSxJQUVYLElBQUk7QUFBQTtBQUFBLElBRUosYUFBYTtBQUFBLE1BQ1gsUUFBUTtBQUFBLE1BQ1IsUUFBUTtBQUFBLElBQ1YsQ0FBQztBQUFBO0FBQUEsSUFFRCxXQUFXO0FBQUEsTUFDVCxTQUFTLENBQUMsT0FBTyxnQkFBZ0IsU0FBUyxXQUFXO0FBQUEsUUFDbkQsTUFBTTtBQUFBLFFBQ04sU0FBUyxDQUFDLGdCQUFnQixhQUFhLFVBQVU7QUFBQSxNQUNuRCxHQUFHO0FBQUEsUUFDRCxNQUFNO0FBQUEsUUFDTixTQUFTLENBQUMsWUFBWSxhQUFhLGFBQWEsWUFBWTtBQUFBLE1BQzlELENBQUM7QUFBQSxNQUNELEtBQUs7QUFBQSxNQUNMLE1BQU0sQ0FBQyxtQkFBbUIsYUFBYSxhQUFhLFNBQVM7QUFBQSxNQUM3RCxhQUFhO0FBQUEsSUFDZixDQUFDO0FBQUE7QUFBQTtBQUFBLElBR0QsT0FBTztBQUFBLEVBQ1Q7QUFBQSxFQUNBLEtBQUs7QUFBQSxJQUNILHFCQUFxQjtBQUFBLE1BQ25CLE1BQU07QUFBQSxRQUNKLEtBQUs7QUFBQSxRQUNMLHFCQUFxQixDQUFDLGVBQWU7QUFBQSxNQUN2QztBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQ0YsQ0FBQzsiLAogICJuYW1lcyI6IFtdCn0K
