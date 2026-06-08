# Groovy 采集脚本代码规范

> 基于 `系统支持的内置的函数说明.md` 及现有脚本的最佳实践整理。

---

## 一、文件与类命名

### 1.1 文件命名

- 路径层级：`groovy-scripts/{数据来源}/{子目录...}/{描述性名称}.groovy`
- 文件名应与采集目标对应，使用中文或英文描述，如：`社会融资规模-社会融资规模存量统计表.groovy`
- 模板文件放在 `groovy-scripts/template/` 目录下

### 1.2 类命名

- 类名必须与文件名一致（不含 `.groovy` 后缀）
- 优先使用英文驼峰或中文拼音缩写，能见名知义
- 必须实现 `com.gildata.spider.base.starter.groovy.IparseScript`
- **禁止类级别的变量**（成员字段），所有变量必须在方法内部声明

```groovy
// ✅ 推荐
class gb_stats_json implements IparseScript { }
class RDSJ_SC implements IparseScript { }

// ❌ 避免
class ParseClass_1727191943194083329 implements IparseScript { }  // 无意义ID
class SampleClass implements IparseScript { }                     // 模板占位名
```

---

## 二、导入规范

### 2.1 必需导入

每个脚本至少需要以下导入：

```groovy
import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
```

### 2.2 按需导入

| 场景 | 导入 |
|---|---|
| JSON 解析 | `import us.codecraft.webmagic.selector.Json` |
| 表单/POST 请求 | `import com.gildata.spider.base.starter.groovy.util.SpiderManagerUtils` |
| 反爬配置 | `import com.gildata.spider.base.starter.groovy.model.AntiPageRequest` |
| 字符串判空 | `import org.apache.commons.lang3.StringUtils` |
| 图片处理 | `import com.gildata.spider.base.starter.groovy.util.ImageToolsUtils` |
| XPath 辅助 | `import com.gildata.spider.base.starter.groovy.util.XPathContainsReplacer` |
| 公共字段枚举 | `import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum` |
| 时间处理 | `import java.time.LocalDate` / `import java.time.LocalDateTime` |

> **已弃用**：`LogUtils` 已废弃，不再使用。

### 2.3 禁止导入

以下包/类被安全校验拦截，**严禁导入**：

- `java.lang.System`
- `java.lang.Runtime`
- `java.lang.ProcessBuilder`
- `java.io.*`
- `java.net.*`
- `java.sql.*`
- `javax.script.*`
- `groovy.lang.GroovyShell`

以下关键字/模式也会被拦截，**严禁使用**：

- `System.exit`、`Runtime.getRuntime`、`ProcessBuilder`
- `Class.forName`、`ClassLoader`、`Reflection`
- `new File`、`new Socket`、`new URL`、`URLConnection`
- `ScriptEngine`、`addTargetRequest`
- `request.addHeader(...)` 设置 Cookie（Cookie 必须走框架的反爬机制，禁止脚本内硬编码）

---

## 三、脚本骨架结构

### 3.1 最小骨架必须带反爬

```groovy
import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request

class YourScriptName implements IparseScript {

    @Override
    AntiPageRequest beforeDownloader(AntiPageRequest request) {
        AntiPageRequest requestTmp = new AntiPageRequest()
        Request requestUrl = new Request()
        requestUrl.putExtra("trustAllCerts", true)
        requestTmp.setRequest(requestUrl)
        return requestTmp
    }
    
    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        String parser = page.request.getExtra("parser")

        if (parser == null) {
            // 第一阶段：入口解析
        } 
        
        if (parser == "阶段名称") {
            // 后续阶段
        }

        return pageResult
    }
}
```

### 3.2 带反爬配置的骨架

```groovy
class YourScriptName implements IparseScript {

    @Override
    AntiPageRequest beforeDownloader(AntiPageRequest request) {
        AntiPageRequest requestTmp = new AntiPageRequest()
        Request requestUrl = new Request()
        requestUrl.putExtra("trustAllCerts", true)
        requestTmp.setRequest(requestUrl)
        return requestTmp
    }

    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        // ... 同上
    }
}
```

