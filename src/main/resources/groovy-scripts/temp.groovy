import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import groovy.json.JsonSlurper
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 泰国央行-BIBOR平均利率(JSON API)
 *
 * 数据源: Bank of Thailand Open API
 * API: https://gateway.api.bot.or.th/BIBOR/v2/bibor_avg_rate/
 *
 * 说明:
 * 1. 本脚本直接采集 JSON API，不再下载 ReportViewer CSV。
 * 2. BOT API 通常需要 API Key，请通过环境变量或 JVM 参数配置 BOT_API_KEY。
 */
class ParseClass_2061726530539720706 implements IparseScript {

    private static final String WEB_URL = "https://app.bot.or.th/bibor/publish/publishRate.aspx?Lang=ENG"
    private static final String API_URL = "https://gateway.api.bot.or.th/BIBOR/v2/bibor_avg_rate/"
    private static final String PAGE_TITLE = "Bank of Thailand\\Statistics\\Financial Markets\\Bangkok Interbank Offered Rate (BIBOR)"
    private static final String TABLE_TITLE = "BIBOR Average Rate"
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @Override
    AntiPageRequest beforeDownloader(AntiPageRequest request) {
        AntiPageRequest requestTmp = new AntiPageRequest()
        Request requestUrl = new Request()
        requestUrl.putExtra("trustAllCerts", true)
        requestUrl.putExtra("timeoutSeconds", 180L)
        addApiHeaders(requestUrl)
        requestTmp.setRequest(requestUrl)
        return requestTmp
    }

    @Override
    ParsePageResult doParse(ParsePageRequest parsePageRequest) {
        Page page = parsePageRequest.page
        ParsePageResult pageResult = new ParsePageResult()
        String parser = page.request.getExtra("parser")

        if (parser == null && !page.getUrl().get().contains("/BIBOR/v2/")) {
            sendJsonRequest(pageResult)
            page.setSkip(true)
            return pageResult
        }

        parseJsonPage(pageResult, page)
        return pageResult
    }

    private static void sendJsonRequest(ParsePageResult pageResult) {
        LocalDate endDate = LocalDate.now()
        LocalDate startDate = endDate.minusYears(1)
        String requestUrl = API_URL + "?start_period=${startDate.format(DATE_FORMATTER)}&end_period=${endDate.format(DATE_FORMATTER)}"

        Request req = new Request(requestUrl)
        req.setPriority(99)
        req.putExtra("parser", "JSON页")
        req.putExtra("webUrl", WEB_URL)
        req.putExtra("trustAllCerts", true)
        req.putExtra("timeoutSeconds", 180L)
        addApiHeaders(req)
        pageResult.addChildRequest(req)
    }

    private static void parseJsonPage(ParsePageResult pageResult, Page page) {
        String rawText = page.getRawText()
        if (StringUtils.isBlank(rawText)) {
            return
        }

        def json
        try {
            json = new JsonSlurper().parseText(rawText)
        } catch (Exception ignored) {
            return
        }

        List<Map<String, Object>> records = collectRecordMaps(json)
        if (records.isEmpty()) {
            return
        }

        List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
        records.each { Map<String, Object> record ->
            addRecordData(dataMapList, record, page.getUrl().get())
        }

        if (!dataMapList.isEmpty()) {
            addDedupKeys(pageResult)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
        }
    }

    private static void addRecordData(List<Map<String, Pair<String, String>>> dataMapList,
                                      Map<String, Object> record, String dataUrl) {
        String rawEndDate = firstNotBlank(record, ["period", "date", "as_of_date", "effective_date", "rate_date", "data_date"])
        String endDate = convertDateToStandard(rawEndDate)
        if (StringUtils.isBlank(endDate)) {
            return
        }

        String rateName = firstNotBlank(record, ["term", "tenor", "period_type", "rate_type", "name", "item", "series_name"])
        String rateValue = firstNotBlank(record, ["rate", "interest_rate", "value", "rate_value"])
        if (StringUtils.isNotBlank(rateValue) && isNumeric(rateValue)) {
            addRateData(dataMapList, TABLE_TITLE, dataUrl, endDate, rawEndDate, safeRateName(rateName, "BIBOR Average Rate"), rateValue)
            return
        }

        record.each { String key, Object value ->
            if (isDateKey(key) || isNameKey(key) || value instanceof Map || value instanceof Collection) {
                return
            }
            String indicatorData = cleanText(value)
            if (!isNumeric(indicatorData)) {
                return
            }
            addRateData(dataMapList, TABLE_TITLE, dataUrl, endDate, rawEndDate, key, indicatorData)
        }
    }

    private static List<Map<String, Object>> collectRecordMaps(Object node) {
        List<Map<String, Object>> records = new ArrayList<>()
        collectRecordMaps(node, records)
        return records
    }

    private static void collectRecordMaps(Object node, List<Map<String, Object>> records) {
        if (node instanceof Map) {
            Map<String, Object> map = node as Map<String, Object>
            if (looksLikeDataRecord(map)) {
                records.add(map)
                return
            }
            map.values().each { Object value ->
                collectRecordMaps(value, records)
            }
            return
        }
        if (node instanceof Collection) {
            node.each { Object value ->
                collectRecordMaps(value, records)
            }
        }
    }

    private static boolean looksLikeDataRecord(Map<String, Object> map) {
        boolean hasDate = map.keySet().any { String key -> isDateKey(key) && StringUtils.isNotBlank(convertDateToStandard(map.get(key))) }
        boolean hasNumeric = map.any { String key, Object value ->
            !isDateKey(key) && !(value instanceof Map) && !(value instanceof Collection) && isNumeric(cleanText(value))
        }
        return hasDate && hasNumeric
    }

