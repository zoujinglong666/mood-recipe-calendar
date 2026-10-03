// Generated advisory implementation starter. Copy only reviewed business logic into src/local-rpa.js.
// It is never installed as the project entry and cannot pass delivery until semantic assertions are implemented.
// IMPORTANT: api exposes documented Puppeteer-like Local Locator calls; it is not the full Puppeteer or Playwright API.
// Exact Local API signatures used by this file:
// - api.step(name: string, action: () => Promise<T> | T, options?: { evidence?: boolean }): Promise<T>
// - api.navigate(url: string, timeoutMs?: number): Promise<PageSummary>
// - api.locator(selector: string, options?: LocalLocatorOptions): LocalLocator
// Read only a method's section in references/local-rpa-api.md when adding or changing that call.
// Filtered 0 non-business or redundant recorded action(s).
// Output evidence coverage: {"ready": false, "missing_required_fields": ["topic_name", "page_url", "page_title", "processing_status"]}

// Step 0: 打开页面 `https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN`
// Selector preflight: BLOCKED — No executable CSS selector was captured.
// Recording evidence:
// {
//   "recorded_step_index": 0,
//   "raw_event_ids": [
//     2
//   ],
//   "action": "navigate",
//   "editable_kind": null,
//   "input_type": null,
//   "parameter": null,
//   "selector": null,
//   "selector_assessment": {
//     "grade": "BLOCKED",
//     "reason": "No executable CSS selector was captured.",
//     "risks": [
//       "css_selector_missing"
//     ]
//   },
//   "selector_candidates": [],
//   "element": {},
//   "dom_delta": null,
//   "postcondition": {
//     "expected": {
//       "ready_state": "interactive_or_complete",
//       "url": "https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN"
//     },
//     "kind": "navigation_ready",
//     "source": "recorded_step"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-6f27a5bddfcf2d71a2059255fa1afee0e817a722a123492a2868d75f2432a743.json",
//   "screenshot": "screenshots/step-000002-document-tab-1.jpg"
// }
await api.step("打开页面 `https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN`", async () => {
  await api.navigate("https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN");
});