### 3.3 带工具方法的完整骨架

```groovy
class YourScriptName implements IparseScript {
    
    @Override
    AntiPageRequest beforeDownloader(AntiPageRequest request) {
        AntiPageRequest requestTmp = new AntiPageRequest()
        Request requestUrl = new Request()
        requestUrl.putExtra("trustAllCerts", true)
        requestTmp.setRequest(requestUrl)
        return requestTmp
    }
    
    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        // 解析逻辑（按 parser 分阶段）
    }

    // ==========================================
    // 【工具方法】
    // ==========================================
    /**
     * 【新增】文本清理方法
     * 1. 去除首尾空格
     * 2. 将中文括号（）替换为英文括号()
     * @param input 原始文本
     * @return 清理后的文本
     */
    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        // 1. 去除首尾空格
        // 2. 替换中文左括号
        // 3. 替换中文右括号
        return input.trim()
                .replace('（', '(')
                .replace('）', ')')
    }

    /**
     * 添加数据字段到结果集
     * @param field Pair.of('字段代码', '字段中文名')
     * @param value 字段值
     * @param dedup 是否作为去重字段（true表示该字段组合用于判断重复数据）
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, value))
    }

    /**
     * 创建GET子请求
     * @param pageName 下一阶段标识（parser参数值）
     * @param requestUrl 请求网址
     * @param pageParams 传递给下一阶段的参数Map
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        req.setPriority(99)
        req.putExtra("trustAllCerts", true)
        pageResult.addChildRequest(req)
    }

    /**
     * 创建POST请求
     * @param pageName 下一阶段名称
     * @param requestUrl 请求网址
     * @param pageParams 传递给下一阶段的参数
     * @param postParams 请求参数
     * @param paramsType 参数类型(form 或者 json)
     */
    private static sendPost(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null, Map<String, Object> postParams, String paramsType = "form"){
        //对下阶段的请求构建post请求
        //创建请求对象
        Double random = Math.random()
        Request req = new Request(requestUrl + "?random=${random}")
        req.setPriority(99)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        if (pageParams != null){
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        //form参数-看请求标头Content-Type
        if (paramsType == 'form')
            SpiderManagerUtils.handlePostFormRequest(req, postParams)
        //json参数-看请求标头Content-Type
        if (paramsType == 'json')
            SpiderManagerUtils.handlePostJSonRequest(req, postParams)
        //将请求添加到子请求中
        pageResult.addChildRequest(req)
    }

    /**
     * 将"yyyyMM"格式的字符串转换为"yyyy年MM月"格式
     * @param input 输入字符串，如"202603MM"
     * @return 转换后的字符串，如"2026年03月"
     */
    private static String parseDate(String input) {
        if (input == null || input.length() < 6 || !input.substring(0, 6).isNumber()) {
            return ""
        }

        // 提取年份和月份
        def year = input.substring(0, 4)
        def month = input.substring(4, 6)

        // 返回格式化后的字符串
        return "${year}年${month}月"
    }
}
```

---

## 四、Parser 分阶段架构

### 4.1 基本原则

- 使用 `page.request.getExtra("parser")` 作为阶段标识
- `parser == null` 表示第一阶段（入口）
- 后续阶段用有意义的**中文名称**标识，如 `"遍历指标树"`、`"详情页"`、`"下载页"`

### 4.2 控制流风格

```groovy
if (parser == null) {
    // 第一阶段
    return pageResult          // 第一阶段用 return 终止
}

if (parser == "第二阶段") {
    // ...
    return pageResult
}

if (parser == "第三阶段") {
    // ...
}

return pageResult              // 最后阶段落到统一 return
```

