import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Selectable

/**
 * 爬虫名称：中国人民银行-南京分行-统计数据-江苏金融机构信贷收支表
 * 结构：四层 — 入口 → 列表页 → 详情页 → 下载页
 * 起始URL：http://www.baidu.com
 */
class JiangsuJrjgxdzsb implements IparseScript {

    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        String parser = page.request.getExtra("parser")

        // ==========================================
        // 【第一阶段】入口 → 跳转到列表页
        // ==========================================
        if (parser == null) {
            String listUrl = "http://nanjing.pbc.gov.cn/nanjing/117532/index.html"
            sendGet(pageResult, "年份列表", listUrl)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第二阶段】列表页 → 提取各条目链接
        // ==========================================
        if (parser == "年份列表") {
            List<Selectable> nodes = page.html.xpath("//td[contains(@class,\"art_titjr\")]").nodes()
            if (nodes == null || nodes.isEmpty()) {
                return pageResult
            }

            for (def node : nodes) {
                String title = node.xpath("//a/text()").get()
                String href = node.xpath("//a/@href").get()

                if (StringUtils.isBlank(href) || StringUtils.isBlank(title)) {
                    continue
                }
                String detailUrl = ParseToolsUtils.resolve(page.getUrl().toString(), href)
                String releaseDate = ParseToolsUtils.extractFirstGroup(
                    node.toString(), '(\\d{4}-\\d{1,2}-\\d{1,2})')

                Request nextReq = new Request(detailUrl)
                nextReq.putExtra("parser", "详情页")
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra("title", title)
                nextReq.putExtra("detailUrl", detailUrl)
                if (StringUtils.isNotBlank(releaseDate)) {
                    nextReq.putExtra("releaseDate", releaseDate.trim())
                }
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)
            }
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第三阶段】详情页 → 提取文件下载链接
        // ==========================================
        if (parser == "详情页") {
            String title = page.request.getExtra("title")
            String detailUrl = page.request.getExtra("detailUrl")
            String releaseDate = page.request.getExtra("releaseDate")

            String fileHref = page.html.xpath("//div[@id=\"zoom\"]/p/a/@href").get()

            if (StringUtils.isBlank(fileHref)) {
                return pageResult
            }
            String downUrl = ParseToolsUtils.resolve(page.getUrl().toString(), fileHref)

            Request downReq = new Request(downUrl)
            downReq.putExtra("parser", "下载页")
            downReq.putExtra("trustAllCerts", true)
            downReq.putExtra("title", title)
            downReq.putExtra("detailUrl", detailUrl)
            downReq.putExtra("downUrl", downUrl)
            if (StringUtils.isNotBlank(releaseDate)) {
                downReq.putExtra("releaseDate", releaseDate)
            }
            downReq.setPriority(99)
            pageResult.addChildRequest(downReq)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第四阶段】下载页 → 产出数据并上传文件
        // ==========================================
        if (parser == "下载页") {
            String title = page.request.getExtra("title")
            String detailUrl = page.request.getExtra("detailUrl")
            String downUrl = page.request.getExtra("downUrl")
            String releaseDate = page.request.getExtra("releaseDate")

            if (StringUtils.isBlank(title) || StringUtils.isBlank(downUrl)) {
                return pageResult
            }

            String pushtime
            if (StringUtils.isNotBlank(releaseDate)) {
                pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            } else {
                pushtime = ParseToolsUtils.formatPushTimeDateTime(
                    java.time.LocalDateTime.now().toString())
            }

            String filename = ParseToolsUtils.getFileName(downUrl)

            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), filename))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), detailUrl))

            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)

            pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())

            pageResult.addOssFileByte(page.getBytes())
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================

    private static void sendGet(ParsePageResult pageResult,
                                String pageName,
                                String requestUrl,
                                Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value -> req.putExtra(key, value) }
        }
        pageResult.addChildRequest(req)
    }
}