// Step 1: 在 `今天也要好好吃饭` 中输入录制文本（input）
// Selector preflight: CONDITIONAL — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 1,
//   "raw_event_ids": [
//     3
//   ],
//   "action": "change",
//   "editable_kind": "input",
//   "input_type": "insertText",
//   "parameter": "topic_name",
//   "selector": "input.weui-desktop-form__input",
//   "selector_assessment": {
//     "grade": "CONDITIONAL",
//     "reason": "Recorded evidence supports this selector plan.",
//     "risks": [
//       "scoped_disambiguation_required",
//       "semantic_identity_required"
//     ],
//     "scope": "div.topic-form",
//     "semantic_identity": "今天也要好好吃饭",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 50.0,
//         "selector": "input.weui-desktop-form__input",
//         "stability": "semantic_classes",
//         "unique": false
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 45.0,
//         "selector": "div.topic-form",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "[data-doubao-translate-traverse-mark=\"\\31 \"]",
//         "stability": "data_attribute",
//         "unique": false
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "span.weui-desktop-form__input-wrp.weui-desktop-form__input_append-in.weui-desktop-form__input_counter",
//         "stability": "semantic_classes",
//         "unique": false
//       },
//       {
//         "relation": "actionable_target",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_unique"
//         ],
//         "score": 38.0,
//         "selector": "[data-doubao-translate-traverse-mark=\"\\31 \"]",
//         "stability": "data_attribute",
//         "unique": false
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.55,
//       "match_count": 163,
//       "recommended": true,
//       "stability": "data_attribute",
//       "type": "css",
//       "unique": false,
//       "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//     },
//     {
//       "confidence": 0.55,
//       "match_count": 3,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": false,
//       "value": "input.weui-desktop-form__input"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 5,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "input"
//     }
//   ],
//   "element": {
//     "tag": "input",
//     "text": "今天也要好好吃饭",
//     "classes": [
//       "weui-desktop-form__input"
//     ],
//     "attributes": {
//       "data-doubao-translate-traverse-mark": "1",
//       "name": "",
//       "placeholder": "输入话题名称，发布后不可修改",
//       "type": "text"
//     },
//     "rect": {
//       "height": 36,
//       "width": 508,
//       "x": 643,
//       "y": 204
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0.0218
//     }
//   },
//   "dom_delta": {
//     "added": [
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "title": "微信公众平台 小程序"
//         },
//         "classes": [
//           "header_logo"
//         ],
//         "rect": {
//           "height": 40,
//           "width": 120,
//           "x": 32,
//           "y": 18
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.header_logo"
//           }
//         ],
//         "tag": "a"
//       },
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1"
//         },
//         "classes": [
//           "header_notify"
//         ],
//         "rect": {
//           "height": 24,
//           "width": 24,
//           "x": 1152,
//           "y": 26
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.header_notify"
//           }
//         ],
//         "tag": "a",
//         "text": "23"
//       },
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1"
//         },
//         "classes": [
//           "weui-desktop-upload__img__btn"
//         ],
//         "rect": {
//           "height": 112,
//           "width": 112,
//           "x": 643,
//           "y": 302
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.weui-desktop-upload__img__btn"
//           }
//         ],
//         "tag": "a",
//         "text": "上传"
//       },
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "type": "button"
//         },
//         "classes": [
//           "weui-desktop-btn",
//           "weui-desktop-btn_primary",
//           "weui-desktop-btn_disabled"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 96,
//           "x": 799,
//           "y": 932
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "button.weui-desktop-btn.weui-desktop-btn_primary.weui-desktop-btn_disabled"
//           }
//         ],
//         "tag": "button",
//         "text": "发布"
//       },
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "placeholder": "输入话题名称，发布后不可修改",
//           "type": "text"
//         },
//         "classes": [
//           "weui-desktop-form__input"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 508,
//           "x": 643,
//           "y": 204
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.55,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": false,
//             "value": "input.weui-desktop-form__input"
//           }
//         ],
//         "tag": "input",
//         "text": "今天也要好好吃饭"
//       },
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "placeholder": "输入参与话题的规则，如分享内容的要求、可获得的活动激励和领奖方式"
//         },
//         "classes": [
//           "weui-desktop-form__textarea"
//         ],
//         "rect": {
//           "height": 80,
//           "width": 508,
//           "x": 643,
//           "y": 446
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "textarea.weui-desktop-form__textarea"
//           }
//         ],
//         "tag": "textarea"
//       }
//     ],
//     "added_count": 6,
//     "removed": [
//       {
//         "attributes": {
//           "title": "微信公众平台 小程序"
//         },
//         "classes": [
//           "header_logo"
//         ],
//         "rect": {
//           "height": 40,
//           "width": 120,
//           "x": 32,
//           "y": 18
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.82,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.header_logo"
//           }
//         ],
//         "tag": "a"
//       },
//       {
//         "classes": [
//           "header_notify"
//         ],
//         "rect": {
//           "height": 24,
//           "width": 24,
//           "x": 1152,
//           "y": 26
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.82,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.header_notify"
//           }
//         ],
//         "tag": "a",
//         "text": "23"
//       },
//       {
//         "classes": [
//           "weui-desktop-upload__img__btn"
//         ],
//         "rect": {
//           "height": 112,
//           "width": 112,
//           "x": 643,
//           "y": 302
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.82,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.weui-desktop-upload__img__btn"
//           }
//         ],
//         "tag": "a",
//         "text": "上传"
//       },
//       {
//         "attributes": {
//           "type": "button"
//         },
//         "classes": [
//           "weui-desktop-btn",
//           "weui-desktop-btn_primary",
//           "weui-desktop-btn_disabled"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 96,
//           "x": 799,
//           "y": 932
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.82,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "button.weui-desktop-btn.weui-desktop-btn_primary.weui-desktop-btn_disabled"
//           }
//         ],
//         "tag": "button",
//         "text": "发布"
//       },
//       {
//         "attributes": {
//           "placeholder": "输入参与话题的规则，如分享内容的要求、可获得的活动激励和领奖方式"
//         },
//         "classes": [
//           "weui-desktop-form__textarea"
//         ],
//         "rect": {
//           "height": 80,
//           "width": 508,
//           "x": 643,
//           "y": 446
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.82,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "textarea.weui-desktop-form__textarea"
//           }
//         ],
//         "tag": "textarea"
//       }
//     ],
//     "removed_count": 5
//   },
//   "postcondition": {
//     "expected": "recorded parameter value is present in the target control",
//     "kind": "input_value_applied",
//     "source": "recorded_step"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-822ec40217d6701fd9007dc679cb01d5ac0193de61dbc9be0f7d1fa442de8188.json",
//   "screenshot": "screenshots/step-000003-change-tab-1.jpg"
// }
await api.step("在 `今天也要好好吃饭` 中输入录制文本（input）", async () => {
  const selector = "input.weui-desktop-form__input";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "div.topic-form", "text": "今天也要好好吃饭", "exactText": false, "near": {"x": 897.0, "y": 222.0}});
  const value = String(input["topic_name"] ?? '').trim();
  if (!value) throw api.businessError('RECORDED_INPUT_REQUIRED', '录制输入参数不能为空', { selector });
  await target.type(value); // Recording appended text; preserve existing content.
  // Recorded rect: x=643, y=204, width=508, height=36
});

