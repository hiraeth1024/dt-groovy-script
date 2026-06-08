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
import java.time.format.DateTimeFormatter

import java.time.LocalDate

class EnergyTrend implements IparseScript {

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
        // 【第一阶段】入口页：跳转到太阳能价格页
        // ==========================================
        if (parser == null) {
            String url = "https://www.energytrend.cn/solar-price.html"
            sendGet(pageResult, "详情页", url)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第二阶段】详情页：解析价格表格
        // ==========================================
        if (parser == "详情页") {
            String pageUrl = page.getUrl().get()
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            List<Selectable> tableBlocks = page.html.xpath("//div[@id='tab-content']//div[@class='left_tab']").nodes()

            for (Selectable tableBlock : tableBlocks) {
                String tableTitle = cleanText(tableBlock.xpath("//h4/text()").get())
                String rawInfoDate = cleanText(tableBlock.xpath("//div[@class=\"update\"]/text()").get())
                // 提取日期部分
                String dateStr = rawInfoDate.replace("更新", "").trim()

                LocalDate date = LocalDate.parse(
                        dateStr,
                        DateTimeFormatter.ofPattern("yyyy/MM/dd")
                )
                String result = date.atStartOfDay()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                String infoDate = ParseToolsUtils.formatPushTimeDateTime(result)
                String pageTitle = cleanText("EnergyTrend\\价格趋势\\太阳能价格")

                List<Selectable> rows = tableBlock.xpath("//table//tr").nodes()
                if (rows.size() <= 1) {
                    continue
                }

                List<String> headers = extractRowTexts(rows.get(0))
                for (Selectable row : rows.drop(1)) {
                    List<String> cells = extractRowTexts(row)
                    if (cells.size() < 3) {
                        continue
                    }

                    String projectName = cleanText(cells.get(1))
                    if (StringUtils.isBlank(projectName)) {
                        continue
                    }

                    for (int i = 2; i < cells.size() && i < headers.size(); i++) {
                        String metricName = cleanText(headers.get(i))
                        String metricValue = cleanIndicatorData(cells.get(i))
                        if (StringUtils.isBlank(metricName) || StringUtils.isBlank(metricValue)) {
                            continue
                        }

                        String indicatorName = "${tableTitle}\\${projectName}\\${metricName}"
                        String indicatorUnit = extractUnit(projectName, metricName)
                        Map<String, Pair<String, String>> dataMap = new HashMap<>()
                        addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "周", true)
                        addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
                        addDataMap(pageResult, dataMap, Pair.of("indicator_name", "指标名称"), indicatorName, true)
                        addDataMap(pageResult, dataMap, Pair.of("indicator_data", "指标数据"), metricValue, true)
                        addDataMap(pageResult, dataMap, Pair.of("indicator_unit", "指标单位"), indicatorUnit, true)
                        addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), dateStr, true)
                        addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.name, CrawlerDataCommonFieldsEnum.PUSHTIME.alias), infoDate, true)
                        addDataMap(pageResult, dataMap, Pair.of("raw_end_date", "原始截止日期"), dateStr, true)
                        addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), infoDate, true)
                        addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), "全球", true)
                        addDataMap(pageResult, dataMap, Pair.of("data_source_url", "数据来源链接"), pageUrl, true)
                        addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
                        dataMapList.add(dataMap)
                    }
                }
            }

            if (!dataMapList.isEmpty()) {
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            }
            return pageResult
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================
    /**
     * 创建 GET 子请求。
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)

        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        pageResult.addChildRequest(req)
    }

    /**
     * 提取一行内所有单元格文本。
     */
    private static List<String> extractRowTexts(Selectable row) {
        List<String> texts = new ArrayList<>()
        List<Selectable> cells = row.xpath("//td").nodes()
        for (Selectable cell : cells) {
            texts.add(cleanText(cell.xpath("//td/allText()").get()))
        }
        return texts
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
     * 从项目名中提取币种单位，涨跌幅固定为百分比。
     */
    private static String extractUnit(String projectName, String metricName) {
        if (metricName == "涨跌幅") {
            return "%"
        }
        String unit = ParseToolsUtils.extractFirstGroup(projectName, "\\(([^)]+)\\)")
        return cleanText(unit)
    }

    /**
     * 清洗指标数据，兼容涨跌幅中类似 "( 0.0 % )" 的展示文本。
     */
    private static String cleanIndicatorData(Object input) {
        String value = cleanText(input)
        String number = ParseToolsUtils.extractFirstGroup(value, "([-+]?\\d+(?:\\.\\d+)?)\\s*%")
        if (StringUtils.isNotBlank(number)) {
            return number
        }
        return value
    }

    /**
     * 清理文本中的换行、多余空格和图片残留文案。
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
}
