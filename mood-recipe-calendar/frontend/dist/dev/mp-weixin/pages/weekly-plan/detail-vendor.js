"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const api_weeklyPlans = require("../../api/weeklyPlans.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_albumShare = require("../../utils/albumShare.js");
const utils_albumLayout = require("../../utils/albumLayout.js");
const utils_assets = require("../../utils/assets.js");
const utils_toast = require("../../utils/toast.js");
const utils_bus = require("../../utils/bus.js");
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_image_preview = __unplugin_components_1;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_image_preview + _component_layout_default_uni)();
}
if (!Math) {
  (ErrorState + GuozaiImage + GuozaiChipGroup + GuozaiButton)();
}
const GuozaiButton = () => "../../components/guozai/GuozaiButton.js";
const GuozaiChipGroup = () => "../../components/guozai/GuozaiChipGroup.js";
const GuozaiImage = () => "../../components/guozai/GuozaiImage.js";
const ErrorState = () => "../../components/guozai/ErrorState.js";
const _sfc_defineComponent = common_vendor.defineComponent({
  __name: "detail",
  setup(__props) {
    common_vendor.onShareAppMessage(() => {
      var _a, _b, _c;
      const pages = getCurrentPages();
      const route = ((_a = pages[pages.length - 1]) == null ? void 0 : _a.route) || "";
      return {
        title: ((_c = (_b = plan.value) == null ? void 0 : _b.days) == null ? void 0 : _c.length) ? `这一周，锅仔帮我安排了 ${plan.value.days.length} 天的饭菜` : "锅仔 · 替你备好这一周的饭菜",
        path: `/${route}`
      };
    });
    common_vendor.onShareTimeline(() => {
      var _a, _b;
      return {
        title: ((_b = (_a = plan.value) == null ? void 0 : _a.days) == null ? void 0 : _b.length) ? `这一周，锅仔帮我安排了 ${plan.value.days.length} 天的饭菜` : "锅仔 · 替你备好这一周的饭菜"
      };
    });
    const router = common_vendor.useRouter();
    const { previewImage } = common_vendor.useImagePreview();
    const plan = common_vendor.ref();
    const loading = common_vendor.ref(true);
    const requestedPlanId = common_vendor.ref();
    const swapping = common_vendor.ref(-1);
    const activeDay = common_vendor.ref(0);
    const shoppingOpen = common_vendor.ref(false);
    const detailsOpen = common_vendor.ref({});
    const coverLoading = common_vendor.ref({});
    const activeDish = common_vendor.ref({});
    const sharingDay = common_vendor.ref(-1);
    let coverQueue = Promise.resolve();
    let loadSequence = 0;
    let loadStarted = false;
    const error = common_vendor.ref("");
    const weekdayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
    const outcomeOptions = [
      { key: "DONE", label: "做完了" },
      { key: "LEFTOVER", label: "有剩菜" },
      { key: "HARD", label: "有点难" },
      { key: "SKIP", label: "没做成" }
    ];
    const outcomeChipOptions = outcomeOptions.map((option) => ({ value: option.key, label: option.label }));
    const outcomeMap = {
      DONE: { cooked: true, leftover: false, tooHard: false },
      LEFTOVER: { cooked: true, leftover: true, tooHard: false },
      HARD: { cooked: false, leftover: false, tooHard: true },
      SKIP: { cooked: false, leftover: false, tooHard: false }
    };
    const outcomes = common_vendor.ref({});
    const postMealMoods = ["开心", "满足", "平静", "疲惫", "焦虑", "难过", "低落"];
    const postMealMoodOptions = postMealMoods.map((m) => ({ value: m, label: `${utils_albumLayout.MOOD_EMOJI[m] ?? ""} ${m}` }));
    const moodAfters = common_vendor.ref({});
    async function reportMood(dayIndex, dishIndex, dishName, mood) {
      const m = Array.isArray(mood) ? mood[0] : mood;
      if (!m || !plan.value)
        return;
      const key = feedbackKey(dayIndex, dishIndex);
      if (moodAfters.value[key] === m)
        return;
      moodAfters.value = { ...moodAfters.value, [key]: m };
      try {
        await api_weeklyPlans.reportPlanDishOutcome({ planId: plan.value.id, dayIndex, dishIndex, dishName, moodAfter: m });
      } catch (error2) {
        const rollback = { ...moodAfters.value };
        delete rollback[key];
        moodAfters.value = rollback;
        utils_toast.toastError(error2, "心情没记上，稍后再试");
      }
    }
    const groups = common_vendor.computed(() => ["肉蛋豆", "蔬菜", "主食", "调料"].map((category) => {
      var _a;
      return { category, items: ((_a = plan.value) == null ? void 0 : _a.shopping.filter((item) => item.category === category)) || [] };
    }).filter((group) => group.items.length));
    const agentCheckCopy = common_vendor.computed(() => {
      var _a, _b;
      const agent = (_a = plan.value) == null ? void 0 : _a.agent;
      if (!agent)
        return "";
      const score = typeof agent.score === "number" ? `搭配检查 ${agent.score} 分` : "搭配检查已完成";
      const memory = ((_b = agent.memoryUsed) == null ? void 0 : _b.length) ? `参考了 ${agent.memoryUsed.length} 条锅仔记忆` : "已检查忌口、重复和荤素搭配";
      return `${score} · ${memory}`;
    });
    function weekday(day, index) {
      return (day == null ? void 0 : day.day) || weekdayNames[index] || `第${index + 1}天`;
    }
    function dishesOf(day) {
      var _a;
      return ((_a = day.dishes) == null ? void 0 : _a.length) ? day.dishes : [{ name: day.dishName, ingredients: day.ingredients, steps: day.steps, fallbackImageUrl: day.fallbackImageUrl }];
    }
    function currentDish(day, dayIndex) {
      return dishesOf(day)[activeDish.value[dayIndex] || 0] || dishesOf(day)[0];
    }
    function coverKey(dayIndex, dishIndex) {
      return `${dayIndex}-${dishIndex}`;
    }
    function coverOf(day, dishIndex) {
      const dish = dishesOf(day)[dishIndex];
      return (dish == null ? void 0 : dish.imageUrl) || (dishIndex === 0 ? day.imageUrl : "") || (dish == null ? void 0 : dish.fallbackImageUrl);
    }
    function previewDishImages(day, dishIndex) {
      const covers = dishesOf(day).map((_, index) => ({ index, url: api_request.resolveAssetUrl(coverOf(day, index)) })).filter((item) => item.url);
      if (!covers.length)
        return;
      previewImage({
        images: covers.map((item) => item.url),
        startPosition: Math.max(0, covers.findIndex((item) => item.index === dishIndex)),
        closeOnClick: true,
        loop: covers.length > 1
      });
    }
    function hasGeneratedCover(day, dishIndex) {
      const dish = day && dishesOf(day)[dishIndex];
      return Boolean((dish == null ? void 0 : dish.imageUrl) || dishIndex === 0 && (day == null ? void 0 : day.imageUrl));
    }
    function selectDay(index) {
      activeDay.value = index;
      queueCovers(index);
    }
    function onDayChange(event) {
      activeDay.value = event.detail.current;
      queueCovers(activeDay.value);
    }
    function selectDish(dayIndex, dishIndex) {
      activeDish.value = { ...activeDish.value, [dayIndex]: dishIndex };
      queueCovers(dayIndex);
    }
    function onDishChange(dayIndex, event) {
      selectDish(dayIndex, event.detail.current);
    }
    function toggleDetails(index) {
      detailsOpen.value = { ...detailsOpen.value, [index]: !detailsOpen.value[index] };
    }
    function feedbackKey(dayIndex, dishIndex) {
      return `${dayIndex}-${dishIndex}`;
    }
    async function reportOutcome(dayIndex, dishIndex, dishName, kind) {
      if (!plan.value || outcomes.value[feedbackKey(dayIndex, dishIndex)] === kind)
        return;
      const key = feedbackKey(dayIndex, dishIndex);
      outcomes.value = { ...outcomes.value, [key]: kind };
      try {
        await api_weeklyPlans.reportPlanDishOutcome({
          planId: plan.value.id,
          dayIndex,
          dishIndex,
          dishName,
          ...outcomeMap[kind]
        });
        utils_toast.toastSuccess("锅仔记下了，下次会少问少排");
      } catch (error2) {
        const rollback = { ...outcomes.value };
        delete rollback[key];
        outcomes.value = rollback;
        utils_toast.toastError(error2, "反馈没记上，稍后再试");
      }
    }
    function onOutcomeChange(dayIndex, dishIndex, dishName, value) {
      const kind = String(value);
      if (kind in outcomeMap)
        void reportOutcome(dayIndex, dishIndex, dishName, kind);
    }
    async function load(opts = {}) {
      loadStarted = true;
      const sequence = ++loadSequence;
      const id = requestedPlanId.value;
      loading.value = true;
      error.value = "";
      try {
        const loaded = id ? await api_weeklyPlans.getWeeklyPlan(id) : await api_weeklyPlans.getCurrentPlan();
        if (sequence !== loadSequence)
          return;
        plan.value = loaded;
        if (!opts.preserveDay)
          activeDay.value = 0;
        queueCovers(0);
      } catch (err) {
        if (sequence !== loadSequence)
          return;
        error.value = (err == null ? void 0 : err.message) || "加载失败";
      } finally {
        loading.value = false;
        common_vendor.index.stopPullDownRefresh();
      }
    }
    function reloadPlan() {
      void load({ preserveDay: true });
    }
    common_vendor.onLoad((query) => {
      const id = Number(query == null ? void 0 : query.id);
      requestedPlanId.value = Number.isFinite(id) && id > 0 ? id : void 0;
      void load();
    });
    common_vendor.onShow(() => {
      if (!loadStarted)
        void load();
    });
    common_vendor.onPullDownRefresh(() => {
      void load();
    });
    utils_bus.bus.on(utils_bus.MRC_EVENTS.PLAN_CHANGED, reloadPlan);
    common_vendor.onUnmounted(() => utils_bus.bus.off(utils_bus.MRC_EVENTS.PLAN_CHANGED, reloadPlan));
    function queueCovers(dayIndex) {
      coverQueue = coverQueue.then(async () => {
        var _a;
        const day = (_a = plan.value) == null ? void 0 : _a.days[dayIndex];
        if (!day)
          return;
        await Promise.all(dishesOf(day).map((_, dishIndex) => ensureDishCover(dayIndex, dishIndex)));
      }).catch(() => void 0);
    }
    async function ensureDishCover(dayIndex, dishIndex) {
      const key = coverKey(dayIndex, dishIndex);
      if (!plan.value || hasGeneratedCover(plan.value.days[dayIndex], dishIndex) || coverLoading.value[key])
        return;
      coverLoading.value = { ...coverLoading.value, [key]: true };
      try {
        plan.value = await api_weeklyPlans.generatePlanDishCover(plan.value.id, dayIndex, dishIndex);
      } catch {
      } finally {
        coverLoading.value = { ...coverLoading.value, [key]: false };
      }
    }
    async function replaceDay(index) {
      if (!plan.value || swapping.value >= 0)
        return;
      swapping.value = index;
      try {
        plan.value = await api_weeklyPlans.replacePlanDay(plan.value.id, index);
        activeDish.value = { ...activeDish.value, [index]: 0 };
        queueCovers(index);
      } catch (error2) {
        utils_toast.toastError(error2, "换菜失败，请重试");
      } finally {
        swapping.value = -1;
      }
    }
    function copyShoppingList() {
      var _a;
      const items = ((_a = plan.value) == null ? void 0 : _a.shopping) || [];
      if (!items.length) {
        utils_toast.toastError(null, "清单是空的");
        return;
      }
      const text = items.map((i) => `${i.name} ${i.quantity}`).join("\n");
      common_vendor.index.setClipboardData({
        data: `本周采购清单：
${text}`,
        success: () => utils_toast.toastSuccess("清单已复制")
      });
    }
    function buyAllIngredients() {
      var _a;
      const pending = (((_a = plan.value) == null ? void 0 : _a.shopping) || []).filter((i) => !i.purchased);
      if (!pending.length) {
        utils_toast.toastSuccess("都买齐了，真棒");
        return;
      }
      copyShoppingList();
      setTimeout(() => utils_toast.toast("食材箱正在筹备中，清单已复制，先去美团或超市买齐"), 400);
    }
    async function toggle(name) {
      if (!plan.value)
        return;
      try {
        plan.value = await api_weeklyPlans.toggleShoppingItem(plan.value.id, name);
      } catch (error2) {
        utils_toast.toastError(error2, "清单更新失败");
      }
    }
    function record(dish) {
      common_vendor.index.setStorageSync("mrc_record_draft", { dish });
      router.pushTab({ name: "record" });
    }
    async function shareDay(day, index) {
      if (sharingDay.value >= 0)
        return;
      const dishIndex = activeDish.value[index] || 0;
      const dish = currentDish(day, index);
      if (!dish)
        return;
      sharingDay.value = index;
      try {
        await common_vendor.nextTick$1();
        const path = await utils_albumShare.exportRecipeShare({
          name: dish.name,
          // 周计划没有真实心情字段，留空由分享卡展示品牌标语，避免虚构心情
          mood: "",
          reason: day.healthTip,
          ingredients: dish.ingredients,
          steps: dish.steps,
          image: api_request.resolveAssetUrl(coverOf(day, dishIndex)),
          guozaiPath: `${utils_assets.STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`,
          style: "guozai"
        }, "weeklyRecipeShareCanvas");
        await utils_albumShare.saveShareImage(path, `${dish.name}-锅仔食谱卡.png`);
        utils_toast.toastSuccess("食谱卡已保存，可以分享给朋友");
      } catch (error2) {
        utils_toast.toastError(error2, "食谱卡生成失败，请重试");
      } finally {
        sharingDay.value = -1;
      }
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "这一周吃什么",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : error.value ? {
        e: common_vendor.o(load),
        f: common_vendor.p({
          message: error.value
        })
      } : plan.value ? common_vendor.e({
        h: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        i: common_vendor.f(plan.value.days, (day, index, i0) => {
          return {
            a: common_vendor.t(weekday(day, index)),
            b: common_vendor.t(index + 1),
            c: index,
            d: activeDay.value === index ? 1 : "",
            e: activeDay.value === index,
            f: `查看${weekday(day, index)}晚餐`,
            g: common_vendor.o(($event) => selectDay(index), index)
          };
        }),
        j: common_vendor.t(weekday(plan.value.days[activeDay.value], activeDay.value)),
        k: common_vendor.t(activeDay.value + 1),
        l: common_vendor.t(plan.value.days.length),
        m: plan.value.agent
      }, plan.value.agent ? common_vendor.e({
        n: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        o: common_vendor.t(agentCheckCopy.value),
        p: (_a = plan.value.agent.degradeReasons) == null ? void 0 : _a.length
      }, ((_b = plan.value.agent.degradeReasons) == null ? void 0 : _b.length) ? {} : {}) : {}, {
        q: common_vendor.f(plan.value.days, (day, index, i0) => {
          return common_vendor.e({
            a: common_vendor.t(weekday(day, index)),
            b: common_vendor.t(dishesOf(day).length),
            c: common_vendor.t(swapping.value === index ? "正在换菜…" : "换一桌"),
            d: `换掉${day.dishName}`,
            e: common_vendor.o(($event) => replaceDay(index), `${day.day}-${index}`),
            f: common_vendor.f(dishesOf(day), (dish, dishIndex, i1) => {
              return common_vendor.e({
                a: coverOf(day, dishIndex)
              }, coverOf(day, dishIndex) ? {
                b: "5d49e1be-3-" + i0 + "-" + i1 + ",5d49e1be-0",
                c: common_vendor.p({
                  src: common_vendor.unref(api_request.resolveAssetUrl)(coverOf(day, dishIndex)),
                  ["placeholder-text"]: "🍲"
                }),
                d: `预览${dish.name}菜品大图`,
                e: common_vendor.o(($event) => previewDishImages(day, dishIndex), `${dish.name}-${dishIndex}`)
              } : {
                f: common_vendor.t(coverLoading.value[coverKey(index, dishIndex)] ? "锅仔正在画这道菜…" : "这道菜的照片正在路上")
              }, {
                g: coverLoading.value[coverKey(index, dishIndex)]
              }, coverLoading.value[coverKey(index, dishIndex)] ? {} : {}, dishesOf(day).length > 1 ? {
                h: common_vendor.t(dishIndex + 1),
                i: common_vendor.t(dishesOf(day).length)
              } : {}, {
                j: coverLoading.value[coverKey(index, dishIndex)] ? 1 : "",
                k: `${dish.name}-${dishIndex}`
              });
            }),
            g: dishesOf(day).length > 1,
            h: activeDish.value[index] || 0,
            i: common_vendor.o(($event) => onDishChange(index, $event), `${day.day}-${index}`),
            j: dishesOf(day).length > 1
          }, dishesOf(day).length > 1 ? {
            k: common_vendor.f(dishesOf(day), (dish, dishIndex, i1) => {
              return {
                a: common_vendor.t(dishIndex + 1),
                b: common_vendor.t(dish.name),
                c: `${dish.name}-${dishIndex}`,
                d: (activeDish.value[index] || 0) === dishIndex ? 1 : "",
                e: (activeDish.value[index] || 0) === dishIndex,
                f: `查看第${dishIndex + 1}道${dish.name}`,
                g: common_vendor.o(($event) => selectDish(index, dishIndex), `${dish.name}-${dishIndex}`)
              };
            })
          } : {}, {
            l: common_vendor.t(currentDish(day, index).name),
            m: common_vendor.t(day.healthTip),
            n: common_vendor.t(day.reuseHint),
            o: common_vendor.t(sharingDay.value === index ? "锅仔正在排版食谱卡…" : "保存这道菜的食谱卡"),
            p: `生成${currentDish(day, index).name}的食谱卡`,
            q: common_vendor.o(($event) => shareDay(day, index), `${day.day}-${index}`),
            r: common_vendor.t(detailsOpen.value[index] ? "收起食材与做法" : "看食材和做法"),
            s: detailsOpen.value[index] ? 1 : "",
            t: Boolean(detailsOpen.value[index]),
            v: detailsOpen.value[index] ? "收起食材与做法" : "展开食材与做法",
            w: common_vendor.o(($event) => toggleDetails(index), `${day.day}-${index}`),
            x: detailsOpen.value[index]
          }, detailsOpen.value[index] ? {
            y: common_vendor.f(dishesOf(day), (dish, dishIndex, i1) => {
              var _a2, _b2, _c, _d;
              return common_vendor.e({
                a: common_vendor.t(dishIndex + 1),
                b: common_vendor.t(dish.name),
                c: (_a2 = dish.ingredients) == null ? void 0 : _a2.length
              }, ((_b2 = dish.ingredients) == null ? void 0 : _b2.length) ? {
                d: common_vendor.f(dish.ingredients, (item, k2, i2) => {
                  return {
                    a: common_vendor.t(item),
                    b: item
                  };
                })
              } : {}, {
                e: (_c = dish.steps) == null ? void 0 : _c.length
              }, ((_d = dish.steps) == null ? void 0 : _d.length) ? {
                f: common_vendor.f(dish.steps, (step, stepIndex, i2) => {
                  return {
                    a: common_vendor.t(stepIndex + 1),
                    b: common_vendor.t(step),
                    c: step
                  };
                })
              } : {}, {
                g: `${dish.name}-${dishIndex}`
              });
            })
          } : {}, {
            z: common_vendor.f(dishesOf(day), (dish, dishIndex, i1) => {
              return common_vendor.e({
                a: common_vendor.t(dishIndex + 1),
                b: common_vendor.t(dish.name),
                c: common_vendor.o(($event) => onOutcomeChange(index, dishIndex, dish.name, $event), `fb-${dish.name}-${dishIndex}`),
                d: "5d49e1be-4-" + i0 + "-" + i1 + ",5d49e1be-0",
                e: common_vendor.p({
                  ["model-value"]: outcomes.value[feedbackKey(index, dishIndex)],
                  options: common_vendor.unref(outcomeChipOptions),
                  columns: 2,
                  ["aria-label"]: "这道菜做得怎么样"
                }),
                f: outcomes.value[feedbackKey(index, dishIndex)] === "DONE" && !moodAfters.value[feedbackKey(index, dishIndex)]
              }, outcomes.value[feedbackKey(index, dishIndex)] === "DONE" && !moodAfters.value[feedbackKey(index, dishIndex)] ? {
                g: common_vendor.o(($event) => reportMood(index, dishIndex, dish.name, $event), `fb-${dish.name}-${dishIndex}`),
                h: "5d49e1be-5-" + i0 + "-" + i1 + ",5d49e1be-0",
                i: common_vendor.p({
                  options: common_vendor.unref(postMealMoodOptions),
                  columns: 4,
                  ["aria-label"]: "吃完心情"
                })
              } : moodAfters.value[feedbackKey(index, dishIndex)] ? {
                k: common_vendor.t(common_vendor.unref(utils_albumLayout.MOOD_EMOJI)[moodAfters.value[feedbackKey(index, dishIndex)]]),
                l: common_vendor.t(moodAfters.value[feedbackKey(index, dishIndex)])
              } : {}, {
                j: moodAfters.value[feedbackKey(index, dishIndex)],
                m: `fb-${dish.name}-${dishIndex}`
              });
            }),
            A: common_vendor.o(($event) => record(day.dishName), `${day.day}-${index}`),
            B: "5d49e1be-6-" + i0 + ",5d49e1be-0",
            C: activeDay.value === index ? 1 : "",
            D: `${day.day}-${index}`
          });
        }),
        r: swapping.value >= 0,
        s: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`,
        t: sharingDay.value >= 0 ? 1 : "",
        v: sharingDay.value >= 0,
        w: common_vendor.p({
          variant: "primary",
          ["aria-label"]: "把这一桌记进时光机"
        }),
        x: activeDay.value,
        y: common_vendor.o(onDayChange),
        z: common_vendor.t(plan.value.shopping.length),
        A: shoppingOpen.value ? 1 : "",
        B: shoppingOpen.value,
        C: common_vendor.o(($event) => shoppingOpen.value = !shoppingOpen.value),
        D: shoppingOpen.value
      }, shoppingOpen.value ? {
        E: common_vendor.f(groups.value, (group, k0, i0) => {
          return {
            a: common_vendor.t(group.category),
            b: common_vendor.f(group.items, (item, k1, i1) => {
              return {
                a: common_vendor.t(item.purchased ? "✓" : "○"),
                b: item.purchased ? 1 : "",
                c: common_vendor.t(item.name),
                d: item.purchased ? 1 : "",
                e: common_vendor.t(item.quantity),
                f: item.name,
                g: item.purchased,
                h: common_vendor.o(($event) => toggle(item.name), item.name)
              };
            }),
            c: group.category
          };
        }),
        F: common_vendor.o(copyShoppingList),
        G: common_vendor.o(buyAllIngredients)
      } : {}) : {}, {
        d: error.value,
        g: plan.value
      });
    };
  }
});
_sfc_defineComponent.__runtimeHooks = 6;
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_defineComponent, [["__scopeId", "data-v-5d49e1be"]]);
exports.MiniProgramPage = MiniProgramPage;
