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
      "navigationBarTitleText": "心情日历"
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
    "path": "pages/record/index",
    "type": "page",
    "name": "record",
    "layout": "tabbar",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "记录今日伙食"
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
      "navigationBarTitleText": "这一周吃什么"
    }
  },
  {
    "path": "pages/weekly-plan/index",
    "type": "page",
    "name": "weekly-plan",
    "layout": "default",
    "style": {
      "navigationStyle": "custom",
      "navigationBarTitleText": "锅仔备餐小本"
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