**规则：**
- 第一阶段（`parser == null`）处理完后**立即 return**，防止穿透到后续阶段
- 中间阶段如果逻辑完整也建议 return
- 最终阶段可以不 return，统一落到方法末尾的 `return pageResult`
- 不推荐使用 `else if` 链——使用独立 `if + return` 更清晰，避免嵌套

### 4.3 数据传递

通过 `request.putExtra(key, value)` 在阶段间传递数据：

```groovy
// 第一阶段：携带数据
Request newRequest = new Request(detailUrl)
newRequest.putExtra("parser", "详情页")
newRequest.putExtra("dataMap", dataMap)     // 传递已提取的字段
newRequest.putExtra("titlePath", "路径/子路径")
pageResult.addChildRequest(newRequest)

// 第二阶段：取出数据
Map<String, Pair<String, String>> dataMap = page.request.getExtra("dataMap")
String parentTitle = page.request.getExtra("titlePath")
```

### 4.4 跳过页面默认下载

如果当前阶段不需要下载页面本身（比如只产子请求），必须跳过：

```groovy
page.setSkip(true)
```

---

## 五、数据提取规范

### 5.1 XPath 提取

> WebMagic 核心使用 **Xsoup**（基于 Jsoup 的轻量级 XPath 解析器），只支持 XPath 的一个子集，未实现完整的 W3C XPath 标准。

#### 5.1.1 节点与层级定位

| 写法 | 语法描述 | 示例 |
|---|---|---|
| `/` | 从根节点选取直接子节点 | `//div[@id='main']/p` |
| `//` | 选取所有后代节点（任意深度） | `//div[@class='content']//a` |
| `*` | 匹配任何元素节点 | `//*[@id='app']` |
| `[n]` | 索引定位（从 1 开始） | `//ul[@class='list']/li[1]` |

#### 5.1.2 属性条件与逻辑组合

| 写法 | 语法描述 | 示例 |
|---|---|---|
| `[@attr]` | 拥有某属性 | `//a[@href]` |
| `[@attr='val']` | 属性值精准匹配 | `//input[@name='username']` |
| `[@attr~='regex']` | 属性值正则匹配 | `//div[@id~='post-\\d+']` |
| `[contains(@attr, 'val')]` | 属性值包含 | `//div[contains(@class, 'btn')]` |

> **注意：** `contains()` 仅支持属性包含，不支持 `contains(text(), '...')`。

| `and` / `or` | 逻辑与 / 逻辑或 | `//a[@class='link' and @id]` |
| `|` | 节点集合的或运算 | `//h1 | //h2` |

#### 5.1.3 数据提取函数

| 写法 | 功能 | 示例 | 返回 |
|---|---|---|---|
| `/@attr` | 获取属性值 | `//img[@class='avatar']/@src` | 图片 src 链接 |
| `/text()` | 获取直接子文本 | `//h1/text()` | h1 内的直接文字 |
| `/allText()` | 获取所有后代文本 | `//div[@class='article']/allText()` | 剥离 HTML 标签后的纯净合并文本 |
| `/tidyText()` | 获取整洁排版文本 | `//div[@id='content']/tidyText()` | 类似 allText，但将 `<p>`、`<br>` 替换为换行符 |
| `/html()` | 获取内部 HTML | `//div[@class='box']/html()` | 该节点内部的所有 HTML 源码 |
| `/outerHtml()` | 获取完整 HTML | `//div[@class='box']/outerHtml()` | 包含该节点本身在内的完整 HTML |

#### 5.1.4 代码示例

```groovy
// 取单个值
String title = page.html.xpath("//h1/text()").get()
String href = page.html.xpath("//a/@href").get()

// 取列表
List<Selectable> items = page.html.xpath("//div[@class='item']").nodes()
for (def item : items) {
    String text = item.xpath("//span/text()").get()
}

// 取全部匹配
List<String> allLinks = page.html.xpath("//a/@href").all()
```

#### 5.1.5 子节点 XPath 规则

