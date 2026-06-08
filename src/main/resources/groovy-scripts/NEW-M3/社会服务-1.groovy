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

import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * NEW-M3-社会服务-1
 * 起始请求链接: https://www.dicj.gov.mo/web/cn/information/DadosEstat_mensal/index.html
 * 起始请求方式: GET
 */
class SHFW_1 implements IparseScript {

    @Override
    AntiPageRequest beforeDownloader(AntiPageRequest request) {
        AntiPageRequest requestTmp = new AntiPageRequest()
        Request requestUrl = new Request()
        requestUrl.putExtra("trustAllCerts", true)
        requestUrl.putExtra("timeoutSeconds", 180L)
        requestTmp.setRequest(requestUrl)
        return requestTmp
    }

    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        String parser = page.request.getExtra("parser")

        if (parser == null) {
            String currentUrl = page.getUrl().get()
            if (currentUrl.contains("report_cn.xml")) {
                String year = extractYear(currentUrl)
                parseDataPage(pageResult, page, currentUrl, currentUrl, year)
                return pageResult
            }

            String webUrl = "https://www.dicj.gov.mo/web/cn/information/DadosEstat_mensal/index.html"
            List<String> yearList = extractRecentYears(page, 1)
            if (yearList.isEmpty()) {
                page.setSkip(true)
                return pageResult
            }

            yearList.each { String year ->
                String dataUrl = "https://www.dicj.gov.mo/web/cn/information/DadosEstat_mensal/${year}/report_cn.xml?id=7"
                sendGet(pageResult, "数据页", dataUrl, [
                        webUrl: webUrl,
                        year  : year
                ])
            }
            page.setSkip(true)
            return pageResult
        }

        if (parser == "数据页") {
            String webUrl = page.request.getExtra("webUrl")
            String year = page.request.getExtra("year")
            parseDataPage(pageResult, page, webUrl, page.getUrl().get(), year)
            return pageResult
        }