// Step 2: 点击 `录制目标控件`
// Selector preflight: CONDITIONAL — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 2,
//   "raw_event_ids": [
//     4
//   ],
//   "action": "click",
//   "editable_kind": null,
//   "input_type": null,
//   "parameter": null,
//   "selector": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//   "selector_assessment": {
//     "grade": "CONDITIONAL",
//     "reason": "Recorded evidence supports this selector plan.",
//     "risks": [
//       "non_actionable_target"
//     ],
//     "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//     "alternatives": [
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 41.0,
//         "selector": "a.weui-desktop-upload__img__btn",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 33.0,
//         "selector": "div.weui-desktop-upload__imgs__wrp",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [],
//         "score": 29.0,
//         "selector": "label",
//         "stability": "positional_fallback",
//         "unique": true
//       },
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 25.0,
//         "selector": "label",
//         "stability": "positional_fallback",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 25.0,
//         "selector": "div.weui-desktop-upload",
//         "stability": "semantic_classes",
//         "unique": true
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.25,
//       "match_count": 1,
//       "recommended": true,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": true,
//       "value": "label"
//     }
//   ],
//   "element": {
//     "tag": "label",
//     "rect": {
//       "height": 85,
//       "width": 85,
//       "x": 656,
//       "y": 315
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0.0086
//     }
//   },
//   "dom_delta": {
//     "added": [],
//     "added_count": 0,
//     "removed": [],
//     "removed_count": 0
//   },
//   "postcondition": {
//     "expected": {
//       "dom_delta": {
//         "added": [],
//         "added_count": 0,
//         "removed": [],
//         "removed_count": 0
//       },
//       "ready_state": "complete",
//       "title": "小程序",
//       "url": "https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN"
//     },
//     "kind": "recorded_page_state",
//     "source": "post_action_evidence"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-0d812da7a55af676708ac5a8a09bee50f748c81c3b2770c8729a395c1212e4b5.json",
//   "screenshot": "screenshots/step-000004-click-tab-1.jpg"
// }
await api.step("点击 `录制目标控件`", async () => {
  const selector = "#rt_rt_1k3ukus3f1ck630kthf1nerngp1";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1", "near": {"x": 698.5, "y": 357.5}, "index": 0});
  await target.click();
  // Recorded rect: x=656, y=315, width=85, height=85
});

