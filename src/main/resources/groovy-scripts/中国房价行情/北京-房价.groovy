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
 * 爬虫名称：中国房价行情-北京-房价
 * 结构：两层 — 列表页 → 详情页
 * 起始URL：https://www.creprice.cn/urban/bj.html
 */
@Slf4j
class BjFangjia implements IparseScript {

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
        log.warn("=====================开始爬取 【第一阶段】列表页 → 提取各城区链接 =====================")
        // ==========================================
        // 【第一阶段】列表页 → 提取各城区链接
        // ==========================================
        log.warn("parser: ${parser}")
        if (parser == null) {
            log.warn("page html : ${page.html}") // 您的访问过于频繁，请输入验证码后继续浏览。
            List<Selectable> nodes = page.html.xpath("//*[contains(@class,\"selectsty1\")]//a").nodes()
            if (nodes == null || nodes.isEmpty()) {
                log.warn("未匹配到列表节点，页面URL: ${page.getUrl()}")
                return pageResult
            }

            for (def node : nodes) {
                String href = node.xpath("//@href").get()

                if (StringUtils.isBlank(href)) {
                    continue
                }
                String detailUrl = ParseToolsUtils.resolve(page.getUrl().toString(), href)

                Request nextReq = new Request(detailUrl)
                nextReq.putExtra("parser", "详情页")
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra("detailUrl", detailUrl)
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)
            }
            page.setSkip(true)
            return pageResult
        }
        log.warn("parser: ${parser}")

        // ==========================================
        // 【第二阶段】详情页 → 提取数据
        // ==========================================
        if (parser == "详情页") {
            log.warn("=====================开始爬取 【第二阶段】详情页 → 提取数据接 =====================")
            String detailUrl = page.request.getExtra("detailUrl")

            String pageTitle = page.html.xpath("//div[@id=\"content2\"]/h1/text()").get()
            String districtDate = page.html.xpath("//div[@class=\"fl\"]/div[2]/text()").get()
            String districtValue = page.html.xpath("//div[@class=\"fl\"]/div[3]/span/text()").get()

            if (StringUtils.isBlank(districtDate) || StringUtils.isBlank(districtValue)) {
                return pageResult
            }

            String pushtime = ParseToolsUtils.formatPushTimeDateTime(
                java.time.LocalDateTime.now().toString())

            Map<String, Pair<String, String>> dataMap = new HashMap<>()
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), cleanText(pageTitle)))
            dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
            dataMap.put("district_date", Pair.of("城区截止日期", cleanText(districtDate)))
            dataMap.put("district_value", Pair.of("城区数值", cleanText(districtValue)))

            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)

            pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
                .addDedupKey("district_date")
                .addDedupKey("district_value")
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
