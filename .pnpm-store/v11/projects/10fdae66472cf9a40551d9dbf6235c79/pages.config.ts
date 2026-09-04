/*
 * @Author: weisheng
 * @Date: 2025-06-23 22:23:05
 * @LastEditTime: 2025-06-27 13:04:54
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /wot-starter/pages.config.ts
 * 记得注释
 */
import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'

export default defineUniPages({
  pages: [],
  globalStyle: {
    // 关闭原生导航栏，统一使用自定义 AppNav 组件（含状态栏/胶囊适配）
    navigationStyle: 'custom',
    // 导航栏配置（原生已关闭，以下作为兜底背景色）
    navigationBarBackgroundColor: '@navBgColor',
    navigationBarTextStyle: '@navTxtStyle',
    navigationBarTitleText: '心情菜谱日历',

    // 页面背景配置
    backgroundColor: '@bgColor',
    backgroundTextStyle: '@bgTxtStyle',
    backgroundColorTop: '@bgColorTop',
    backgroundColorBottom: '@bgColorBottom',

    // 下拉刷新配置
    enablePullDownRefresh: false,
    onReachBottomDistance: 50,
  },
  tabBar: {
    custom: true,
    height: '0',
    color: '@tabColor',
    selectedColor: '@tabSelectedColor',
    backgroundColor: '@tabBgColor',
    borderStyle: '@tabBorderStyle',
    list: [{
      pagePath: 'pages/index/index',
    }, {
      pagePath: 'pages/record/index',
    }, {
      pagePath: 'pages/profile/index',
    }],
  },
})