- 对 `Selectable` 节点取子元素时，必须使用 `//` 前缀（相对路径），**禁止使用 `./`**和`.//`
- `./` 和`.//`在某些解析器实现中会退化为全局搜索，导致结果不正确

```groovy
// ✅ 正确：使用 // 相对路径
for (Selectable node : nodes) {
    String href = node.xpath("//a/@href").get()
    String text = node.xpath("//a/text()").get()
}

// ❌ 错误：使用 ./ 禁止
String href = node.xpath("./a/@href").get()
String href = node.xpath(".//a/@href").get() // Could not parse query './/a/@href': unexpected token at './/a/@href'
```

#### 5.1.6 不支持的 XPath 语法及替代方案

Xsoup 不支持标准 XPath 中的以下函数，遇到类似需求必须用代码替代：

| 不支持写法 | 替代方案 |
|---|---|
| `position()` | 使用 `.nodes()` 获取全部节点后，在 Groovy 中用下标或 `.drop(N)` 过滤 |
| `last()` | 同上，用 `nodes.size()-1` 或 `.last()` 取最后一个 |
| `count()` | 用 `.nodes().size()` 统计节点数 |
| `contains(text(), 'val')` | 先用 `.nodes()` 获取节点，再对 `.xpath("text()").get()` 做 `String.contains()` 判断 |
| `normalize-space()` | 用 Groovy 的 `.trim().replaceAll('\\s+', ' ')` |
| `starts-with()` | 用 Groovy 的 `String.startsWith()` |
| `substring-before/after()` | 用 Groovy 的字符串方法或 `ParseToolsUtils.extractFirstGroup()` |
| `not()` | 反向逻辑放到代码中判断 |

**position() 替代示例：**

```groovy
// ❌ 不支持：tr[position()>5]
// page.html.xpath("//table//tr[position()>5]").nodes()

// ✅ 替代：全部取回后用 Groovy 跳过前 5 个
List<Selectable> allRows = page.html.xpath("//table//tr").nodes()
List<Selectable> filteredRows = allRows.drop(5)  // 跳过前 5 行
for (def row : filteredRows) {
    // 处理
}

// ✅ 也可以遍历时用索引跳过
int index = 0
for (def row : allRows) {
    index++
    if (index <= 5) {
        continue     // 跳过前 5 个
    }
    // 处理
}
```

### 5.2 正则提取

优先使用 `ParseToolsUtils`：

```groovy
String title = ParseToolsUtils.extractFirstGroup(html, '<h1[^>]*>(.*?)</h1>')
String time = ParseToolsUtils.extractFirstGroup(html, '发布时间[：:]\\s*([0-9\\- :/]+)')
List<String> attachments = ParseToolsUtils.extractAll(html, 'href="(.*?\\.(pdf|docx?|xlsx?))"')
```

### 5.3 JSON 提取

```groovy
// 直接用 Page 的 JSON（前提是响应为 JSON）
def nodes = page.getJson().jsonPath("$.data[*]").all()

// JSONP 先转 JSON
def json = ParseToolsUtils.extractJsonFromJsonp(page.getRawText())

// 安全读取字段
String value = ParseToolsUtils.safeGetJsonPath(new Json(item), "$.fieldName")
```

### 5.4 链接补全

```groovy
// 补全相对链接
String fullUrl = ParseToolsUtils.resolve(page.getUrl().toString(), relativeHref)

// 批量补全页面中所有相对链接
page = ParseToolsUtils.convertRelativeUrlsToAbsolute(page)

// 补全图片地址
contentHtml = ParseToolsUtils.convertImgRelativeToAbsolute(contentHtml, page.getUrl().toString())
```

---

## 六、数据产出规范

### 6.1 使用 CrawlerDataCommonFieldsEnum（推荐）

当目标系统预定义了字段枚举时优先使用。

