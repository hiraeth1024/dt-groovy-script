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
import us.codecraft.webmagic.selector.Html
import us.codecraft.webmagic.selector.Selectable

/**
 * 配置名称: 中华人民共和国海关总署-政务公开-海关统计-统计月报-进出口商品国别(地区)总值表
 * 结构: 列表入口 → 列表页 → 正文页 / 附件页 → 文件下载
 * 起始请求链接: https://www.baidu.com/
 */
class adcas implements IparseScript {

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
        // 【列表入口】起始页 → 跳转到列表页
        // ==========================================
        if (parser == null) {
            page.setSkip(true)
            String url1 = "http://www.customs.gov.cn/customs/302249/zfxxgk/2799825/302274/302277/4899681/index.html"
            String url2 = url1
            Map<String, Object> pageParams = new HashMap<>()
            pageParams.put("url1", url1)
            pageParams.put("url2", url2)
            sendGet(pageResult, "列表页", url2, pageParams)
            return pageResult
        }

        // ==========================================
        // 【列表页】提取文章列表项
        // ==========================================
        if (parser == "列表页") {
            page.setSkip(true)
            String url1 = page.request.getExtra("url1")
            String url2 = page.request.getExtra("url2")

            List<Selectable> all = page.html.xpath('//ul[@class="article"]/li').nodes() ?: []
            List<Selectable> nodes = all.take(10)

            if (nodes == null || nodes.isEmpty()) {
                return pageResult
            }
            for (Selectable node : nodes) {
                Map<String, Object> resultMap = new LinkedHashMap<>()
                List<Map<String, Object>> candidatePool = new ArrayList<>()
                candidatePool.add([fieldName: 'url1', value: url1, priority: 0, pageIndex: 0])
                candidatePool.add([fieldName: 'url2', value: url2, priority: 0, pageIndex: 0])
                String titleCandidateValue_0 = node.xpath('//a/@title').get()
                candidatePool.add([fieldName: 'title', value: titleCandidateValue_0, priority: 0, pageIndex: 1])
                String downUrlCandidateValue_1 = node.xpath('//a[contains(@title,"进出口商品国别（地区）总值表（人民币）")]/../a/@href').get()
                candidatePool.add([fieldName: 'downUrl', value: downUrlCandidateValue_1, priority: 0, pageIndex: 1])
                String url3CandidateValue_2 = "${downUrlCandidateValue_1}"
                candidatePool.add([fieldName: 'url3', value: url3CandidateValue_2, priority: 0, pageIndex: 1])
                String webUrlCandidateValue_3 = "${downUrlCandidateValue_1}"
                candidatePool.add([fieldName: 'webUrl', value: webUrlCandidateValue_3, priority: 0, pageIndex: 1])
                String ggmcCandidateValue_4 = "${titleCandidateValue_0}"
                candidatePool.add([fieldName: 'ggmc', value: ggmcCandidateValue_4, priority: 0, pageIndex: 1])
                Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
                candidatePool.each { candidate ->
                    if (StringUtils.isBlank((String) candidate.value)) { return }
                    def best = bestCandidateMap.get(candidate.fieldName)
                    if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                        bestCandidateMap.put(candidate.fieldName, candidate)
                    }
                }
                bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
                url1 = resultMap.get('url1') ?: url1
                url2 = resultMap.get('url2') ?: url2
                String title = resultMap.get('title')
                String downUrl = resultMap.get('downUrl')
                String url3 = resultMap.get('url3')
                String webUrl = resultMap.get('webUrl')
                String ggmc = resultMap.get('ggmc')

                if (StringUtils.isBlank(downUrl)) {
                    continue
                }
                // 创建正文页子请求
                if (StringUtils.isNotBlank(url3)) {
                    String nextUrl = ParseToolsUtils.resolve(page.getRequest().getUrl(), url3)
                    Map<String, Object> pageParams = new HashMap<>()
                    pageParams.put("url1", url1)
                    pageParams.put("url2", url2)
                    pageParams.put("title", title)
                    pageParams.put("downUrl", downUrl)
                    pageParams.put("url3", nextUrl)
                    pageParams.put("webUrl", webUrl)
                    pageParams.put("ggmc", ggmc)
                    sendGet(pageResult, "正文页", nextUrl, pageParams)
                }
                // 创建附件页子请求
                if (StringUtils.isNotBlank(webUrl)) {
                    String nextUrl = ParseToolsUtils.resolve(page.getRequest().getUrl(), webUrl)
                    Map<String, Object> pageParams = new HashMap<>()
                    pageParams.put("url1", url1)
                    pageParams.put("url2", url2)
                    pageParams.put("title", title)
                    pageParams.put("downUrl", downUrl)
                    pageParams.put("url3", url3)
                    pageParams.put("webUrl", nextUrl)
                    pageParams.put("ggmc", ggmc)
                    sendGet(pageResult, "附件页", nextUrl, pageParams)
                }
            }
            return pageResult
        }

        // ==========================================
        // 【正文页】提取文章正文内容
        // ==========================================
        if (parser == "正文页") {
            String url1 = page.request.getExtra("url1")
            String url2 = page.request.getExtra("url2")
            String title = page.request.getExtra("title")
            String downUrl = page.request.getExtra("downUrl")
            String url3 = page.request.getExtra("url3")
            String webUrl = page.request.getExtra("webUrl")
            String ggmc = page.request.getExtra("ggmc")

            Map<String, Object> resultMap = new LinkedHashMap<>()
            List<Map<String, Object>> candidatePool = new ArrayList<>()
            candidatePool.add([fieldName: 'url1', value: url1, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'url2', value: url2, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'title', value: title, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'downUrl', value: downUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'url3', value: url3, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'webUrl', value: webUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'ggmc', value: ggmc, priority: 0, pageIndex: 1])
            String contentCandidateValue_0 = page.html.regex('[\\s\\S]+').get()
            candidatePool.add([fieldName: 'content', value: contentCandidateValue_0, priority: 0, pageIndex: 2])
            String releaseDateCandidateValue_1 = page.html.xpath('//div[@class="easysite-news-title"]//allText()').get()
            candidatePool.add([fieldName: 'releaseDate', value: releaseDateCandidateValue_1, priority: 0, pageIndex: 2])
            String releaseDateCandidateValue_2 = page.html.regex('\\d{4}-\\d{1,2}-\\d{1,2}').get()
            candidatePool.add([fieldName: 'releaseDate', value: releaseDateCandidateValue_2, priority: 0, pageIndex: 2])
            String downloadUrlCandidateValue_3 = "${page.getUrl()}"
            candidatePool.add([fieldName: 'downloadUrl', value: downloadUrlCandidateValue_3, priority: 0, pageIndex: 2])
            String filenameCandidateValue_4 = ParseToolsUtils.getFileName(downUrl)
            candidatePool.add([fieldName: 'filename', value: filenameCandidateValue_4, priority: 0, pageIndex: 2])
            Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
            candidatePool.each { candidate ->
                if (StringUtils.isBlank((String) candidate.value)) { return }
                def best = bestCandidateMap.get(candidate.fieldName)
                if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                    bestCandidateMap.put(candidate.fieldName, candidate)
                }
            }
            bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
            url1 = resultMap.get('url1') ?: url1
            url2 = resultMap.get('url2') ?: url2
            title = resultMap.get('title') ?: title
            downUrl = resultMap.get('downUrl') ?: downUrl
            url3 = resultMap.get('url3') ?: url3
            webUrl = resultMap.get('webUrl') ?: webUrl
            ggmc = resultMap.get('ggmc') ?: ggmc
            String content = resultMap.get('content')
            String releaseDate = resultMap.get('releaseDate')
            String downloadUrl = resultMap.get('downloadUrl')
            String filename = resultMap.get('filename')

            if (StringUtils.isBlank(content)) {
                return pageResult
            }
            String pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)

            // 返回采集到的数据
            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put('downUrl', Pair.of('下载地址', downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
            dataMap.put('releaseDate', Pair.of('原始发布时间', releaseDate))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downloadUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), filename))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

            // 上传正文HTML
            String uploadText = content
            if (StringUtils.isNotBlank(uploadText)) {
                pageResult.addOssFileByte(uploadText.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            }
            return pageResult
        }

        // ==========================================
        // 【附件页】提取附件名称 → 跳转到文件下载
        // ==========================================
        if (parser == "附件页") {
            page.setSkip(true)
            String url1 = page.request.getExtra("url1")
            String url2 = page.request.getExtra("url2")
            String title = page.request.getExtra("title")
            String downUrl = page.request.getExtra("downUrl")
            String url3 = page.request.getExtra("url3")
            String webUrl = page.request.getExtra("webUrl")
            String ggmc = page.request.getExtra("ggmc")

            Map<String, Object> resultMap = new LinkedHashMap<>()
            List<Map<String, Object>> candidatePool = new ArrayList<>()
            candidatePool.add([fieldName: 'url1', value: url1, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'url2', value: url2, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'title', value: title, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'downUrl', value: downUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'url3', value: url3, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'webUrl', value: webUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'ggmc', value: ggmc, priority: 0, pageIndex: 1])
            String fjmcCandidateValue_0 = page.html.xpath('//div[@class="easysite-news-title"]/h2/text()').get()
            candidatePool.add([fieldName: 'fjmc', value: fjmcCandidateValue_0, priority: 0, pageIndex: 3])
            // 从页面中提取文件下载链接
            String fileHrefCandidateValue_1 = page.html.xpath('//a[contains(@href,".pdf") or contains(@href,".xls") or contains(@href,".doc")]/@href').get()
            candidatePool.add([fieldName: 'fileHref', value: fileHrefCandidateValue_1, priority: 0, pageIndex: 3])
            Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
            candidatePool.each { candidate ->
                if (StringUtils.isBlank((String) candidate.value)) { return }
                def best = bestCandidateMap.get(candidate.fieldName)
                if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                    bestCandidateMap.put(candidate.fieldName, candidate)
                }
            }
            bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
            url1 = resultMap.get('url1') ?: url1
            url2 = resultMap.get('url2') ?: url2
            title = resultMap.get('title') ?: title
            downUrl = resultMap.get('downUrl') ?: downUrl
            url3 = resultMap.get('url3') ?: url3
            webUrl = resultMap.get('webUrl') ?: webUrl
            ggmc = resultMap.get('ggmc') ?: ggmc
            String fjmc = resultMap.get('fjmc')
            String fileHref = resultMap.get('fileHref')

            // 优先使用页面内提取的文件链接，否则使用列表页传入的下载地址
            String targetDownloadUrl = StringUtils.isNotBlank(fileHref)
                    ? ParseToolsUtils.resolve(page.getRequest().getUrl(), fileHref)
                    : downUrl

            if (StringUtils.isBlank(targetDownloadUrl)) {
                return pageResult
            }
            Map<String, Object> pageParams = new HashMap<>()
            pageParams.put("url1", url1)
            pageParams.put("url2", url2)
            pageParams.put("title", title)
            pageParams.put("downUrl", targetDownloadUrl)
            pageParams.put("url3", url3)
            pageParams.put("webUrl", webUrl)
            pageParams.put("ggmc", ggmc)
            pageParams.put("fjmc", fjmc)
            sendGet(pageResult, "文件下载", targetDownloadUrl, pageParams)
            return pageResult
        }

        // ==========================================
        // 【文件下载】下载文件并产出数据
        // ==========================================
        if (parser == "文件下载") {
            String url1 = page.request.getExtra("url1")
            String url2 = page.request.getExtra("url2")
            String title = page.request.getExtra("title")
            String downUrl = page.request.getExtra("downUrl")
            String url3 = page.request.getExtra("url3")
            String webUrl = page.request.getExtra("webUrl")
            String ggmc = page.request.getExtra("ggmc")
            String fjmc = page.request.getExtra("fjmc")

            Map<String, Object> resultMap = new LinkedHashMap<>()
            List<Map<String, Object>> candidatePool = new ArrayList<>()
            candidatePool.add([fieldName: 'url1', value: url1, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'url2', value: url2, priority: 0, pageIndex: 0])
            candidatePool.add([fieldName: 'title', value: title, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'downUrl', value: downUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'url3', value: url3, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'webUrl', value: webUrl, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'ggmc', value: ggmc, priority: 0, pageIndex: 1])
            candidatePool.add([fieldName: 'fjmc', value: fjmc, priority: 0, pageIndex: 3])
            String downloadUrlCandidateValue_0 = "${page.getUrl()}"
            candidatePool.add([fieldName: 'downloadUrl', value: downloadUrlCandidateValue_0, priority: 0, pageIndex: 4])
            String filenameCandidateValue_1 = ParseToolsUtils.getFileName(downUrl)
            candidatePool.add([fieldName: 'filename', value: filenameCandidateValue_1, priority: 0, pageIndex: 4])
            Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
            candidatePool.each { candidate ->
                if (StringUtils.isBlank((String) candidate.value)) { return }
                def best = bestCandidateMap.get(candidate.fieldName)
                if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                    bestCandidateMap.put(candidate.fieldName, candidate)
                }
            }
            bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
            url1 = resultMap.get('url1') ?: url1
            url2 = resultMap.get('url2') ?: url2
            title = resultMap.get('title') ?: title
            downUrl = resultMap.get('downUrl') ?: downUrl
            url3 = resultMap.get('url3') ?: url3
            webUrl = resultMap.get('webUrl') ?: webUrl
            ggmc = resultMap.get('ggmc') ?: ggmc
            fjmc = resultMap.get('fjmc') ?: fjmc
            String downloadUrl = resultMap.get('downloadUrl')
            String filename = resultMap.get('filename')

            String pushtime = ParseToolsUtils.formatPushTimeDateTime(null)

            // 返回采集到的数据
            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put('downUrl', Pair.of('下载地址', downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downloadUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), filename))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

            // 上传文件二进制
            pageResult.addOssFileByte(page.getBytes())
        }

        // 去重键
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.URL1.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.URL2.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey('downUrl')
                .addDedupKey(CrawlerDataCommonFieldsEnum.URL3.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.WEB_URL.getName())

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