// Step 3: 点击 `file`
// Selector preflight: STABLE — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 3,
//   "raw_event_ids": [
//     5
//   ],
//   "action": "click",
//   "editable_kind": null,
//   "input_type": null,
//   "parameter": null,
//   "selector": "input[name=\"file\"]",
//   "selector_assessment": {
//     "grade": "STABLE",
//     "reason": "Recorded evidence supports this selector plan.",
//     "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//     "semantic_identity": "file",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 97.0,
//         "selector": "input[name=\"file\"]",
//         "stability": "name_attribute",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [],
//         "score": 89.0,
//         "selector": "input.webuploader-element-invisible",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 87.0,
//         "selector": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//         "stability": "id",
//         "unique": true
//       },
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 85.0,
//         "selector": "input.webuploader-element-invisible",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 41.0,
//         "selector": "a.weui-desktop-upload__img__btn",
//         "stability": "semantic_classes",
//         "unique": true
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.88,
//       "match_count": 1,
//       "recommended": true,
//       "stability": "name_attribute",
//       "type": "css",
//       "unique": true,
//       "value": "input[name=\"file\"]"
//     },
//     {
//       "confidence": 0.82,
//       "match_count": 1,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": true,
//       "value": "input.webuploader-element-invisible"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 5,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "input"
//     }
//   ],
//   "element": {
//     "tag": "input",
//     "name": "file",
//     "classes": [
//       "webuploader-element-invisible"
//     ],
//     "attributes": {
//       "name": "file",
//       "type": "file"
//     },
//     "rect": {
//       "height": 0,
//       "width": 0,
//       "x": 0,
//       "y": 0
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0
//     }
//   },
//   "dom_delta": {
//     "added": [],
//     "added_count": 0,
//     "removed": [],
//     "removed_count": 0
//   },
//   "postcondition": {
//     "expected": {
//       "dom_delta": {
//         "added": [],
//         "added_count": 0,
//         "removed": [],
//         "removed_count": 0
//       },
//       "ready_state": "complete",
//       "title": "小程序",
//       "url": "https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN"
//     },
//     "kind": "recorded_page_state",
//     "source": "post_action_evidence"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-0d812da7a55af676708ac5a8a09bee50f748c81c3b2770c8729a395c1212e4b5.json",
//   "screenshot": "screenshots/step-000005-click-tab-1.jpg"
// }
await api.step("点击 `file`", async () => {
  const selector = "input[name=\"file\"]";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1", "text": "file", "exactText": false, "near": {"x": 0.0, "y": 0.0}});
  await target.click();
  // Recorded rect: x=0, y=0, width=0, height=0
});

// Step 4: 在 `file` 中输入录制文本（input）
// Selector preflight: STABLE — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 4,
//   "raw_event_ids": [
//     6
//   ],
//   "action": "change",
//   "editable_kind": "input",
//   "input_type": null,
//   "parameter": "topic_bg_image",
//   "selector": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//   "selector_assessment": {
//     "grade": "STABLE",
//     "reason": "Recorded evidence supports this selector plan.",
//     "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1",
//     "semantic_identity": "file",
//     "alternatives": [
//       {
//         "relation": "recorded_target",
//         "risks": [],
//         "score": 101.0,
//         "selector": "input[name=\"file\"]",
//         "stability": "name_attribute",
//         "unique": true
//       },
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 97.0,
//         "selector": "input[name=\"file\"]",
//         "stability": "name_attribute",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [],
//         "score": 89.0,
//         "selector": "input.webuploader-element-invisible",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 85.0,
//         "selector": "input.webuploader-element-invisible",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 61.0,
//         "selector": "a.weui-desktop-upload__img__btn",
//         "stability": "semantic_classes",
//         "unique": true
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.88,
//       "match_count": 1,
//       "recommended": true,
//       "stability": "name_attribute",
//       "type": "css",
//       "unique": true,
//       "value": "input[name=\"file\"]"
//     },
//     {
//       "confidence": 0.82,
//       "match_count": 1,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": true,
//       "value": "input.webuploader-element-invisible"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 5,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "input"
//     }
//   ],
//   "element": {
//     "tag": "input",
//     "name": "file",
//     "text": "C:\\fakepath\\topic-bg-4x3.png",
//     "classes": [
//       "webuploader-element-invisible"
//     ],
//     "attributes": {
//       "name": "file",
//       "type": "file"
//     },
//     "rect": {
//       "height": 0,
//       "width": 0,
//       "x": 0,
//       "y": 0
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0
//     }
//   },
//   "dom_delta": {
//     "added": [],
//     "added_count": 0,
//     "removed": [
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1"
//         },
//         "classes": [
//           "weui-desktop-upload__img__btn"
//         ],
//         "rect": {
//           "height": 112,
//           "width": 112,
//           "x": 643,
//           "y": 302
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "a.weui-desktop-upload__img__btn"
//           }
//         ],
//         "tag": "a",
//         "text": "上传"
//       }
//     ],
//     "removed_count": 1
//   },
//   "postcondition": {
//     "expected": "recorded parameter value is present in the target control",
//     "kind": "input_value_applied",
//     "source": "recorded_step"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-6910a4649910ee6a3a8672fdca23b5bcf8928f873e8e618f46bc52af80997c42.json",
//   "screenshot": "screenshots/step-000006-change-tab-1.jpg"
// }
await api.step("在 `file` 中输入录制文本（input）", async () => {
  const selector = "#rt_rt_1k3ukus3f1ck630kthf1nerngp1";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "#rt_rt_1k3ukus3f1ck630kthf1nerngp1", "text": "file", "exactText": false, "near": {"x": 0.0, "y": 0.0}});
  const value = String(input["topic_bg_image"] ?? '').trim();
  if (!value) throw api.businessError('RECORDED_INPUT_REQUIRED', '录制输入参数不能为空', { selector });
  await target.type(value); // Recording appended text; preserve existing content.
  // Recorded rect: x=0, y=0, width=0, height=0
});

