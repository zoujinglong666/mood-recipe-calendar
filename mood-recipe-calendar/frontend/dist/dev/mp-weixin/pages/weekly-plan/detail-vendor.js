"use strict";
const common_vendor = require("../../common/vendor.js");
const api_request = require("../../api/request.js");
const api_weeklyPlans = require("../../api/weeklyPlans.js");
const composables_useNavBar = require("../../composables/useNavBar.js");
const utils_albumShare = require("../../utils/albumShare.js");
const utils_assets = require("../../utils/assets.js");
const utils_toast = require("../../utils/toast.js");
const __unplugin_components_3 = () => "../../node-modules/@wot-ui/ui/components/wd-image-preview/wd-image-preview.js";
const __unplugin_components_2 = () => "../../node-modules/@wot-ui/ui/components/wd-radio-group/wd-radio-group.js";
const __unplugin_components_1 = () => "../../node-modules/@wot-ui/ui/components/wd-radio/wd-radio.js";
const __unplugin_components_0 = () => "../../node-modules/@wot-ui/ui/components/wd-navbar/wd-navbar.js";
if (!Array) {
  const _component_wd_navbar = __unplugin_components_0;
  const _component_wd_radio = __unplugin_components_1;
  const _component_wd_radio_group = __unplugin_components_2;
  const _component_wd_image_preview = __unplugin_components_3;
  const _component_layout_default_uni = common_vendor.resolveComponent("layout-default-uni");
  (_component_wd_navbar + _component_wd_radio + _component_wd_radio_group + _component_wd_image_preview + _component_layout_default_uni)();
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "detail",
  setup(__props) {
    const router = common_vendor.useRouter();
    const route = common_vendor.useRoute();
    const { previewImage } = common_vendor.useImagePreview();
    const plan = common_vendor.ref();
    const loading = common_vendor.ref(true);
    const swapping = common_vendor.ref(-1);
    const activeDay = common_vendor.ref(0);
    const shoppingOpen = common_vendor.ref(false);
    const detailsOpen = common_vendor.ref({});
    const coverLoading = common_vendor.ref({});
    const activeDish = common_vendor.ref({});
    const sharingDay = common_vendor.ref(-1);
    let coverQueue = Promise.resolve();
    const weekdayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
    const outcomeOptions = [
      { key: "DONE", label: "做完了" },
      { key: "LEFTOVER", label: "有剩菜" },
      { key: "HARD", label: "有点难" },
      { key: "SKIP", label: "没做成" }
    ];
    const outcomeMap = {
      DONE: { cooked: true, leftover: false, tooHard: false },
      LEFTOVER: { cooked: true, leftover: true, tooHard: false },
      HARD: { cooked: false, leftover: false, tooHard: true },
      SKIP: { cooked: false, leftover: false, tooHard: false }
    };
    const outcomes = common_vendor.ref({});
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
        closeOnClick: false,
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
      } catch (error) {
        const rollback = { ...outcomes.value };
        delete rollback[key];
        outcomes.value = rollback;
        utils_toast.toastError(error, "反馈没记上，稍后再试");
      }
    }
    function onOutcomeChange(dayIndex, dishIndex, dishName, event) {
      const kind = String(event.value);
      if (kind in outcomeMap)
        void reportOutcome(dayIndex, dishIndex, dishName, kind);
    }
    async function load() {
      loading.value = true;
      try {
        const id = Number(route.query.id);
        plan.value = Number.isFinite(id) && id > 0 ? await api_weeklyPlans.getWeeklyPlan(id) : await api_weeklyPlans.getCurrentPlan();
        activeDay.value = 0;
        queueCovers(0);
      } catch (error) {
        utils_toast.toastError(error, "还没有备餐计划");
        router.back();
      } finally {
        loading.value = false;
      }
    }
    common_vendor.onShow(load);
    function queueCovers(dayIndex) {
      coverQueue = coverQueue.then(async () => {
        var _a;
        const day = (_a = plan.value) == null ? void 0 : _a.days[dayIndex];
        if (!day)
          return;
        for (let dishIndex = 0; dishIndex < dishesOf(day).length; dishIndex++)
          await ensureDishCover(dayIndex, dishIndex);
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
      } catch (error) {
        utils_toast.toastError(error, "换菜失败，请重试");
      } finally {
        swapping.value = -1;
      }
    }
    async function toggle(name) {
      if (!plan.value)
        return;
      try {
        plan.value = await api_weeklyPlans.toggleShoppingItem(plan.value.id, name);
      } catch (error) {
        utils_toast.toastError(error, "清单更新失败");
      }
    }
    function record(dish) {
      common_vendor.index.setStorageSync("mrc_record_draft", { dish, mood: "满足" });
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
          mood: "满足",
          reason: day.healthTip,
          ingredients: dish.ingredients,
          steps: dish.steps,
          image: api_request.resolveAssetUrl(coverOf(day, dishIndex)),
          guozaiPath: `${utils_assets.STATIC_BASE_URL}/static/guozai/action_16_chopsticks.png`,
          style: "guozai"
        }, "weeklyRecipeShareCanvas");
        await utils_albumShare.saveShareImage(path, `${dish.name}-锅仔食谱卡.png`);
        utils_toast.toastSuccess("食谱卡已保存，可以分享给朋友");
      } catch (error) {
        utils_toast.toastError(error, "食谱卡生成失败，请重试");
      } finally {
        sharingDay.value = -1;
      }
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.o(common_vendor.unref(composables_useNavBar.navBack)),
        b: common_vendor.p({
          title: "这一周的晚餐",
          ["left-arrow"]: true,
          ["safe-area-inset-top"]: true,
          ["custom-style"]: "background-color: transparent !important;"
        }),
        c: loading.value
      }, loading.value ? {} : plan.value ? common_vendor.e({
        e: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_09_celebrate.png`,
        f: common_vendor.f(plan.value.days, (day, index, i0) => {
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
        g: common_vendor.t(weekday(plan.value.days[activeDay.value], activeDay.value)),
        h: common_vendor.t(activeDay.value + 1),
        i: common_vendor.t(plan.value.days.length),
        j: plan.value.agent
      }, plan.value.agent ? common_vendor.e({
        k: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_06_glasses.png`,
        l: common_vendor.t(agentCheckCopy.value),
        m: (_a = plan.value.agent.degradeReasons) == null ? void 0 : _a.length
      }, ((_b = plan.value.agent.degradeReasons) == null ? void 0 : _b.length) ? {} : {}) : {}, {
        n: common_vendor.f(plan.value.days, (day, index, i0) => {
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
                b: common_vendor.unref(api_request.resolveAssetUrl)(coverOf(day, dishIndex)),
                c: `预览${dish.name}菜品大图`,
                d: common_vendor.o(($event) => previewDishImages(day, dishIndex), `${dish.name}-${dishIndex}`)
              } : {
                e: common_vendor.t(coverLoading.value[coverKey(index, dishIndex)] ? "锅仔正在画这道菜…" : "这道菜的照片正在路上")
              }, {
                f: coverLoading.value[coverKey(index, dishIndex)]
              }, coverLoading.value[coverKey(index, dishIndex)] ? {} : {}, dishesOf(day).length > 1 ? {
                g: common_vendor.t(dishIndex + 1),
                h: common_vendor.t(dishesOf(day).length)
              } : {}, {
                i: coverLoading.value[coverKey(index, dishIndex)] ? 1 : "",
                j: `${dish.name}-${dishIndex}`
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
              return {
                a: common_vendor.t(dishIndex + 1),
                b: common_vendor.t(dish.name),
                c: common_vendor.f(dish.ingredients, (item, k2, i2) => {
                  return {
                    a: common_vendor.t(item),
                    b: item
                  };
                }),
                d: common_vendor.f(dish.steps, (step, stepIndex, i2) => {
                  return {
                    a: common_vendor.t(stepIndex + 1),
                    b: common_vendor.t(step),
                    c: step
                  };
                }),
                e: `${dish.name}-${dishIndex}`
              };
            })
          } : {}, {
            z: common_vendor.f(dishesOf(day), (dish, dishIndex, i1) => {
              return {
                a: common_vendor.t(dishIndex + 1),
                b: common_vendor.t(dish.name),
                c: common_vendor.f(outcomeOptions, (option, k2, i2) => {
                  return {
                    a: common_vendor.t(option.label),
                    b: option.key,
                    c: "5d49e1be-3-" + i0 + "-" + i1 + "-" + i2 + "," + ("5d49e1be-2-" + i0 + "-" + i1),
                    d: common_vendor.p({
                      value: option.key
                    })
                  };
                }),
                d: common_vendor.o(($event) => onOutcomeChange(index, dishIndex, dish.name, $event), `fb-${dish.name}-${dishIndex}`),
                e: "5d49e1be-2-" + i0 + "-" + i1 + ",5d49e1be-0",
                f: common_vendor.p({
                  ["custom-class"]: "outcome-selector",
                  type: "button",
                  direction: "horizontal",
                  ["model-value"]: outcomes.value[feedbackKey(index, dishIndex)]
                }),
                g: `fb-${dish.name}-${dishIndex}`
              };
            }),
            A: `把${day.dishName}记进时光机`,
            B: common_vendor.o(($event) => record(day.dishName), `${day.day}-${index}`),
            C: activeDay.value === index ? 1 : "",
            D: `${day.day}-${index}`
          });
        }),
        o: swapping.value >= 0,
        p: `${common_vendor.unref(utils_assets.STATIC_BASE_URL)}/static/guozai/action_16_chopsticks.png`,
        q: sharingDay.value >= 0 ? 1 : "",
        r: sharingDay.value >= 0,
        s: activeDay.value,
        t: common_vendor.o(onDayChange),
        v: common_vendor.t(plan.value.shopping.length),
        w: shoppingOpen.value ? 1 : "",
        x: shoppingOpen.value,
        y: common_vendor.o(($event) => shoppingOpen.value = !shoppingOpen.value),
        z: shoppingOpen.value
      }, shoppingOpen.value ? {
        A: common_vendor.f(groups.value, (group, k0, i0) => {
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
        })
      } : {}) : {}, {
        d: plan.value
      });
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-5d49e1be"]]);
exports.MiniProgramPage = MiniProgramPage;
