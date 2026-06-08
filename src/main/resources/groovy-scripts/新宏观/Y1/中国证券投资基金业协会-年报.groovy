import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Selectable

/**
 * 配置名称: 新宏观-Y1-中国证券投资基金业协会-年报
 * 结构: 列表页 → 详情页（若为HTML则 → 文件下载）
 * 起始请求链接: https://www.amac.org.cn/sjtj/tjbg/nb/
 */

class 中国证券投资基金业协会_年报 implements IparseScript {

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

        // ==========================================
        // 【列表页】提取文章列表项 → 跳转到详情页
        // ==========================================
        if (parser == null) {
            page.setSkip(true)

            // Xsoup 不支持 position()，用 Groovy 取前 3 条
            List<Selectable> allNodes = page.html.xpath('//div[@class="r-Top"]//ul/li').nodes() ?: []
            List<Selectable> nodes = allNodes.take(13)
            if (nodes.isEmpty()) {
                return pageResult
            }
            for (Selectable node : nodes) {
                Map<String, Object> resultMap = new LinkedHashMap<>()
                List<Map<String, Object>> candidatePool = new ArrayList<>()
                String pageTitleCandidateValue_0 = node.xpath('//a/text()').get()
                candidatePool.add([fieldName: 'pageTitle', value: pageTitleCandidateValue_0, priority: 0, pageIndex: 0])
                String releaseDateCandidateValue_1 = node.regex('\\d{4}[\\-\\.]\\d{1,2}[\\-\\.]\\d{1,2}').get()
                candidatePool.add([fieldName: 'releaseDate', value: releaseDateCandidateValue_1, priority: 0, pageIndex: 0])
                String webUrlCandidateValue_2 = node.xpath('//a/@href').get()
                candidatePool.add([fieldName: 'webUrl', value: webUrlCandidateValue_2, priority: 0, pageIndex: 0])
                String titleCandidateValue_3 = "${pageTitleCandidateValue_0}"
                candidatePool.add([fieldName: 'title', value: titleCandidateValue_3, priority: 0, pageIndex: 0])
                String downUrlCandidateValue_4 = "${webUrlCandidateValue_2}"
                candidatePool.add([fieldName: 'downUrl', value: downUrlCandidateValue_4, priority: 0, pageIndex: 0])
                Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
                candidatePool.each { candidate ->
                    if (StringUtils.isBlank((String) candidate.value)) { return }
                    def best = bestCandidateMap.get(candidate.fieldName)
                    if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                        bestCandidateMap.put(candidate.fieldName, candidate)
                    }
                }
                bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
                String pageTitle = resultMap.get('pageTitle')
                String releaseDate = resultMap.get('releaseDate')
                String webUrl = "https://www.amac.org.cn/sjtj/tjbg/nb/"
                String title = resultMap.get('title')
                String downUrl = resultMap.get('downUrl')

                if (StringUtils.isBlank(pageTitle)) {
                    continue
                }
                if (StringUtils.isNotBlank(downUrl)) {
                    String nextUrl = ParseToolsUtils.resolve(page.getRequest().getUrl(), downUrl)
                    Map<String, Object> pageParams = new HashMap<>()
                    pageParams.put("pageTitle", pageTitle)
                    pageParams.put("releaseDate", releaseDate)
                    pageParams.put("webUrl", webUrl)
                    pageParams.put("title", title)
                    pageParams.put("downUrl", nextUrl)
                    sendGet(pageResult, "详情页", nextUrl, pageParams)
                }
            }
            return pageResult
        }

        // ==========================================
        // 【详情页】判断页面类型 → 直接产出或跳转文件下载
        // ==========================================
        if (parser == "详情页") {
            String pageTitle = page.request.getExtra("pageTitle")
            String releaseDate = page.request.getExtra("releaseDate")
            String webUrl = page.request.getExtra("webUrl")
            String title = page.request.getExtra("title")
            String downUrl = page.request.getExtra("downUrl")

            // 判断当前页面是 HTML 还是二进制文件
            String rawText = page.getRawText()
            boolean isHtmlPage = StringUtils.isNotBlank(rawText)
                    && (rawText.trim().startsWith('<') || rawText.contains('<html') || rawText.contains('<!DOCTYPE'))

            if (isHtmlPage) {
                // HTML 页面 → 需找到 xlsx 链接，进入额外的文件下载阶段
                page.setSkip(true)
                List<Selectable> aNodes =  page.html.xpath("//div[@class=\"attachment-down textBox\"]//li").nodes()

                for(Selectable node : aNodes){

                    String xlsxHref = node.xpath('//a/@href').get()
                    String targetUrl = StringUtils.isNotBlank(xlsxHref)
                            ? ParseToolsUtils.resolve(page.getRequest().getUrl(), xlsxHref)
                            : null

                    if (StringUtils.isBlank(targetUrl)) {
                        return pageResult
                    }
                    Map<String, Object> pageParams = new HashMap<>()
                    pageParams.put("pageTitle", pageTitle)
                    pageParams.put("releaseDate", releaseDate)
                    pageParams.put("webUrl", webUrl)
                    pageParams.put("title", title)
                    pageParams.put("downUrl", targetUrl)
                    sendGet(pageResult, "文件下载", targetUrl, pageParams)
                }
                return pageResult

            }

            // 二进制文件 → 直接产出数据并上传
            Map<String, Object> resultMap = new LinkedHashMap<>()
            List<Map<String, Object>> candidatePool = new ArrayList<>()
            candidatePool.add([fieldName: 'pageTitle', value: pageTitle, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'releaseDate', value: releaseDate, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'webUrl', value: webUrl, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'title', value: title, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'downUrl', value: downUrl, priority: 0, pageIndex: 0])
            String downloadUrlCandidateValue_0 = "${page.getUrl()}"
            candidatePool.add([fieldName: 'downloadUrl', value: downloadUrlCandidateValue_0, priority: 0, pageIndex: 1])
            String filenameCandidateValue_1 = ParseToolsUtils.getFileName(downUrl)
            candidatePool.add([fieldName: 'filename', value: filenameCandidateValue_1, priority: 0, pageIndex: 1])
            Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
            candidatePool.each { candidate ->
                if (StringUtils.isBlank((String) candidate.value)) { return }
                def best = bestCandidateMap.get(candidate.fieldName)
                if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                    bestCandidateMap.put(candidate.fieldName, candidate)
                }
            }
            bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
            pageTitle = resultMap.get('pageTitle') ?: pageTitle
            releaseDate = resultMap.get('releaseDate') ?: releaseDate
            webUrl = resultMap.get('webUrl') ?: webUrl
            title = resultMap.get('title') ?: title
            downUrl = resultMap.get('downUrl') ?: downUrl
            String downloadUrl = resultMap.get('downloadUrl')
            String filename = resultMap.get('filename')

            String pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)

            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put('pageTitle', Pair.of('页面标题', pageTitle))
            dataMap.put('releaseDate', Pair.of('原始发布时间', releaseDate))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put('downUrl', Pair.of('下载地址', downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downloadUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), filename))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataMap.put("release_frequency", Pair.of("披露频率" ,"年"))
            dataMap.put("statistic_area", Pair.of("统计区域", "全国"))
            dataMap.put("data_target_table", Pair.of("数据目标表", "usrXHGSJBHG"))
            dataMap.put("pageTitle", Pair.of("路径", "中国人民银江西省分行\\金融数据\\江西省金融运行情况"))
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

            pageResult.addOssFileByte(page.getBytes())
            return pageResult
        }

        // ==========================================
        // 【文件下载】从 HTML 中间页跳转后下载文件并产出数据
        // ==========================================
        if (parser == "文件下载") {
            String pageTitle = page.request.getExtra("pageTitle")
            String releaseDate = page.request.getExtra("releaseDate")
            String webUrl = page.request.getExtra("webUrl")
            String title = page.request.getExtra("title")
            String downUrl = page.request.getExtra("downUrl")

            String pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)

            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put('pageTitle', Pair.of('页面标题', pageTitle))
            dataMap.put('releaseDate', Pair.of('原始发布时间', releaseDate))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put('downUrl', Pair.of('下载地址', downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), page.getUrl().toString()))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), ParseToolsUtils.getFileName(downUrl)))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataMap.put("release_frequency", Pair.of("披露频率" ,"年"))
            dataMap.put("statistic_area", Pair.of("统计区域", "全国"))
            dataMap.put("data_target_table", Pair.of("数据目标表", "usrXHGSJBHG"))
            dataMap.put("pageTitle", Pair.of("路径", "中国人民银江西省分行\\金融数据\\江西省金融运行情况"))
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

            pageResult.addOssFileByte(page.getBytes())
        }

        // 去重键
        pageResult.addDedupKey('pageTitle')
                .addDedupKey(CrawlerDataCommonFieldsEnum.WEB_URL.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey('downUrl')

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================

    /**
     * 文本清理：去除首尾空格，将中文括号替换为英文括号
     */
    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        return input.trim()
                .replace('（', '(')
                .replace('）', ')')
    }

    /**
     * 添加数据字段到结果集，同时可选加入去重键
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, value))
    }

    /**
     * 创建GET子请求
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        pageResult.addChildRequest(req)
    }
}