**pushtime 字段强制要求：**
- 键名必须使用 `CrawlerDataCommonFieldsEnum.PUSHTIME` 枚举
- 值必须经过 `ParseToolsUtils.formatPushTimeDateTime()` 标准化，禁止原始字符串直接入库

**CrawlerDataCommonFieldsEnum 可用字段：**

| 枚举常量 | name（入库键） | alias（中文名） | 说明 |
|---|---|---|---|
| `TITLE` | `title` | 标题 | |
| `SUBTITLE` | `subtitle` | 副标题 | |
| `CONTENT` | `content` | 资讯正文 | |
| `PUSHTIME` | `pushtime` | 发布时间 | 必须用 formatPushTimeDateTime 标准化 |
| `AUTHOR` | `author` | 作者 | |
| `MTCC` | `mtcc` | 媒体出处 | |
| `DETAILURL` | `detailUrl` | url链接 | |
| `DOWNLOADURL` | `downloadUrl` | 文件下载链接 | |
| `FILENAME` | `filename` | 文件名 | 用 ParseToolsUtils.getFileName 提取 |
| `FILEMD5` | `fileMd5` | 文件MD5 | |
| `FILETYPE` | `fileType` | 文件类型 | |
| `FILESIZE` | `fileSize` | 文件大小 | |
| `SECUCODE` | `secuCode` | 证券代码 | |
| `SECUNAME` | `secuName` | 证券简称 | |
| `LAYOUT_NUMBER` | `layout_number` | 版面号 | 报纸模板专用 |
| `LAYOUT_INFO` | `layout_info` | 版面信息 | 报纸模板专用 |
| `WEB_URL` | `webUrl` | 网站地址 | |
| `DATA_SOURCE_CODE` | `dataSourceCode` | 数据源编码 | |
| `SYS_DATE` | `sysdate` | 系统日期 | |
| `DATA_URL` | `dataURL` | 数据链接 | |
| `V_CONTENT` | `vContent` | 正文 | |
| `IMG_DATA_SRCS` | `img_data_srcs` | 图片链接加密值 | |
| `IMG_SRCS` | `img_srcs` | 图片链接解密值 | |
| ~~`RELEASE_DATE`~~ | `releaseDate` | 发布日期 | **已废弃，勿用** |

> **注意**：没有 `PAGETITLE` 枚举，阶段间传递非入库字段请使用自定义字符串 key（如 `"yearTitle"`），不要用 `CrawlerDataCommonFieldsEnum.XXX.getName()`。

```groovy
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum

Map<String, Pair<String, String>> dataMap = new HashMap<>()

// ✅ 正确：pushtime 使用枚举 + 标准化
dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
    Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), ParseToolsUtils.formatPushTimeDateTime(releaseDate)))

// ❌ 错误：pushtime 未标准化
dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
    Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), releaseDate))
dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
    Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), url))
dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(),
    Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downUrl))
dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(),
    Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), ParseToolsUtils.getFileName(downUrl)))
```

### 6.2 自定义字段（使用 addDataMap 辅助方法）

```groovy
Map<String, Pair<String, String>> dataMap = new HashMap<>()
addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
addDataMap(pageResult, dataMap, Pair.of("indicator_name", "指标名称"), indicatorName, true)
addDataMap(pageResult, dataMap, Pair.of("indicator_data", "指标数据"), value, true)
addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "日", true)
addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
```

**字段 Pair 规范：**
- `Pair.left`：**字段代码**（英文 key，入库用）
- `Pair.right`：**字段中文名**（展示用）

### 6.3 产出到结果集

```groovy
List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
// ... 循环添加 dataMap ...
dataMapList.add(dataMap)

// 批量产出
if (!dataMapList.isEmpty()) {
    pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
}
```

### 6.4 文件下载

```groovy
// 添加文件字节流（触发文件上传）
// 注意：下载阶段只做文件字节上传，不需要提取页面 content
pageResult.addOssFileByte(page.getBytes())
```

