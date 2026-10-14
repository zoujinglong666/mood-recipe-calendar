"use strict";
const common_vendor = require("../common/vendor.js");
const pages = [
  {
    "path": "pages/index/index",
    "type": "home",
    "name": "home",
    "layout": "tabbar",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "首页"
    }
  },
  {
    "path": "pages/about/index",
    "type": "page",
    "name": "about",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "关于"
    }
  },
  {
    "path": "pages/album/index",
    "type": "page",
    "name": "album",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "月度画册"
    }
  },
  {
    "path": "pages/calendar/index",
    "type": "page",
    "name": "calendar",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "食光日历"
    }
  },
  {
    "path": "pages/cooking/index",
    "type": "page",
    "name": "cooking",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "跟锅仔做菜"
    }
  },
  {
    "path": "pages/daily-meal-plan/index",
    "type": "page",
    "name": "daily-meal-plan",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "今日三餐"
    }
  },
  {
    "path": "pages/feedback/index",
    "type": "page",
    "name": "feedback",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "反馈建议"
    }
  },
  {
    "path": "pages/fridge/index",
    "type": "page",
    "name": "fridge",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "我的冰箱"
    }
  },
  {
    "path": "pages/gallery/index",
    "type": "page",
    "name": "gallery",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔形象馆"
    }
  },
  {
    "path": "pages/login/index",
    "type": "page",
    "name": "login",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "微信登录"
    }
  },
  {
    "path": "pages/meal-agent/index",
    "type": "page",
    "name": "meal-agent",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔管饭"
    }
  },
  {
    "path": "pages/membership/index",
    "type": "page",
    "name": "membership",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔会员"
    }
  },
  {
    "path": "pages/mood/index",
    "type": "page",
    "name": "mood",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "选一个心情吧"
    }
  },
  {
    "path": "pages/nutrition-report/index",
    "type": "page",
    "name": "nutrition-report",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "本月营养月报"
    }
  },
  {
    "path": "pages/preferences/index",
    "type": "page",
    "name": "preferences",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔记忆"
    }
  },
  {
    "path": "pages/privacy/index",
    "type": "page",
    "name": "privacy",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "隐私政策"
    }
  },
  {
    "path": "pages/profile/index",
    "type": "page",
    "name": "profile",
    "layout": "tabbar",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "我的"
    }
  },
  {
    "path": "pages/recipe/index",
    "type": "page",
    "name": "recipe",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "今日推荐"
    }
  },
  {
    "path": "pages/recipe-library/index",
    "type": "page",
    "name": "recipe-library",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "菜谱库"
    }
  },
  {
    "path": "pages/recommendation-history/index",
    "type": "page",
    "name": "recommendation-history",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "推荐记录"
    }
  },
  {
    "path": "pages/record/detail",
    "type": "page",
    "name": "record-detail",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "这一餐的食光"
    }
  },
  {
    "path": "pages/record/edit",
    "type": "page",
    "name": "record-edit",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "编辑这顿饭"
    }
  },
  {
    "path": "pages/record/index",
    "type": "page",
    "name": "record",
    "layout": "tabbar",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "记一笔今天"
    }
  },
  {
    "path": "pages/report/index",
    "type": "page",
    "name": "report",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "年度报告"
    }
  },
  {
    "path": "pages/settings/index",
    "type": "page",
    "name": "settings",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "设置"
    }
  },
  {
    "path": "pages/theme-albums/detail",
    "type": "page",
    "name": "theme-album-detail",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "主题专辑"
    }
  },
  {
    "path": "pages/theme-albums/index",
    "type": "page",
    "name": "theme-albums",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "主题专辑"
    }
  },
  {
    "path": "pages/timeline/index",
    "type": "page",
    "name": "timeline",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "菜谱时光机"
    }
  },
  {
    "path": "pages/weekly-plan/detail",
    "type": "page",
    "name": "weekly-plan-detail",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "这一周吃什么",
      "enablePullDownRefresh": true
    }
  },
  {
    "path": "pages/weekly-plan/index",
    "type": "page",
    "name": "weekly-plan",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔管饭"
    }
  },
  {
    "path": "pages/wishlist/index",
    "type": "page",
    "name": "wishlist",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "想吃清单"
    }
  },
  {
    "path": "pages/admin/recipes/edit",
    "type": "page",
    "name": "admin-recipe-edit",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "编辑菜谱"
    }
  },
  {
    "path": "pages/admin/recipes/index",
    "type": "page",
    "name": "admin-recipes",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "菜谱管理"
    }
  }
];
const subPackages = [];
function generateRoutes() {
  const routes = pages.map((page) => {
    const newPath = `/${page.path}`;
    return { ...page, path: newPath };
  });
  if (subPackages && subPackages.length > 0) {
    subPackages.forEach((subPackage) => {
      const subRoutes = subPackage.pages.map((page) => {
        const newPath = `/${subPackage.root}/${page.path}`;
        return { ...page, path: newPath };
      });
      routes.push(...subRoutes);
    });
  }
  return routes;
}
const router = common_vendor.createRouter({
  routes: generateRoutes()
});
exports.router = router;