// Step 5: 在 `选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！` 中输入录制文本（textarea）
// Selector preflight: STABLE — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 5,
//   "raw_event_ids": [
//     7
//   ],
//   "action": "change",
//   "editable_kind": "textarea",
//   "input_type": "insertText",
//   "parameter": "topic_description",
//   "selector": "textarea.weui-desktop-form__textarea",
//   "selector_assessment": {
//     "grade": "STABLE",
//     "reason": "Recorded evidence supports this selector plan.",
//     "scope": "span.weui-desktop-form__input-wrp.weui-desktop-form__input_append-in.weui-desktop-form__input_textarea.weui-desktop-form__input_counter",
//     "semantic_identity": "选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 85.0,
//         "selector": "textarea.weui-desktop-form__textarea",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 77.0,
//         "selector": "span.weui-desktop-form__input-wrp.weui-desktop-form__input_append-in.weui-desktop-form__input_textarea.weui-desktop-form__input_counter",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 69.0,
//         "selector": "div.topic-form__input.topic-form__input--desc.weui-desktop-form__input-area",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 45.0,
//         "selector": "div.topic-form",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "[data-doubao-translate-traverse-mark=\"\\31 \"]",
//         "stability": "data_attribute",
//         "unique": false
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.55,
//       "match_count": 154,
//       "recommended": true,
//       "stability": "data_attribute",
//       "type": "css",
//       "unique": false,
//       "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//     },
//     {
//       "confidence": 0.82,
//       "match_count": 1,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": true,
//       "value": "textarea.weui-desktop-form__textarea"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 1,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": true,
//       "value": "textarea"
//     }
//   ],
//   "element": {
//     "tag": "textarea",
//     "text": "选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！",
//     "classes": [
//       "weui-desktop-form__textarea"
//     ],
//     "attributes": {
//       "data-doubao-translate-traverse-mark": "1",
//       "name": "",
//       "placeholder": "输入参与话题的规则，如分享内容的要求、可获得的活动激励和领奖方式"
//     },
//     "rect": {
//       "height": 80,
//       "width": 508,
//       "x": 643,
//       "y": 453.3984375
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": true,
//       "viewport_area_ratio": 0.0485
//     }
//   },
//   "dom_delta": {
//     "added": [
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "placeholder": "输入参与话题的规则，如分享内容的要求、可获得的活动激励和领奖方式"
//         },
//         "classes": [
//           "weui-desktop-form__textarea"
//         ],
//         "rect": {
//           "height": 80,
//           "width": 508,
//           "x": 643,
//           "y": 453.3984375
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "textarea.weui-desktop-form__textarea"
//           }
//         ],
//         "tag": "textarea",
//         "text": "选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！"
//       }
//     ],
//     "added_count": 1,
//     "removed": [
//       {
//         "attributes": {
//           "data-doubao-translate-traverse-mark": "1",
//           "placeholder": "输入参与话题的规则，如分享内容的要求、可获得的活动激励和领奖方式"
//         },
//         "classes": [
//           "weui-desktop-form__textarea"
//         ],
//         "rect": {
//           "height": 80,
//           "width": 508,
//           "x": 643,
//           "y": 454
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "data_attribute",
//             "type": "css",
//             "unique": false,
//             "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//           },
//           {
//             "confidence": 0.82,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": true,
//             "value": "textarea.weui-desktop-form__textarea"
//           }
//         ],
//         "tag": "textarea"
//       }
//     ],
//     "removed_count": 1
//   },
//   "postcondition": {
//     "expected": "recorded parameter value is present in the target control",
//     "kind": "input_value_applied",
//     "source": "recorded_step"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-f0e0544e4b019373feb6e886022fbef54fbff68f2251ef8a27395bb71e83f804.json",
//   "screenshot": "screenshots/step-000007-change-tab-1.jpg"
// }
await api.step("在 `选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！` 中输入录制文本（textarea）", async () => {
  const selector = "textarea.weui-desktop-form__textarea";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "span.weui-desktop-form__input-wrp.weui-desktop-form__input_append-in.weui-desktop-form__input_textarea.weui-desktop-form__input_counter", "text": "选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！", "exactText": false, "near": {"x": 897.0, "y": 493.4}});
  const value = String(input["topic_description"] ?? '').trim();
  if (!value) throw api.businessError('RECORDED_INPUT_REQUIRED', '录制输入参数不能为空', { selector });
  await target.type(value); // Recording appended text; preserve existing content.
  // Recorded rect: x=643, y=453.3984375, width=508, height=80
});