> **下载阶段规则**：文件下载页只调用 `addOssFileByte(page.getBytes())` 上传文件，**禁止**在此阶段提取 `content`（下载页通常是二进制文件或 HTML 骨架，提取的 content 无意义且影响数据美观）。

### 6.5 去重键

```groovy
// 方式一：链式调用（字段代码/key）
pageResult.addDedupKey("title")
        .addDedupKey("releaseDate")
        .addDedupKey("downUrl")

// 方式二：通过 addDataMap 的 dedup 参数自动添加
addDataMap(pageResult, dataMap, Pair.of("title", "标题"), title, true)  // true = 加入去重
```

**规则：**
- 去重键用 Pair 的 left（字段代码），不是中文名
- 去重键应在最终数据阶段统一添加，不要分散在各阶段

---



## 七、数据质量要求

### 7.1 字段命名

字段键名应能大体反映字段值的含义，做到见名知义：

```groovy
// ✅ 正确：键名与值含义一致
dataMap.put("indicator_name", Pair.of("指标名称", "CPI同比增速"))
dataMap.put("statistic_area", Pair.of("统计区域", "全国"))

// ❌ 错误：键名与值含义不符
dataMap.put("backup_field1", Pair.of("公司ID", "12345"))  // 键名无意义
dataMap.put("title", Pair.of("路径", "/a/b/c"))           // title 存的是路径
```

### 7.2 空值规则

- **禁止整条记录的某个字段在所有行中全为空字符串**
- 部分行为空是允许的——只要该字段在整个结果集中存在至少一条非空记录即合法
- 产出前用 `StringUtils.isBlank()` 过滤空值，但不要因为个别字段为空就丢弃整条记录

### 7.3 文本清洁

- **禁止字段值中包含多余干扰阅读的字符**（如乱码、转义符 `\n`、`\r`、多余空格等）
- **HTML 标签是允许的**（正文、内容类字段可保留标签结构）
- 产出前使用 `cleanText()` 或 `ParseToolsUtils` 做必要清洗

```groovy
// ✅ 正确：清洗干扰字符
title = title.trim().replace('\n', '').replace('\r', '')

// ✅ 正确：HTML 标签可以保留
content = ParseToolsUtils.buqiHtml(rawHtml)
```

---

## 八、时间处理规范

### 8.1 时间标准化

```groovy
// 已知格式时显式转换
String pushTime = ParseToolsUtils.standardDateByFormat("2026/03/31 18:05", "yyyy/MM/dd HH:mm")

// 格式不确定时智能解析
String pushTime = ParseToolsUtils.smartGetPushTime(rawTime)

// 默认格式化
String pushTime = ParseToolsUtils.formatPushTimeDateTime(rawTime)

// 时间戳转标准格式
String date = ParseToolsUtils.timestamp2StandardDate("1711929600000")
```

### 8.2 时间范围判断

```groovy
// 判断是否在最近 N 天内
if (!ParseToolsUtils.isDateInRange(pushTime, -7, 0)) {
    return new ParsePageResult()  // 超出范围的跳过
}

// 表达式判断
String result = ParseToolsUtils.dateRangeCheck(targetDate, startDate, endDate, "命中", "未命中")
```

### 8.3 获取当前时间

```groovy
// 当前日期
String today = LocalDate.now().toString()                         // "2026-05-08"

// 前N天
String twoDaysAgo = LocalDate.now().minusDays(2).toString()

// 当前时间
LocalDateTime currentTime = LocalDateTime.now()
LocalDateTime startTime = currentTime.minusMonths(24)
```

---

## 九、请求构造规范

### 9.1 GET 请求

```groovy
Request req = new Request(requestUrl)
req.putExtra("parser", "阶段名")
req.putExtra("trustAllCerts", true)
req.setPriority(99)                          // 优先级（越大越先执行）
pageResult.addChildRequest(req)
```

### 9.2 POST 表单请求

