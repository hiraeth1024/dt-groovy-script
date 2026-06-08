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
 * NEW-D10-有色金属-SPDR黄金ETF行情
 * 起始请求链接: https://www.spdrgoldshares.com/assets/dynamic/GLD/GLD_US_archive_EN.csv
 * 起始请求方式: GET
 */
class YSJS_SPDR_HJETF_HQ implements IparseScript {



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

        final String WEB_URL = "https://www.spdrgoldshares.com/usa/historical-data/"
        final String PAGE_TITLE = "SPDR Gold Shares\\Historical Data"
        final String TITLE = "SPDR Gold Shares Historical Data"
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        String downloadUrl = "https://www.spdrgoldshares.com/assets/dynamic/GLD/GLD_US_archive_EN.csv"
        String pushtime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()
        addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), PAGE_TITLE, true)
        addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.TITLE, TITLE, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, WEB_URL, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushtime, true)
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

    private static String buildFileName(String downloadUrl) {
        String suffix = ParseToolsUtils.extractFirstGroup(downloadUrl, '\\.(csv|xls|xlsx|pdf|doc|docx)(?:\\?|$)')
        if (StringUtils.isBlank(suffix)) {
            suffix = "csv"
        }
        return "SPDR Gold Shares Historical Data.${suffix}"
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