// Step 6: 在 `记录今日伙食` 中输入录制文本（input）
// Selector preflight: CONDITIONAL — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 6,
//   "raw_event_ids": [
//     8,
//     9
//   ],
//   "action": "change",
//   "editable_kind": "input",
//   "input_type": "insertText",
//   "parameter": "link_title",
//   "selector": "input.weui-desktop-form__input",
//   "selector_assessment": {
//     "grade": "CONDITIONAL",
//     "reason": "Recorded evidence supports this selector plan.",
//     "risks": [
//       "scoped_disambiguation_required",
//       "semantic_identity_required"
//     ],
//     "scope": "div.topic-form",
//     "semantic_identity": "记录今日伙食",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 50.0,
//         "selector": "input.weui-desktop-form__input",
//         "stability": "semantic_classes",
//         "unique": false
//       },
//       {
//         "relation": "ancestor",
//         "risks": [],
//         "score": 45.0,
//         "selector": "div.topic-form",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "span.weui-desktop-form__input-wrp",
//         "stability": "semantic_classes",
//         "unique": false
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 34.0,
//         "selector": "div.topic-form__input.weui-desktop-form__input-area",
//         "stability": "semantic_classes",
//         "unique": false
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_unique"
//         ],
//         "score": 26.0,
//         "selector": "div.topic-form__control",
//         "stability": "semantic_classes",
//         "unique": false
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.55,
//       "match_count": 3,
//       "recommended": true,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": false,
//       "value": "input.weui-desktop-form__input"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 4,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "input"
//     }
//   ],
//   "element": {
//     "tag": "input",
//     "text": "记录今日伙食",
//     "classes": [
//       "weui-desktop-form__input"
//     ],
//     "attributes": {
//       "name": "",
//       "placeholder": "请输入推荐链接的标题，便于作者和读者理解链接内容",
//       "type": "text"
//     },
//     "rect": {
//       "height": 36,
//       "width": 492,
//       "x": 659,
//       "y": 617.8984375
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0.0212
//     }
//   },
//   "dom_delta": {
//     "added": [
//       {
//         "attributes": {
//           "placeholder": "请填写用户参与话题时推荐添加的链接路径，仅支持当前小程序内的页面路径",
//           "type": "text"
//         },
//         "classes": [
//           "weui-desktop-form__input"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 492,
//           "x": 659,
//           "y": 549.8984375
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": false,
//             "value": "input.weui-desktop-form__input"
//           }
//         ],
//         "tag": "input",
//         "text": "pages/record/index"
//       },
//       {
//         "attributes": {
//           "placeholder": "请输入推荐链接的标题，便于作者和读者理解链接内容",
//           "type": "text"
//         },
//         "classes": [
//           "weui-desktop-form__input"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 492,
//           "x": 659,
//           "y": 617.8984375
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": false,
//             "value": "input.weui-desktop-form__input"
//           }
//         ],
//         "tag": "input",
//         "text": "记录今日伙食"
//       }
//     ],
//     "added_count": 2,
//     "removed": [
//       {
//         "attributes": {
//           "placeholder": "请输入推荐链接的标题，便于作者和读者理解链接内容",
//           "type": "text"
//         },
//         "classes": [
//           "weui-desktop-form__input"
//         ],
//         "rect": {
//           "height": 36,
//           "width": 492,
//           "x": 659,
//           "y": 633.3984375
//         },
//         "selector_candidates": [
//           {
//             "confidence": 0.55,
//             "recommended": true,
//             "stability": "semantic_classes",
//             "type": "css",
//             "unique": false,
//             "value": "input.weui-desktop-form__input"
//           }
//         ],
//         "tag": "input"
//       }
//     ],
//     "removed_count": 1
//   },
//   "postcondition": {
//     "expected": "recorded parameter value is present in the target control",
//     "kind": "input_value_applied",
//     "source": "recorded_step"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-480255e2588fa865326ea53257dd8277058360786c66fa21391d43dd91d1f9e7.json",
//   "screenshot": "screenshots/step-000009-change-tab-1.jpg"
// }
await api.step("在 `记录今日伙食` 中输入录制文本（input）", async () => {
  const selector = "input.weui-desktop-form__input";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "div.topic-form", "text": "记录今日伙食", "exactText": false, "near": {"x": 905.0, "y": 635.9}});
  const value = String(input["link_title"] ?? '').trim();
  if (!value) throw api.businessError('RECORDED_INPUT_REQUIRED', '录制输入参数不能为空', { selector });
  await target.type(value); // Recording appended text; preserve existing content.
  // Recorded rect: x=659, y=617.8984375, width=492, height=36
});

