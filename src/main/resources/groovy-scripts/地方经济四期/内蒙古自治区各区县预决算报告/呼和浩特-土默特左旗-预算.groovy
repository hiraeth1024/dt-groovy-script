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
import us.codecraft.webmagic.selector.Json

import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.List
import java.util.Map

/**
 * 地方经济四期-内蒙古自治区各区县预决算报告-呼和浩特-土默特左旗-预算
 *


 * 起始请求链接: https://czt.nmg.gov.cn/yjs/business/basic/select?id=150121zf&type=2&p=1
 * 起始请求方式: GET
 */
class HHHT_TMTZQ_YS implements IparseScript {

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
        // 【第一阶段】列表页：解析预算报告列表
        // ==========================================
        if (parser == null) {
            page.setSkip(true)

            List<Map<String, Object>> nodes = filterRecentYearNodes(extractObjectList(page.getRawText(), "list"), 3)
            for (Map<String, Object> node : nodes) {
                String title = cleanText(node.get("title"))
                String id = cleanText(node.get("id"))
                String releaseDate = cleanText(node.get("publishTime"))

                if (StringUtils.isBlank(title) || StringUtils.isBlank(id)) {
                    continue
                }

                String detailUrl = "http://czt.nmg.gov.cn/yjs/business/page/content?contentId=${id}"
                Map<String, Object> pageParams = new HashMap<>()
                pageParams.put("title", title)
                pageParams.put("id", id)
                pageParams.put("releaseDate", releaseDate)
                pageParams.put("webUrl", "http://czt.nmg.gov.cn/yjs/business/page/list?type=2&basicId=150121zf")
                sendGet(pageResult, "详情页", detailUrl, pageParams)
            }
            return pageResult
        }