        return pageResult
    }

    private static void parseDataPage(ParsePageResult pageResult, Page page, String webUrl, String dataUrl, String year) {
        String cleanYear = cleanText(year)
        if (StringUtils.isBlank(cleanYear)) {
            cleanYear = extractYear(dataUrl)
        }
        String pageTitle = "澳门特别行政区政府博彩监察协调局\\资料\\每月幸运博彩收入"
        String title = "每月幸运博彩收入*行业"

        List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
        List<Selectable> recordNodes = new Html(page.getRawText()).xpath("//RECORD|//record").nodes()
        Set<String> rowKeySet = new HashSet<>()
        addDedupKeys(pageResult)

        recordNodes.eachWithIndex { Selectable node, int index ->
            List<String> dataValues = new Html(node.toString()).xpath("//DATA/text()|//data/text()").all()
            if (dataValues == null || dataValues.size() < 7) {
                return
            }

            String month = buildMonthByRecordIndex(index)
            String endDate = buildMonthEndDate(cleanYear, month)
            if (StringUtils.isBlank(endDate)) {
                return
            }

            String monthName = buildChineseMonthName(month)
            String pushtime = buildPushTime(cleanYear, month)
            String currentYear = cleanYear

            addIndicatorData(dataMapList, rowKeySet, pageTitle, title, buildIndicatorName("月份毛收入", currentYear, monthName), "月份毛收入", webUrl, dataUrl, pushtime, endDate, dataValues[1])
            addIndicatorData(dataMapList, rowKeySet, pageTitle, title, buildIndicatorName("累计毛收入", currentYear, monthName), "累计毛收入", webUrl, dataUrl, pushtime, endDate, dataValues[4])
            addIndicatorData(dataMapList, rowKeySet, pageTitle, title, buildIndicatorName("月份毛收入同比", currentYear, monthName), "同比", webUrl, dataUrl, pushtime, endDate, dataValues[3])
            addIndicatorData(dataMapList, rowKeySet, pageTitle, title, buildIndicatorName("累计毛收入同比", currentYear, monthName), "同比", webUrl, dataUrl, pushtime, endDate, dataValues[6])
        }

        if (!dataMapList.isEmpty()) {
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
        }
    }

    private static void addIndicatorData(List<Map<String, Pair<String, String>>> dataMapList, Set<String> rowKeySet,
                                         String pageTitle, String title, String indicatorName, String statisticItem,
                                         String webUrl, String dataUrl, String pushtime, String endDate, String indicatorData) {
        String formattedData = formatIndicatorData(indicatorData)
        if (StringUtils.isBlank(formattedData)) {
            return
        }

        String rowKey = "${endDate}_${indicatorName}_${statisticItem}"
        if (rowKeySet.contains(rowKey)) {
            return
        }
        rowKeySet.add(rowKey)

        Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()
        dataMap.put("pageTitle", Pair.of("路径", pageTitle))
        dataMap.put("statistic_area", Pair.of("统计区域", "澳门"))
        dataMap.put("fileType", Pair.of("数据类型", "json"))
        dataMap.put("data_target_table", Pair.of("数据目标表", "usrHYSJB"))
        dataMap.put("release_frequency", Pair.of("披露频率", "月"))
        dataMap.put("end_date", Pair.of("截止日期", endDate))
        dataMap.put("indicator_name", Pair.of("指标名称", indicatorName))
        dataMap.put("indicator_data", Pair.of("指标数据", formattedData))
        dataMap.put("indicator_unit", Pair.of("指标单位", buildIndicatorUnit(statisticItem)))
        dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
        dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
        dataMap.put(CrawlerDataCommonFieldsEnum.DATA_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DATA_URL.getAlias(), dataUrl))
        dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
        dataMapList.add(dataMap)
    }

    private static void addDedupKeys(ParsePageResult pageResult) {
        pageResult.addDedupKey("pageTitle")
        pageResult.addDedupKey("data_target_table")
        pageResult.addDedupKey("release_frequency")
        pageResult.addDedupKey("end_date")
        pageResult.addDedupKey("indicator_name")
        pageResult.addDedupKey("backup_filed1")
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.WEB_URL.getName())
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.DATA_URL.getName())
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())
    }

    private static String buildIndicatorUnit(String statisticItem) {
        String item = cleanText(statisticItem)
        if (item.contains("同比")) {
            return "%"
        }
        if (item.contains("毛收入")) {
            return "百万澳门元"
        }
        return ""
    }

    private static List<String> extractRecentYears(Page page, int limit) {
        List<String> yearList = new Html(page.getRawText()).xpath("//a/text()").all()
                .collect { cleanText(it) }
                .findAll { it ==~ /\d{4}/ }
                .unique()
                .sort(false) { a, b -> b <=> a }
        if (yearList == null) {
            return []
        }
        return yearList.take(limit)
    }

    private static String extractYear(String url) {
        String year = ParseToolsUtils.extractFirstGroup(url, '/(20\\d{2})/')
        if (StringUtils.isBlank(year)) {
            year = ParseToolsUtils.extractFirstGroup(url, '(20\\d{2})')
        }
        return cleanText(year)
    }

    private static String buildMonthByRecordIndex(int index) {
        return String.valueOf(index % 12 + 1)
    }

    private static String buildChineseMonthName(String month) {
        String cleanMonth = cleanText(month).replaceAll('[^0-9]', '')
        if (StringUtils.isBlank(cleanMonth)) {
            return ""
        }
        List<String> monthNameList = ["一月份", "二月份", "三月份", "四月份", "五月份", "六月份", "七月份", "八月份", "九月份", "十月份", "十一月份", "十二月份"]
        try {
            int monthValue = Integer.parseInt(cleanMonth)
            if (monthValue >= 1 && monthValue <= 12) {
                return monthNameList[monthValue - 1]
            }
        } catch (Exception ignored) {
        }
        return ""
    }

    private static String buildIndicatorName(String statisticItem, String year, String monthName) {
        return cleanText(statisticItem)
    }

    private static String buildPushTime(String year, String month) {
        String cleanYear = cleanText(year)
        String cleanMonth = cleanText(month).replaceAll('[^0-9]', '')
        if (StringUtils.isBlank(cleanYear) || StringUtils.isBlank(cleanMonth)) {
            return ""
        }
        try {
            int monthValue = Integer.parseInt(cleanMonth)
            String pushDate = YearMonth.of(Integer.parseInt(cleanYear), monthValue).plusMonths(1).atDay(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            return ParseToolsUtils.formatPushTimeDateTime(pushDate)
        } catch (Exception ignored) {
        }
        return ""
    }

    private static String buildMonthEndDate(String year, String month) {
        String cleanYear = cleanText(year)
        String cleanMonth = cleanText(month).replaceAll('[^0-9]', '')
        if (StringUtils.isBlank(cleanYear) || StringUtils.isBlank(cleanMonth)) {
            return ""
        }
        try {
            int monthValue = Integer.parseInt(cleanMonth)
            return YearMonth.of(Integer.parseInt(cleanYear), monthValue).atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (Exception ignored) {
        }
        return ""
    }

    private static String formatIndicatorData(Object input) {
        String value = cleanText(input)
        if (StringUtils.isBlank(value) || value == "-") {
            return ""
        }
        value = value.replace(",", "")
                .replace("，", "")
                .replace("%", "")
                .replace("％", "")
                .replace("(", "")
                .replace(")", "")
                .trim()
        return value
    }

    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.putExtra("timeoutSeconds", 180L)
        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        pageResult.addChildRequest(req)
    }

    private static String cleanText(Object input) {
        if (input == null) {
            return ""
        }
        return input.toString()
                .trim()
                .replace("\n", "")
                .replace("\r", "")
                .replaceAll("\\s+", " ")
                .replace("（", "(")
                .replace("）", ")")
    }
}
