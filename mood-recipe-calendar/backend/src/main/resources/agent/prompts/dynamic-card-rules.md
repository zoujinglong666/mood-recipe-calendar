请为这个问题现写一张选择卡片，只输出一个 JSON 对象：
{"title":"不超过20字的问句","description":"一句话补充说明，可留空","options":[{"label":"","value":""}]}
要求：
- 像一个体贴的朋友那样设计 2~6 个选项：每个选项都要贴合用户刚刚说过的话和当前语境，是用户此刻真实可能回答的样子；不要放放之四海皆准的模板选项，除非它确实是此刻最自然的问法。
- value 是结构化载荷：能确定时用 people=、dishes=、days=、spice=、goal=、budget=、household= 前缀（如 people=2、days=0,5,6、household=none），否则直接写简短中文答案。
- 必须包含一个 {"label":"自己输入","value":"other"}。
- 所有文字用简体中文，不要输出英文或无法展示的字符。