        // ==========================================
        // 【第二阶段】详情页：请求附件列表
        // ==========================================
        if (parser == "详情页") {
            String title = cleanText(page.request.getExtra("title"))
            String id = cleanText(page.request.getExtra("id"))
            String releaseDate = cleanText(page.request.getExtra("releaseDate"))
            String webUrl = cleanText(page.request.getExtra("webUrl"))
            String detailUrl = cleanText(page.request.getUrl())

            if (StringUtils.isBlank(title) || StringUtils.isBlank(id) || StringUtils.isBlank(detailUrl)) {
                return pageResult
            }

            String pageTitle = "地方经济四期\\内蒙古自治区各区县预决算报告\\呼和浩特\\土默特左旗\\预算"
            String pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            Map<String, Pair<String, String>> detailDataMap = buildFileDataMap(pageResult, pageTitle, title + "*地方经济",
                    releaseDate, pushtime, detailUrl, buildHtmlFileName(title), webUrl, detailUrl)
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            dataMapList.add(detailDataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            pageResult.addOssFileByte(buildDetailHtml(title, page.getRawText()).getBytes("UTF-8"))

            String fileDetailUrl = "https://czt.nmg.gov.cn/yjs/business/basic/result/${id}"
            Map<String, Object> pageParams = new HashMap<>()
            pageParams.put("title", title)
            pageParams.put("id", id)
            pageParams.put("releaseDate", releaseDate)
            pageParams.put("webUrl", webUrl)
            pageParams.put("detailUrl", detailUrl)
            sendGet(pageResult, "文件详情页", fileDetailUrl, pageParams)
            return pageResult
        }

        // ==========================================
        // 【第三阶段】文件详情页：生成附件下载请求
        // ==========================================
        if (parser == "文件详情页") {
            String title = cleanText(page.request.getExtra("title"))
            String id = cleanText(page.request.getExtra("id"))
            String releaseDate = cleanText(page.request.getExtra("releaseDate"))
            String webUrl = cleanText(page.request.getExtra("webUrl"))
            String detailUrl = cleanText(page.request.getExtra("detailUrl"))
            String pageTitle = "地方经济四期\\内蒙古自治区各区县预决算报告\\呼和浩特\\土默特左旗\\预算"
            String pushtime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            title = title +  "*地方经济"
            List<Map<String, Object>> nodes = extractObjectList(page.getRawText(), "files")
            for (Map<String, Object> node : nodes) {
                String fileId = cleanText(node.get("id"))
                String fileName = cleanText(node.get("name"))
                String fileSuffix = cleanText(node.get("suffix"))
                if (StringUtils.isBlank(fileId)) {
                    continue
                }

                String downloadUrl = "https://czt.nmg.gov.cn/yjs/business/file/download/${fileId}"
                String normalizedFileName = normalizeFileName(fileName, fileSuffix, title)
                Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()
                addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "年", true)
                addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), "内蒙古自治区呼和浩特市土默特左旗", true)
                addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
                addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始发布日期"), releaseDate, false)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushtime, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DOWNLOADURL, downloadUrl, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILEMD5, "", false)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.TITLE, title, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILENAME, normalizedFileName, false)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILESIZE, "", false)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, webUrl, true)
                addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrDFJJYXSJB", true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DETAILURL, detailUrl, true)
                addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILETYPE, "", false)

                sendAttachmentGet(pageResult, downloadUrl, dataMap, pageResult.getDeDupKeyList())
            }

            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第四阶段】附件页：产出文件元数据并上传文件
        // ==========================================
        if (parser == "附件页") {
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = page.request.getExtra("dataMap")
            List<String> dedupKeyList = page.request.getExtra("dedupKey")
            if (dataMap == null || dataMap.isEmpty()) {
                return pageResult
            }

            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            pageResult.addOssFileByte(page.getBytes())
            pageResult.setDeDupKeyList(dedupKeyList)
            return pageResult
        }

        return pageResult
    }




    /**
     * 创建 GET 子请求，并透传阶段参数。
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.setPriority(99)
        req.putExtra("trustAllCerts", true)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        pageResult.addChildRequest(req)
    }

    private static Map<String, Pair<String, String>> buildFileDataMap(ParsePageResult pageResult, String pageTitle, String title,
                                                                      String releaseDate, String pushtime, String downloadUrl,
                                                                      String fileName, String webUrl, String detailUrl) {
        Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()
        addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "年", true)
        addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), "内蒙古自治区呼和浩特市土默特左旗", true)
        addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
        addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始发布日期"), releaseDate, false)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.PUSHTIME, pushtime, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DOWNLOADURL, downloadUrl, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILEMD5, "", false)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.TITLE, title, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILENAME, fileName, false)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILESIZE, "", false)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.WEB_URL, webUrl, true)
        addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrDFJJYXSJB", true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.DETAILURL, detailUrl, true)
        addDataMap(pageResult, dataMap, CrawlerDataCommonFieldsEnum.FILETYPE, "", false)
        return dataMap
    }

    /**
     * 创建附件下载请求。附件页只负责产出数据并上传文件字节。
     */
    private static void sendAttachmentGet(ParsePageResult pageResult, String requestUrl, Map<String, Pair<String, String>> dataMap, List<String> dedupKeyList) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", "附件页")
        req.putExtra("dataMap", dataMap)
        req.putExtra("dedupKey", dedupKeyList)
        req.putExtra("trustAllCerts", true)
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
     * 添加自定义字段，并按需加入去重键。
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup && !pageResult.getDeDupKeyList().contains(field.left)) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, cleanText(value)))
    }

    private static String buildHtmlFileName(String title) {
        return normalizeFileName(title, "html", "报告详情页")
    }

    private static String buildDetailHtml(String title, String rawText) {
        String content = rawText ?: ""
        if (!content.toLowerCase().contains("<html")) {
            content = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>${escapeHtml(title)}</title></head><body><pre>${escapeHtml(content)}</pre></body></html>"
        }
        return content
    }

    private static String escapeHtml(Object input) {
        return (input == null ? "" : input.toString())
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }

    private static List<Map<String, Object>> extractObjectList(String rawText, String fieldName) {
        Object root = parseJsonRoot(rawText)
        if (!(root instanceof Map)) {
            return new ArrayList<>()
        }
        Object value = ((Map) root).get(fieldName)
        if (!(value instanceof Collection)) {
            return new ArrayList<>()
        }

        List<Map<String, Object>> resultList = new ArrayList<>()
        ((Collection) value).each { Object item ->
            if (item instanceof Map) {
                resultList.add((Map<String, Object>) item)
            } else {
                Object itemRoot = parseJsonRoot(cleanText(item))
                if (itemRoot instanceof Map) {
                    resultList.add((Map<String, Object>) itemRoot)
                }
            }
        }
        return resultList
    }

    private static Object parseJsonRoot(String rawText) {
        String text = cleanText(rawText)
        if (StringUtils.isBlank(text)) {
            return null
        }
        Object root
        try {
            root = new JsonSlurper().parseText(text)
        } catch (Exception ignored) {
            return null
        }
        int unwrapCount = 0
        while (root instanceof String && unwrapCount < 3) {
            String rootText = cleanText(root)
            if (!(rootText.startsWith("{") || rootText.startsWith("["))) {
                break
            }
            try {
                root = new JsonSlurper().parseText(rootText)
            } catch (Exception ignored) {
                break
            }
            unwrapCount++
        }
        return root
    }

    private static List<Map<String, Object>> filterRecentYearNodes(List<Map<String, Object>> nodes, int yearLimit) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>()
        }

        List<Integer> recentYears = nodes.collect { Map<String, Object> node ->
            extractReportYear(cleanText(node.get("title")), cleanText(node.get("publishTime")))
        }.findAll { Integer year ->
            year != null
        }.unique().sort(false) { Integer a, Integer b ->
            b <=> a
        }.take(yearLimit)

        if (recentYears.isEmpty()) {
            return nodes
        }

        return nodes.findAll { Map<String, Object> node ->
            Integer reportYear = extractReportYear(cleanText(node.get("title")), cleanText(node.get("publishTime")))
            reportYear != null && recentYears.contains(reportYear)
        }
    }

    private static Integer extractReportYear(String title, String releaseDate) {
        String year = ParseToolsUtils.extractFirstGroup(cleanText(title), '(20\\d{2})(?=\\s*(年|年度)?\\s*决算)')
        if (StringUtils.isBlank(year)) {
            year = ParseToolsUtils.extractFirstGroup(cleanText(title), '(20\\d{2})')
        }
        if (StringUtils.isBlank(year)) {
            year = ParseToolsUtils.extractFirstGroup(cleanText(releaseDate), '(20\\d{2})')
        }
        if (StringUtils.isBlank(year)) {
            return null
        }
        try {
            return Integer.parseInt(year)
        } catch (Exception ignored) {
        }
        return null
    }

    /**
     * 清理字段文本中的干扰字符。
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

    /**
     * 规范化附件文件名，确保上传服务能识别短后缀。
     */
    private static String normalizeFileName(String fileName, String fileSuffix, String title) {
        String name = cleanText(fileName)
        if (StringUtils.isBlank(name)) {
            name = cleanText(title)
        }
        name = name.replaceAll('[\\\\/:*?"<>|]', "_")
        String suffix = cleanText(fileSuffix).replace(".", "")
        if (StringUtils.isNotBlank(suffix) && !name.toLowerCase().endsWith("." + suffix.toLowerCase())) {
            name = "${name}.${suffix}"
        }
        if (StringUtils.isBlank(name)) {
            return "下载文件.pdf"
        }
        return name
    }
}