    private static void addRateData(List<Map<String, Pair<String, String>>> dataMapList, String tableTitle,
                                    String dataUrl, String endDate, String rawEndDate,
                                    String rateName, String indicatorData) {
        String pushtime = ParseToolsUtils.formatPushTimeDateTime(endDate)
        String title = "Bangkok Interbank Offered Rate (BIBOR)*${tableTitle}"

        Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()
        addDataMap(dataMap, Pair.of("pageTitle", "路径"), PAGE_TITLE)
        addDataMap(dataMap, Pair.of("data_target_table", "数据目标表"), "usrGJHGSJB")
        addDataMap(dataMap, Pair.of("release_frequency", "披露频率"), "日")
        addDataMap(dataMap, Pair.of("end_date", "截止日期"), pushtime)
        addDataMap(dataMap, Pair.of("indicator_name", "指标名称"), "${tableTitle}\\${safeRateName(rateName, 'BIBOR Average Rate')}")
        addDataMap(dataMap, Pair.of("indicator_data", "指标数据"), cleanNumber(indicatorData))
        addDataMap(dataMap, Pair.of("indicator_unit", "指标单位"), "%")
        addDataMap(dataMap, CrawlerDataCommonFieldsEnum.TITLE, title)
        addDataMap(dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushtime)
        addDataMap(dataMap, Pair.of("raw_end_date", "原始截止日期"), cleanText(rawEndDate))
        addDataMap(dataMap, Pair.of("fileType", "文件类型"), "json")
        addDataMap(dataMap, Pair.of("statistic_area", "统计区域"), "泰国")
        addDataMap(dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, WEB_URL)
        addDataMap(dataMap, CrawlerDataCommonFieldsEnum.DATA_URL, dataUrl)
        dataMapList.add(dataMap)
    }

    private static void addApiHeaders(Request request) {
        request.addHeader("Accept", "application/json")
        request.addHeader("Content-Type", "application/json; charset=UTF-8")
        String apiKey = getApiKey()
        if (StringUtils.isNotBlank(apiKey)) {
            request.addHeader("Authorization", apiKey)
            request.addHeader("X-IBM-Client-Id", apiKey)
        }
    }

    private static String getApiKey() {
        String apiKey = System.getProperty("BOT_API_KEY")
        if (StringUtils.isBlank(apiKey)) {
            apiKey = System.getenv("BOT_API_KEY")
        }
        return cleanText(apiKey)
    }

    private static String firstNotBlank(Map<String, Object> record, List<String> keys) {
        for (String key : keys) {
            Object value = findValueIgnoreCase(record, key)
            String text = cleanText(value)
            if (StringUtils.isNotBlank(text)) {
                return text
            }
        }
        return ""
    }

    private static Object findValueIgnoreCase(Map<String, Object> record, String targetKey) {
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.key != null && entry.key.equalsIgnoreCase(targetKey)) {
                return entry.value
            }
        }
        return null
    }

    private static boolean isDateKey(String key) {
        String lowerKey = cleanText(key).toLowerCase()
        return lowerKey == "period"
                || lowerKey == "date"
                || lowerKey.endsWith("_date")
                || lowerKey.contains("period")
    }

    private static boolean isNameKey(String key) {
        String lowerKey = cleanText(key).toLowerCase()
        return lowerKey == "term"
                || lowerKey == "tenor"
                || lowerKey.contains("name")
                || lowerKey.contains("type")
                || lowerKey.contains("desc")
    }

    private static String safeRateName(String rateName, String defaultName) {
        String name = cleanText(rateName)
        return StringUtils.isNotBlank(name) ? name : defaultName
    }

    private static String convertDateToStandard(Object rawDate) {
        String date = cleanText(rawDate)
        if (StringUtils.isBlank(date)) {
            return ""
        }
        def matcher = (date =~ /(\d{4})-(\d{1,2})-(\d{1,2})/)
        if (matcher.find()) {
            return "${matcher.group(1)}-${pad2(matcher.group(2))}-${pad2(matcher.group(3))}"
        }
        matcher = (date =~ /(\d{4})\/(\d{1,2})\/(\d{1,2})/)
        if (matcher.find()) {
            return "${matcher.group(1)}-${pad2(matcher.group(2))}-${pad2(matcher.group(3))}"
        }
        matcher = (date =~ /(\d{1,2})\/(\d{1,2})\/(\d{4})/)
        if (matcher.find()) {
            return "${matcher.group(3)}-${pad2(matcher.group(2))}-${pad2(matcher.group(1))}"
        }
        return ""
    }

    private static String pad2(String value) {
        return String.format("%02d", Integer.parseInt(value))
    }

    private static boolean isNumeric(String str) {
        if (StringUtils.isBlank(str)) {
            return false
        }
        try {
            new BigDecimal(cleanNumber(str))
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    private static String cleanNumber(Object input) {
        return cleanText(input).replace(",", "").replace("%", "").trim()
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

    private static void addDedupKeys(ParsePageResult pageResult) {
        pageResult.addDedupKey("pageTitle")
        pageResult.addDedupKey("data_target_table")
        pageResult.addDedupKey("release_frequency")
        pageResult.addDedupKey("end_date")
        pageResult.addDedupKey("indicator_name")
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
        pageResult.addDedupKey("statistic_area")
    }

    private static void addDataMap(Map<String, Pair<String, String>> dataMap, CrawlerDataCommonFieldsEnum field, String value) {
        addDataMap(dataMap, Pair.of(field.getName(), field.getAlias()), value)
    }

    private static void addDataMap(Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value) {
        dataMap.put(field.left, Pair.of(field.right, cleanText(value)))
    }
}
