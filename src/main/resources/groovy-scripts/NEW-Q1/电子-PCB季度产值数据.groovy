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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * NEW-Q1-电子-PCB季度产值数据
 * 起始请求链接: https://static.wixstatic.com/ugd/950e51_6ec67f0d891a4be4bf32bc5e7fd8399d.pdf
 * 起始请求方式: GET
 */
class DZ_PCB_JDCZSJ implements IparseScript {


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

        final String WEB_URL = "https://www.prismark.com/printed-circuit-report-pcb"
        final String PAGE_TITLE = "Prismark\\WHAT WE DO\\PUBLICATIONS\\PRINTED CIRCUIT REPORT (PCB)"
        final String TITLE = "Prismark Printed Circuit Report Table of Contents"
        final String DEFAULT_RAW_INFO_DATE = "March 2026"
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        String downloadUrl = "https://static.wixstatic.com/ugd/950e51_6ec67f0d891a4be4bf32bc5e7fd8399d.pdf"
        String rawInfoDate = extractReleaseDate(page.getRawText())
        String pushTime = buildPushTime(rawInfoDate)
        pushTime = ParseToolsUtils.formatPushTimeDateTime(pushTime)
        String endDate = buildQuarterEndDate(rawInfoDate)

        Map<String, Pair<String, String>> dataMap = new HashMap<>()
        addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), PAGE_TITLE, true)
        addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
        addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "季", true)
        addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), rawInfoDate, false)
        addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), endDate, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.TITLE, TITLE, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, WEB_URL, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushTime, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DOWNLOADURL, downloadUrl, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILENAME, buildFileName(downloadUrl), false)

        List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
        dataMapList.add(dataMap)
        pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
        pageResult.addOssFileByte(page.getBytes())
        return pageResult
    }

    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, CrawlerDataCommonFieldsEnum field, String value, boolean dedup) {
        addDataMap(pageResult, dataMap, Pair.of(field.getName(), field.getAlias()), value, dedup)
    }

    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, cleanText(value)))
    }

    private static String extractReleaseDate(String rawText) {
        String releaseDate = ParseToolsUtils.extractFirstGroup(rawText, '(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}')
        if (StringUtils.isBlank(releaseDate)) {
            return "March 2026"
        }
        return cleanText(releaseDate)
    }

    private static String buildPushTime(String rawInfoDate) {
        if (StringUtils.isNotBlank(rawInfoDate)) {
            try {
                return LocalDateTime.parse(rawInfoDate + " 01 00:00:00", DateTimeFormatter.ofPattern("MMMM yyyy dd HH:mm:ss", Locale.ENGLISH))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    private static String buildQuarterEndDate(String rawInfoDate) {
        if (StringUtils.isNotBlank(rawInfoDate)) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(rawInfoDate + " 01 00:00:00", DateTimeFormatter.ofPattern("MMMM yyyy dd HH:mm:ss", Locale.ENGLISH))
                int quarterEndMonth = ((dateTime.getMonthValue() - 1).intdiv(3) + 1) * 3
                LocalDateTime quarterEnd = LocalDateTime.of(dateTime.getYear(), quarterEndMonth, 1, 0, 0).plusMonths(1).minusDays(1)
                return quarterEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (Exception ignored) {
            }
        }
        return ""
    }

    private static String buildFileName(String downloadUrl) {
        String suffix = ParseToolsUtils.extractFirstGroup(downloadUrl, '\\.(pdf|doc|docx|xls|xlsx)(?:\\?|$)')
        if (StringUtils.isBlank(suffix)) {
            suffix = "pdf"
        }
        return " Prismark Printed Circuit Report Table of Contents.${suffix}"
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
    }
}