```groovy
Request req = new Request(requestUrl)
Map<String, Object> params = new HashMap<>()
params.put("keyword", "招标公告")
params.put("pageNum", 1)
SpiderManagerUtils.handlePostFormRequest(req, params)
req.putExtra("parser", "搜索页")
req.setPriority(99)
pageResult.addChildRequest(req)
```

### 9.3 POST JSON 请求

```groovy
Request req = new Request(requestUrl)
Map<String, Object> params = new HashMap<>()
params.put("pageNo", 1)
params.put("pageSize", 20)
SpiderManagerUtils.handlePostJSonRequest(req, params)
req.putExtra("parser", "列表页")
req.setPriority(99)
pageResult.addChildRequest(req)
```

### 9.4 参数标准化（用于签名/去重）

```groovy
def params = [b: "2", a: "1"]
String formText = SpiderManagerUtils.normalizeFormParams(params)  // "a=1&b=2"
String jsonText = SpiderManagerUtils.normalizeJsonParams(params)  // {"a":"1","b":"2"}
```

---

## 十、文本清洗规范

### 10.1 使用 cleanText 辅助方法

```groovy
private static String cleanText(String input) {
    if (input == null) {
        return ""
    }
    return input.trim()
            .replace('（', '(')
            .replace('）', ')')
}

// 使用
title = cleanText(title)
location = cleanText(location)
```

### 10.2 HTML 清洗

```groovy
// 移除脚本、样式、无关节点
contentHtml = ParseToolsUtils.removeElementsByXpath(contentHtml,
    "//script&&//style&&//div[contains(@class,'share')]")

// 补全残缺 HTML
contentHtml = ParseToolsUtils.completeHtml(contentHtml)
```

### 10.3 字符串处理

```groovy
// 前后缀
String key = ParseToolsUtils.addPrefix(fileName, "attachment:")
String url = ParseToolsUtils.addSuffix(baseUrl, "/index.html")

// 分割取值
String lastPart = ParseToolsUtils.getSplittedLastItem(path)
String item = ParseToolsUtils.getSplittedItem(csvLine, 2)

// Unicode 解码
String chinese = ParseToolsUtils.unicodeToChinese("\\u4e2d\\u6587")
```

---

## 十一、判空与防御性编程

### 11.1 关键字段判空

```groovy
// 使用 StringUtils（Apache Commons）
import org.apache.commons.lang3.StringUtils

if (StringUtils.isBlank(url) || StringUtils.isBlank(releaseDate) || StringUtils.isBlank(downUrl)) {
    return pageResult  // 关键字段缺失，放弃本条
}
```

### 11.2 列表遍历中的判空

```groovy
for (def item : items) {
    String value = ParseToolsUtils.safeGetJsonPath(new Json(item), "$.field")
    if (StringUtils.isBlank(value)) {
        continue   // 跳过空值，不要直接 return pageResult
    }
    // ... 正常处理 ...
}
```

> **重要：** 在循环中遇到空值用 `continue` 跳过当前项，**不要** `return pageResult`，否则会丢弃同批次的其他有效数据。

### 11.3 Groovy 安全导航

```groovy
// 使用安全导航操作符 ?.
String title = page?.html?.xpath("//h1/text()")?.get()

// 使用 Elvis 操作符提供默认值
String name = ParseToolsUtils.safeGetJsonPath(it, "name") ?: ""
String parentTitle = page.request.getExtra("titlePath") ?: "默认路径"
```

---

## 十二、日志规范（已废弃）

> **LogUtils 已弃用**，脚本中不再使用 `LogUtils` 进行日志输出。运行时由框架统一处理日志。

---

## 十三、代码风格

### 13.1 缩进与空格

- 使用 **4 空格** 缩进（不使用 Tab）
- 运算符两侧加空格：`String name = value ?: "default"`
- 逗号后加空格：`[a: 1, b: 2]`

### 13.2 命名约定

