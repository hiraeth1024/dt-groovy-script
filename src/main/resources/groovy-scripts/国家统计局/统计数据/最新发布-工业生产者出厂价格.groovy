import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
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
 * 爬虫名称：国家统计局-统计数据-最新发布-工业生产者出厂价格
 * 结构：两层 — 列表页 → 详情页（表格数据提取）
 * 起始URL：http://www.stats.gov.cn/sj/zxfb/
 */
@Slf4j
class ZxfbGysccjg implements IparseScript {

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
        // 【第一阶段】列表页 → 提取各发布条目链接
        // ==========================================
        if (parser == null) {
            List<Selectable> nodes = page.html.xpath("//div[contains(@class,\"list-content\")]//ul/li").nodes()
            if (nodes == null || nodes.isEmpty()) {
                return pageResult
            }

            for (def node : nodes) {
                String pageTitle = node.xpath("//a[1]/@title").get()
                String webUrl = node.xpath("//a/@href").get()

                if (StringUtils.isBlank(pageTitle)) {
                    continue
                }
                if (StringUtils.isBlank(webUrl)) {
                    continue
                }
                String detailUrl = ParseToolsUtils.resolve(page.getUrl().toString(), webUrl)

                Request nextReq = new Request(detailUrl)
                nextReq.putExtra("parser", "详情页")
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra("title", cleanText(pageTitle))
                nextReq.putExtra("detailUrl", detailUrl)
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)
            }
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第二阶段】详情页 → 提取表格数据
        // ==========================================
        if (parser == "详情页") {
            String title = page.request.getExtra("title")
            String detailUrl = page.request.getExtra("detailUrl")

            page = ParseToolsUtils.convertRelativeUrlsToAbsolute(page)

            List<Selectable> rows = page.html.xpath("//table[contains(@class,\"trs_word_table\")][1]//tr").nodes()
            if (rows == null || rows.isEmpty()) {
                return pageResult
            }

            log.warn("=================pushtime======================")
            String pushtime = ParseToolsUtils.formatPushTimeDateTime(
                java.time.LocalDate.now().toString())

            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()

            for (def row : rows) {
                String indicatorName = row.xpath("//td[1]/allText()").get()
                String momChange = row.xpath("//td[3]/allText()").get()
                String yoyChange = row.xpath("//td[4]/allText()").get()
                String yoyCumulative = row.xpath("//td[5]/allText()").get()

                if (StringUtils.isBlank(indicatorName)) {
                    continue
                }

                Map<String, Pair<String, String>> dataMap = new HashMap<>()
                dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
                dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl))
                dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
                dataMap.put("indicator_name", Pair.of("指标名称", cleanText(indicatorName)))
                dataMap.put("indicator_mom", Pair.of("本期价格", cleanText(momChange)))
                dataMap.put("indicator_yoy", Pair.of("比上期价格涨跌（元）", cleanText(yoyChange)))
                dataMap.put("indicator_yoy_cum", Pair.of("涨跌幅(%)", cleanText(yoyCumulative)))

                dataMapList.add(dataMap)
            }

            if (!dataMapList.isEmpty()) {
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            }

            pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
                .addDedupKey("indicator_name")
                .addDedupKey("indicator_mom")
                .addDedupKey("indicator_yoy")
                .addDedupKey("indicator_yoy_cum")
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================

    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        return input.trim()
            .replace('（', '(')
            .replace('）', ')')
    }
}