// Step 7: 点击 `发布`
// Selector preflight: STABLE — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 7,
//   "raw_event_ids": [
//     10
//   ],
//   "action": "click",
//   "editable_kind": null,
//   "input_type": null,
//   "parameter": null,
//   "selector": "button.weui-desktop-btn.weui-desktop-btn_primary",
//   "selector_assessment": {
//     "grade": "STABLE",
//     "reason": "Recorded evidence supports this selector plan.",
//     "scope": "[data-component=\"mp-button\"]",
//     "semantic_identity": "发布",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 85.0,
//         "selector": "button.weui-desktop-btn.weui-desktop-btn_primary",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 57.0,
//         "selector": "div.weui-desktop-btn_wrp",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 49.0,
//         "selector": "div.topic-create-page__actions",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_actionable_target"
//         ],
//         "score": 45.0,
//         "selector": "[data-component=\"mp-button\"]",
//         "stability": "data_attribute",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "[data-doubao-translate-traverse-mark=\"\\31 \"]",
//         "stability": "data_attribute",
//         "unique": false
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.55,
//       "match_count": 154,
//       "recommended": true,
//       "stability": "data_attribute",
//       "type": "css",
//       "unique": false,
//       "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//     },
//     {
//       "confidence": 0.82,
//       "match_count": 1,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": true,
//       "value": "button.weui-desktop-btn.weui-desktop-btn_primary"
//     },
//     {
//       "confidence": 0.72,
//       "stability": "visible_text",
//       "type": "text",
//       "value": "发布"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 2,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "button"
//     }
//   ],
//   "element": {
//     "tag": "button",
//     "text": "发布",
//     "classes": [
//       "weui-desktop-btn",
//       "weui-desktop-btn_primary"
//     ],
//     "attributes": {
//       "data-doubao-translate-traverse-mark": "1",
//       "type": "button"
//     },
//     "rect": {
//       "height": 36,
//       "width": 96,
//       "x": 799,
//       "y": 916.5
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0.0041
//     }
//   },
//   "dom_delta": {
//     "added": [],
//     "added_count": 0,
//     "removed": [],
//     "removed_count": 0
//   },
//   "postcondition": {
//     "expected": {
//       "dom_delta": {
//         "added": [],
//         "added_count": 0,
//         "removed": [],
//         "removed_count": 0
//       },
//       "ready_state": "complete",
//       "title": "小程序",
//       "url": "https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN"
//     },
//     "kind": "recorded_page_state",
//     "source": "post_action_evidence"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-cb6185bd82039ef120c063e78d8dcea36289a542685f73cf6055162525f696b2.json",
//   "screenshot": "screenshots/step-000010-click-tab-1.jpg"
// }
await api.step("点击 `发布`", async () => {
  const selector = "button.weui-desktop-btn.weui-desktop-btn_primary";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "[data-component=\"mp-button\"]", "text": "发布", "exactText": false, "near": {"x": 847.0, "y": 934.5}});
  await target.click();
  // Recorded rect: x=799, y=916.5, width=96, height=36
});

