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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MSCIIndexScript implements IparseScript {

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

        // ==========================================
        // 【第1阶段】入口页
        // ==========================================
        if (parser == null) {
            if (page.getUrl().get().contains("getLatestWorldDate")) {
                parser = "最新日期页"
            } else {
                sendGet(pageResult, "最新日期页", "https://app2.msci.com/products/service/index/indexmaster/getLatestWorldDate", null)
                page.setSkip(true)
                return pageResult
            }
        }

        // ==========================================
        // 【第2阶段】获取最新交易日并构造表格列表请求
        // ==========================================
        if (parser == "最新日期页") {
            String latestDate = cleanText(getPageText(page))
            if (StringUtils.isBlank(latestDate)) {
                return pageResult
            }

            String requestDate = parseRequestDate(latestDate)
            String publishDate = parsePublishDate(requestDate)
            String pushTime = ParseToolsUtils.formatPushTimeDateTime(publishDate)

            List<Map<String, String>> tableList = buildTableList(requestDate)
            for (Map<String, String> tableItem : tableList) {
                Map<String, Object> pageParams = new HashMap<>()
                pageParams.put("requestDate", requestDate)
                pageParams.put("publishDate", publishDate)
                pageParams.put("pushTime", pushTime)
                pageParams.put("scopeName", tableItem.get("scopeName"))
                sendGet(pageResult, "表格列表页", tableItem.get("listUrl"), pageParams)
            }

            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第3阶段】表格列表页：逐行构造 Download Data 附件请求
        // ==========================================
        if (parser == "表格列表页") {
            String requestDate = page.request.getExtra("requestDate")
            String publishDate = page.request.getExtra("publishDate")
            String pushTime = page.request.getExtra("pushTime")
            String scopeName = page.request.getExtra("scopeName")
            String webUrl = "https://www-cdn.msci.com/web/msci/index-tools/end-of-day-index-data-search"
            String startDate = parseStartDate(requestDate)

            List<List<String>> rowList = parseCsv(getPageText(page))
            for (int i = 1; i < rowList.size(); i++) {
                List<String> row = rowList.get(i)
                if (row.size() < 2) {
                    continue
                }

                String indexName = cleanText(row.get(0))
                String indexCode = cleanText(row.get(1))
                if (StringUtils.isBlank(indexName) || StringUtils.isBlank(indexCode) || !indexCode.isNumber()) {
                    continue
                }

                String title = "MSCI指数-" + scopeName + "-" + indexName
                String downloadUrl = buildIndexDownloadUrl(indexCode, startDate, requestDate)
                String fileName = buildFileName(scopeName, indexName, indexCode, requestDate)

                Map<String, Pair<String, String>> dataMap = new HashMap<>()
                addDataMap(dataMap, CrawlerDataCommonFieldsEnum.TITLE, title)
                addDataMap(dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushTime)
                addDataMap(dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, webUrl)
                addDataMap(dataMap, CrawlerDataCommonFieldsEnum.DOWNLOADURL, downloadUrl)
                addDataMap(dataMap, CrawlerDataCommonFieldsEnum.FILENAME, fileName)
                addDataMap(dataMap, Pair.of("pageTitle", "路径"), "国际宏观\\美国\\其他\\MSCI指数")
                addDataMap(dataMap, Pair.of("raw_start_date", "原始开始日期"), startDate)
                addDataMap(dataMap, Pair.of("raw_end_date", "原始截止日期"), requestDate)
                addDataMap(dataMap, Pair.of("end_date", "截止日期"), publishDate)
                addDataMap(dataMap, Pair.of("data_target_table", "数据目标表"), "usrGJHGSJB")
                addDataMap(dataMap, Pair.of("release_frequency", "披露频率"), "日")

                Map<String, Object> pageParams = new HashMap<>()
                pageParams.put("dataMap", dataMap)
                sendGet(pageResult, "附件页", downloadUrl, pageParams)
            }

            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第4阶段】附件页
        // ==========================================
        if (parser == "附件页") {
            Map<String, Pair<String, String>> dataMap = page.request.getExtra("dataMap")
            if (dataMap == null || dataMap.isEmpty()) {
                return pageResult
            }

            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            pageResult.addOssFileByte(page.getBytes())
            pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                    .addDedupKey(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())
                    .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
            return pageResult
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================
    private static List<Map<String, String>> buildTableList(String requestDate) {
        List<Map<String, String>> tableList = new ArrayList<>()

        tableList.add(buildTableItem("Regional", buildClassificationUrl(requestDate, "Region")))
        tableList.add(buildTableItem("Country", buildClassificationUrl(requestDate, "Country")))
        tableList.add(buildTableItem("Sector", buildSectorListUrl()))

        return tableList
    }

    private static Map<String, String> buildTableItem(String scopeName, String listUrl) {
        Map<String, String> tableItem = new HashMap<>()
        tableItem.put("scopeName", scopeName)
        tableItem.put("listUrl", listUrl)
        return tableItem
    }

    private static String buildClassificationUrl(String requestDate, String scope) {
        return "https://app2.msci.com/products/service/index/indexmaster/description/indexes" +
                "?calc_date=${requestDate}" +
                "&index_variant=STRD" +
                "&currency_symbol=USD" +
                "&index_market=16384" +
                "&index_size=12" +
                "&index_style=None" +
                "&index_suite=C" +
                "&index_scope=${scope}"
    }

    private static String buildSectorListUrl() {
        return "https://app2.msci.com/products/service/index/indexmaster/description/indexes" +
                "?index_variant=STRD" +
                "&currency_symbol=USD" +
                "&sector_code=25" +
                "&index_period=-1" +
                "&index_size=12" +
                "&index_scope=Sector"
    }

    private static String buildIndexDownloadUrl(String indexCode, String startDate, String endDate) {
        return "https://app2.msci.com/products/service/index/indexmaster/downloadLevelData" +
                "?output=INDEX_LEVELS" +
                "&currency_symbol=USD" +
                "&index_variant=STRD" +
                "&start_date=${startDate}" +
                "&end_date=${endDate}" +
                "&data_frequency=DAILY" +
                "&baseValue=false" +
                "&index_codes=${indexCode}"
    }

    private static String buildFileName(String scopeName, String indexName, String indexCode, String requestDate) {
        String safeName = cleanText(indexName).replaceAll('[\\\\/:*?"<>|]', "_")
        return "MSCI指数-${scopeName}-${safeName}-${indexCode}-${requestDate}.xls"
    }

    private static List<List<String>> parseCsv(String csvText) {
        List<List<String>> rowList = new ArrayList<>()
        String text = csvText ?: ""
        List<String> currentRow = new ArrayList<>()
        StringBuilder currentCell = new StringBuilder()
        boolean inQuote = false

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i)
            if (ch == (34 as char)) {
                if (inQuote && i + 1 < text.length() && text.charAt(i + 1) == (34 as char)) {
                    currentCell.append(ch)
                    i++
                } else {
                    inQuote = !inQuote
                }
                continue
            }
            if (ch == (44 as char) && !inQuote) {
                currentRow.add(cleanText(currentCell.toString()))
                currentCell.setLength(0)
                continue
            }
            if ((ch == (10 as char) || ch == (13 as char)) && !inQuote) {
                if (ch == (13 as char) && i + 1 < text.length() && text.charAt(i + 1) == (10 as char)) {
                    i++
                }
                currentRow.add(cleanText(currentCell.toString()))
                currentCell.setLength(0)
                if (!currentRow.isEmpty() && currentRow.any { StringUtils.isNotBlank(it) }) {
                    rowList.add(currentRow)
                }
                currentRow = new ArrayList<>()
                continue
            }
            currentCell.append(ch)
        }

        currentRow.add(cleanText(currentCell.toString()))
        if (!currentRow.isEmpty() && currentRow.any { StringUtils.isNotBlank(it) }) {
            rowList.add(currentRow)
        }

        return rowList
    }

    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams) {
        Request req = new Request(requestUrl)
        req.setMethod("GET")
        req.addHeader("Accept-Encoding", "identity")
        req.addHeader("Accept", "text/csv,application/vnd.ms-excel,application/octet-stream,text/plain,*/*")
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.putExtra("timeoutSeconds", 180L)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        req.setPriority(99)
        pageResult.addChildRequest(req)
    }

    private static void addDataMap(Map<String, Pair<String, String>> dataMap, CrawlerDataCommonFieldsEnum field, String value) {
        dataMap.put(field.getName(), Pair.of(field.getAlias(), value))
    }

    private static void addDataMap(Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value) {
        dataMap.put(field.left, Pair.of(field.right, value))
    }

    private static String parseRequestDate(String dateText) {
        String cleanedDate = cleanText(dateText)
        if (cleanedDate ==~ /[0-9]{8}/) {
            return cleanedDate
        }
        return LocalDate.parse(cleanedDate, DateTimeFormatter.ofPattern("dd/MMM/yyyy", Locale.ENGLISH))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    }

    private static String parsePublishDate(String requestDate) {
        LocalDate date = LocalDate.parse(requestDate, DateTimeFormatter.ofPattern("yyyyMMdd"))
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00"
    }

    private static String parseStartDate(String requestDate) {
        LocalDate date = LocalDate.parse(requestDate, DateTimeFormatter.ofPattern("yyyyMMdd"))
        return date.minusYears(4).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    }

    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        return input.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replaceAll("\\s+", " ")
    }

    private static String getPageText(Page page) {
        String rawText = page.getRawText()
        if (StringUtils.isNotBlank(rawText)) {
            return rawText
        }
        byte[] pageBytes = page.getBytes()
        if (pageBytes == null || pageBytes.length == 0) {
            return ""
        }
        return new String(pageBytes, "UTF-8")
    }
}
