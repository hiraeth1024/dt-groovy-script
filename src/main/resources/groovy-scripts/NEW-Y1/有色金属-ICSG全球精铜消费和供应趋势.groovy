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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * NEW-Y1-有色金属-ICSG全球精铜消费和供应趋势
 *
 * 起始请求链接: https://icsg.org/press-releases/
 * 起始请求方式: GET
 */
class ICSG_JT_XFGYQS implements IparseScript {

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
            parser = "详情页"
            page.setSkip(true)
        }

        // ==========================================
        // 【第二阶段】列表页：提取新闻稿下载链接
        // ==========================================
        if (parser == "详情页") {

            String webUrl = page.getUrl().get()
            List<Selectable> nodes = page.html.xpath("//div[@class='row']/div//h3[contains(@class,'media-heading')]").nodes()
            int index = 0
            for (Selectable node : nodes) {
                index++


                String title = cleanTitle(node.xpath("//h3/allText()").get())
                String downUrl = cleanText(node.xpath("//a/@data-downloadurl").get())
                if (StringUtils.isBlank(downUrl)) {
                    downUrl = cleanText(node.xpath("//a/@href").get())
                }
                if (StringUtils.isBlank(title) || StringUtils.isBlank(downUrl)) {
                    continue
                }

                String downloadUrl = ParseToolsUtils.resolve(webUrl, downUrl)
                String releaseDate = parseReleaseDate(title)
                if (StringUtils.isBlank(releaseDate)) {
                    releaseDate = parseReleaseDate(downloadUrl)
                }
                String pushtime = formatPushTime(releaseDate)
                String endDate = formatMonthEndDate(releaseDate)
                String fileName = cleanText(getUrlFileName(downloadUrl))
                if (StringUtils.isBlank(fileName) || !hasValidFileExtension(fileName)) {
                    fileName = buildFileName(title)
                }

                Map<String, Pair<String, String>> dataMap = new HashMap<>()
                addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), "INTERNATIONAL COPPER STUDY GROUP\\NEWS\\Press Release\\Recent Press Releases", true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, webUrl, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.TITLE, title, true)
                addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), releaseDate, false)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushtime, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DOWNLOADURL, downloadUrl, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILENAME, fileName, false)
                addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
                addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "月", true)
                addDataMap(pageResult, dataMap, Pair.of("raw_end_date", "原始截止日期"), releaseDate, true)
                addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), endDate, true)
                sendAttachmentGet(pageResult, downloadUrl, dataMap)
            }
            return pageResult
        }

        // ==========================================
        // 【第三阶段】附件页：上传文件
        // ==========================================
        if (parser == "附件页") {
            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = page.request.getExtra("dataMap")
            List<String> dedupKeyList = page.request.getExtra("dedupKey")
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)
            pageResult.addOssFileByte(page.getBytes())
            pageResult.setDeDupKeyList(dedupKeyList)
            return pageResult
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================
    /**
     * 创建 GET 子请求，并透传阶段参数。
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.setMethod("GET")
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.putExtra("timeoutSeconds", 600L)
        req.addHeader("Referer", "https://icsg.org/press-releases/")
        req.addHeader("Accept", "application/pdf,application/octet-stream,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        pageResult.addChildRequest(req)
    }

    /**
     * 创建附件下载请求。附件页只负责上传 page.getBytes()。
     */
    private static void sendAttachmentGet(ParsePageResult pageResult, String requestUrl, Map<String, Pair<String, String>> dataMap) {
        Request req = new Request(requestUrl)
        req.setMethod("GET")
        req.putExtra("parser", "附件页")
        req.putExtra("dataMap", dataMap)
        req.putExtra("dedupKey", pageResult.getDeDupKeyList())
        req.putExtra("trustAllCerts", true)
        req.putExtra("timeoutSeconds", 600L)
        req.addHeader("Referer", "https://icsg.org/press-releases/")
        req.addHeader("Accept", "application/pdf,application/octet-stream,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        req.setPriority(99)
        pageResult.addChildRequest(req)
    }

    /**
     * 添加公共字段枚举对应的数据。
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, CrawlerDataCommonFieldsEnum field, String value, boolean dedup) {
        addDataMap(pageResult, dataMap, Pair.of(field.getName(), field.getAlias()), value, dedup)
    }

    /**
     * 添加字段并按需加入去重键。
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, cleanText(value)))
    }

    /**
     * 从新闻稿标题中提取发布时间。
     */
    private static String parseReleaseDate(String title) {
        String releaseDate = ParseToolsUtils.extractFirstGroup(title, "([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})")
        if (StringUtils.isBlank(releaseDate)) {
            releaseDate = ParseToolsUtils.extractFirstGroup(title, "(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})")
        }
        if (StringUtils.isBlank(releaseDate)) {
            releaseDate = ParseToolsUtils.extractFirstGroup(title, "(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})")
        }
        if (StringUtils.isBlank(releaseDate)) {
            releaseDate = ParseToolsUtils.extractFirstGroup(title, "(\\d{4}_\\d{1,2}_\\d{1,2})")
        }
        if (StringUtils.isBlank(releaseDate)) {
            releaseDate = ParseToolsUtils.extractFirstGroup(title, "(\\d{4}\\.\\d{1,2}\\.\\d{1,2})")
        }
        if (StringUtils.isBlank(releaseDate)) {
            releaseDate = ParseToolsUtils.extractFirstGroup(title, "(\\d{4}[-_/\\.\\s]\\d{1,2})")
        }
        return cleanText(releaseDate)
    }

    /**
     * 将标题中的日期转换为入库发布时间。
     */
    private static String formatPushTime(String releaseDate) {
        LocalDate localDate = parseLocalDate(releaseDate)
        if (localDate == null) {
            return ""
        }
        return localDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    /**
     * 根据发布日期生成当月最后一天作为截止日期。
     */
    private static String formatMonthEndDate(String releaseDate) {
        LocalDate localDate = parseLocalDate(releaseDate)
        if (localDate == null) {
            return ""
        }
        return localDate.withDayOfMonth(localDate.lengthOfMonth())
                .atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    /**
     * 兼容 ICSG 标题和下载链接中的多种日期格式。
     */
    private static LocalDate parseLocalDate(String releaseDate) {
        String date = cleanText(releaseDate)
        if (StringUtils.isBlank(date)) {
            return null
        }
        if (date ==~ /\d{4}[-_\.]\d{1,2}[-_\.]\d{1,2}/) {
            String normalizedDate = date.replace("_", "-").replace(".", "-")
            return LocalDate.parse(normalizedDate, DateTimeFormatter.ofPattern("yyyy-M-d"))
        }
        if (date ==~ /\d{4}[-_.\s]\d{1,2}/) {
            String[] parts = date.split("[-_\\.\\s]")
            String month = parts[1].padLeft(2, "0")
            return LocalDate.parse("${parts[0]}-${month}-01")
        }
        return null
    }

    /**
     * 清理新闻稿标题，去掉按钮文本。
     */
    private static String cleanTitle(Object input) {
        return cleanText(input).replaceAll("\\s+Download\$", "")
    }

    /**
     * 使用标题兜底生成文件名。
     */
    private static String buildFileName(String title) {
        String safeTitle = cleanText(title).replaceAll('[\\\\/:*?"<>|.]', "_")
        if (StringUtils.isBlank(safeTitle)) {
            return "ICSG_press_release.pdf"
        }
        return "${safeTitle}.pdf"
    }

    /**
     * 清理文本中的换行和多余空格。
     */
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

    private static String getUrlFileName(String url) {
        if (url == null || url.isEmpty()) {
            return ""
        }

        String cleanUrl = url.split("\\?")[0]

        int lastSlashIndex = cleanUrl.lastIndexOf("/")
        if (lastSlashIndex == -1 || lastSlashIndex == cleanUrl.length() - 1) {
            return ""
        }

        return cleanUrl.substring(lastSlashIndex + 1)
    }

    /**
     * 校验文件名是否包含上传服务可接受的短后缀。
     */
    private static boolean hasValidFileExtension(String fileName) {
        String name = cleanText(fileName)
        int dotIndex = name.lastIndexOf(".")
        if (dotIndex <= 0 || dotIndex == name.length() - 1) {
            return false
        }
        String ext = name.substring(dotIndex + 1)
        return ext ==~ /[A-Za-z0-9]{1,8}/
    }
}