| 类型 | 风格 | 示例 |
|---|---|---|
| 局部变量 | camelCase | `pageResult`, `dataMapList`, `requestUrl` |
| 方法名 | camelCase | `sendGet`, `addDataMap`, `cleanText` |
| 常量/枚举 | UPPER_SNAKE | `CrawlerDataConstants.CRAWLER_DATA_KEY` |
| 阶段名（parser） | 中文 | `"遍历指标树"`, `"详情页"`, `"下载页"` |

### 13.3 变量声明

- 明确类型优于 `def`（除 Groovy 闭包迭代变量外）：

```groovy
// ✅ 推荐
String title = page.html.xpath("//h1/text()").get()
List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
ParsePageResult pageResult = new ParsePageResult()

// ✅ 闭包迭代可用 def
for (def item : items) { ... }
items.each { key, value -> req.putExtra(key, value) }

// ❌ 避免过度使用 def
def title = ...      // 不明确类型
def dataMapList = ... // 不明确类型
```

### 13.4 Map 字面量

```groovy
// Groovy 风格
def map = [
    "key1": value1,
    "key2": value2
]

// 显式 HashMap
Map<String, Object> params = new HashMap<>()
params.put("key1", value1)
params.put("key2", value2)
```

### 13.5 注释规范

```groovy
// ==========================================
// 【第N阶段】阶段中文描述
// ==========================================
if (parser == "阶段名") {
    // 解析逻辑
}
```

- 用分隔注释标明阶段边界
- 工具方法用简短 JavaDoc 说明参数用途
- 对于较复杂逻辑处理，给出单行注释
- 不要用注释解释代码做了什么——方法名和变量名应该自说明

---

## 十四、常见场景模板

### 14.1 HTML 列表页 → 详情页 → 下载

```
阶段：null（列表页） → "详情页" → "下载页"
```

### 14.2 JSON 接口 → 递归遍历 → 数据提取

```
阶段：null（入口） → "遍历指标树" → "获取叶子节点" → "获取最终数据"
```

### 14.3 百度起始 → 跳转 → 下载

```
阶段：null（百度） → "跳转页" → "下载页"
```

---

## 十五、检查清单

编写完成脚本后，逐项确认：

- [ ] 类名与文件名一致，实现 `IparseScript`
- [ ] 没有类级别变量，所有变量在方法内声明
- [ ] 没有导入/使用被禁止的包和关键字（含已弃用的 `LogUtils`）
- [ ] 没有使用 `request.addHeader(...)` 设置 Cookie
- [ ] 没有使用不存在的枚举常量（如 `PAGETITLE`），阶段间传递用自定义字符串 key
- [ ] `parser == null` 的入口阶段有 `return pageResult`
- [ ] `page.setSkip(true)` 用于不需要下载页面本身的阶段
- [ ] 循环中遇到空字段用 `continue` 而非 `return`
- [ ] 关键字段（url、title、date）在产出前做了判空
- [ ] 去重键在最终数据阶段统一添加
- [ ] `pushtime` 使用 `CrawlerDataCommonFieldsEnum.PUSHTIME` 枚举，值经过 `formatPushTimeDateTime` 标准化
- [ ] 使用 `ParseToolsUtils` 处理时间标准化，而非手写 SimpleDateFormat
- [ ] `pageResult.addOssFileByte()` 在需要下载文件的阶段调用,当下载文件时，无需` String content = page.html.get()`，即无需获取下载页面的content（输出不美观），直接下载`pageResult.addOssFileByte(page.getBytes())`
- [ ] 相对链接已补全为绝对链接
- [ ] 数据产出使用 `CrawlerDataConstants.CRAWLER_DATA_KEY`
- [ ] 子请求设置了 `parser` 和 `priority`
- [ ] 跨阶段数据通过 `request.putExtra` 正确传递
- [ ] 字段键名与值含义一致，无无意义命名
- [ ] 无全字段为空的列；文本中无干扰字符（乱码、\n、\r 等）