// Step 8: 点击 `发布`
// Selector preflight: STABLE — Recorded evidence supports this selector plan.
// Recording evidence:
// {
//   "recorded_step_index": 8,
//   "raw_event_ids": [
//     11
//   ],
//   "action": "click",
//   "editable_kind": null,
//   "input_type": null,
//   "parameter": null,
//   "selector": "button.weui-desktop-btn.weui-desktop-btn_primary",
//   "selector_assessment": {
//     "grade": "STABLE",
//     "reason": "Recorded evidence supports this selector plan.",
//     "scope": "[data-component=\"mp-button\"]",
//     "semantic_identity": "发布",
//     "alternatives": [
//       {
//         "relation": "actionable_target",
//         "risks": [],
//         "score": 85.0,
//         "selector": "button.weui-desktop-btn.weui-desktop-btn_primary",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 57.0,
//         "selector": "div.weui-desktop-btn_wrp",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "non_actionable_target"
//         ],
//         "score": 49.0,
//         "selector": "div.topic-create-page__actions",
//         "stability": "semantic_classes",
//         "unique": true
//       },
//       {
//         "relation": "ancestor",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_actionable_target"
//         ],
//         "score": 45.0,
//         "selector": "[data-component=\"mp-button\"]",
//         "stability": "data_attribute",
//         "unique": true
//       },
//       {
//         "relation": "recorded_target",
//         "risks": [
//           "unproven_data_attribute_lifetime",
//           "non_unique"
//         ],
//         "score": 42.0,
//         "selector": "[data-doubao-translate-traverse-mark=\"\\31 \"]",
//         "stability": "data_attribute",
//         "unique": false
//       }
//     ]
//   },
//   "selector_candidates": [
//     {
//       "confidence": 0.55,
//       "match_count": 154,
//       "recommended": true,
//       "stability": "data_attribute",
//       "type": "css",
//       "unique": false,
//       "value": "[data-doubao-translate-traverse-mark=\"\\31 \"]"
//     },
//     {
//       "confidence": 0.82,
//       "match_count": 1,
//       "stability": "semantic_classes",
//       "type": "css",
//       "unique": true,
//       "value": "button.weui-desktop-btn.weui-desktop-btn_primary"
//     },
//     {
//       "confidence": 0.72,
//       "stability": "visible_text",
//       "type": "text",
//       "value": "发布"
//     },
//     {
//       "confidence": 0.25,
//       "match_count": 2,
//       "stability": "positional_fallback",
//       "type": "css",
//       "unique": false,
//       "value": "button"
//     }
//   ],
//   "element": {
//     "tag": "button",
//     "text": "发布",
//     "classes": [
//       "weui-desktop-btn",
//       "weui-desktop-btn_primary"
//     ],
//     "attributes": {
//       "data-doubao-translate-traverse-mark": "1",
//       "type": "button"
//     },
//     "rect": {
//       "height": 36,
//       "width": 96,
//       "x": 799,
//       "y": 916.5
//     },
//     "interaction": {
//       "actionable": true,
//       "child_element_count": 0,
//       "container_like": false,
//       "interactive_descendant_count": 0,
//       "scrollable": false,
//       "viewport_area_ratio": 0.0041
//     }
//   },
//   "dom_delta": {
//     "added": [],
//     "added_count": 0,
//     "removed": [],
//     "removed_count": 0
//   },
//   "postcondition": {
//     "expected": {
//       "dom_delta": {
//         "added": [],
//         "added_count": 0,
//         "removed": [],
//         "removed_count": 0
//       },
//       "ready_state": "complete",
//       "title": "小程序",
//       "url": "https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN"
//     },
//     "kind": "recorded_page_state",
//     "source": "post_action_evidence"
//   },
//   "structure_snapshot_path": "structure-snapshots/snapshot-cb6185bd82039ef120c063e78d8dcea36289a542685f73cf6055162525f696b2.json",
//   "screenshot": "screenshots/step-000011-click-tab-1.jpg"
// }
await api.step("点击 `发布`", async () => {
  const selector = "button.weui-desktop-btn.weui-desktop-btn_primary";
  const target = api.locator(selector, {"visible": true, "requireUnique": true, "scope": "[data-component=\"mp-button\"]", "text": "发布", "exactText": false, "near": {"x": 847.0, "y": 934.5}});
  await target.click();
  // Recorded rect: x=799, y=916.5, width=96, height=36
});

// REQUIRED: replace this fail-closed result only after checking the PRD's business identity and completion invariant.
return { success: false, validated: false, workflow: "operation_submission", validation_required: true };
