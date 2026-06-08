# WebMagic支持的xpath的语法

# WebMagic支持的xpath的语法

WebMagic 核心使用的 **Xsoup** 是一个基于 Jsoup 开发的轻量级 XPath 解析器。由于它的底层原理是将 XPath 翻译为 Jsoup 的 CSS 选择器执行，因此它**只支持 XPath 的一个子集，**并且加入了一些爬虫专用的扩展函数，**它没有实现完整的 W3C XPath 标准规范。**

## 一、支持节点定位与层级定位

| **支持的写法** | **语法描述** | **XPath 示例** | **说明** |
| --- | --- | --- | --- |
| / | 从根节点选取，或绝对子节点，只找元素直接的下一层 | //div\[@id='main'\]/p | 选取 id='main' 的 div 下的所有**直接**子节点 p。 |
| // | 选取所有后代节点，找节点元素内部的所有层级 | //div\[@class='content'\]//a | 选取该 所有div标签 属性class='content' 下的所有 a 标签，无论嵌套多深。 |
| \* | 匹配任何元素节点 | //\*\[@id='app'\] | 查找 HTML 中任意一个 id 为 app 的标签。 |
| \[n\] | 索引定位（从 1 开始） | //ul\[@class='list'\]/li\[1\] | 选取匹配到的 ulclass='list' 下的**第一个** li 节点。 |

## 二、支持属性条件与逻辑组合

| **支持的写法** | **语法描述** | **XPath 示例** | **说明** |
| --- | --- | --- | --- |
| \[@attr\] | 拥有某属性 | //a\[@href\] | 选取所有带有 href 属性的 a 标签。 |
| \[@attr='val'\] | 属性值精准匹配 | //input\[@name='username'\] | 选取 name 属性刚好等于 username 的 input。 |
| \[@attr~='regex'\] | 属性值正则匹配 | //div\[@id~='post-\d+'\] | 匹配 id 类似 post-123、post-456 的动态节点。 |
| \[contains(@attr, 'val')\] | 属性值包含 | //div\[contains(@class, 'btn')\] | **注意：仅限属性包含**。不支持 contains(text(), '...')。 |
| and / or | 逻辑与 / 逻辑或 | //a\[@class='link' and @id\] | 选取 class 为 link **且** 包含 id 属性的 a 标签。 |
| <code> | </code> | 节点集合的或运算 | //h1 \| //h2 |

## 三、支持的数据提取函数

| **支持的写法** | **语法描述** | **XPath 示例** | **提取结果示例** |
| --- | --- | --- | --- |
| /@attr | 获取属性值(attr替换为节点具体属性) | //img\[@class='avatar'\]/@src | 返回图片的 src属性 链接。 |
| /text() | 获取直接子文本 | //h1/text() | 仅返回 h1 标签内包裹的直接文字。 |
| /allText() | 获取所有后代文本 | //div\[@class='article'\]/allText() | 自动剥离该区块内所有 HTML 标签，返回纯净的合并文本。 |
| /tidyText() | 获取整洁排版文本 | //div\[@id='content'\]/tidyText() | 类似 allText，但会把 <p>、<br> 智能替换为换行符。 |
| /html() | 获取内部 HTML 代码 | //div\[@class='box'\]/html() | 返回该 div 内部包含的所有 HTML 源码。 |
| /outerHtml() | 获取完整 HTML 代码 | //div\[@class='box'\]/outerHtml() | 返回包含该 div 本身在内的完整 HTML 源码。 |

**脚本中使用xpath定位元素的方法：**

使用page对象获取html，使用html的xpath方法执行xpath规则匹配

// 获取HTML中属性id值是from的直接子文本，存在多个id='from'的只会获取第一个  
page.html.xpath("//\*\[@id=\"from\"\]/text()").get()