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

import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List
import java.util.Map

/**
 * 配置名称: DC-国家统计局-统计数据-最新发布-工业生产者出厂价格
 * 起始请求链接: http://www.stats.gov.cn/sj/zxfb/
 * 起始请求方式: GET
 */
class DC_最新发布_工业生产者出厂价格 implements IparseScript {

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
        // 【第一阶段】列表页
        // ==========================================
        if (parser == null) {
            page.setSkip(true)

            def nodes = page.html.xpath('//div[@class="list-content"]//ul/li').nodes()
            if (nodes == null || nodes.isEmpty()) {
                return pageResult
            }

            for (def node : nodes) {
                String title = node.xpath('//a[1]/@title').get()
                String href = node.xpath('//a/@href').get()
                String releaseDate = node.regex('\\d{4}[\\-\\.]\\d{1,2}[\\-\\.]\\d{1,2}').get()

                title = cleanText(title)
                if (StringUtils.isBlank(title) || StringUtils.isBlank(href)) {
                    continue
                }

                String detailUrl = ParseToolsUtils.resolve(page.request.url, href)
                Map<String, Object> pageParams = new LinkedHashMap<>()
                pageParams.put("title", title)
                pageParams.put("detailUrl", detailUrl)
                pageParams.put("releaseDate", releaseDate)
                sendGet(pageResult, "详情页", detailUrl, pageParams)
            }

            return pageResult
        }

        // ==========================================
        // 【第二阶段】详情页
        // ==========================================
        if (parser == "详情页") {
            String title = page.request.getExtra("title")
            String detailUrl = page.request.getExtra("detailUrl")
            String releaseDate = page.request.getExtra("releaseDate")

            title = cleanText(title)

            if (StringUtils.isBlank(title) || StringUtils.isBlank(detailUrl)) {
                return pageResult
            }

            String content = page.rawText
            if (StringUtils.isBlank(content)) {
                return pageResult
            }

            // 发布时间标准化
            String pushTime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            if (StringUtils.isBlank(pushTime)) {
                pushTime = ParseToolsUtils.formatPushTimeDateTime(ParseToolsUtils.smartGetPushTime(releaseDate))
            }

            Map<String, Pair<String, String>> dataMap = new LinkedHashMap<>()

            addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getName(), CrawlerDataCommonFieldsEnum.TITLE.getAlias()), title, true)
            addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), CrawlerDataCommonFieldsEnum.DETAILURL.getAlias()), detailUrl, true)
            addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), CrawlerDataCommonFieldsEnum.WEB_URL.getAlias()), detailUrl, false)
            addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias()), pushTime, false)
            addDataMap(pageResult, dataMap, Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getName(), CrawlerDataCommonFieldsEnum.FILENAME.getAlias()), ParseToolsUtils.getFileName(detailUrl), false)
            addDataMap(pageResult, dataMap, Pair.of("releaseDate", "发布日期"), releaseDate, true)
            addDataMap(pageResult, dataMap, Pair.of("announcementName", "公告名称"), title, false)
            addDataMap(pageResult, dataMap, Pair.of(ReleaseFrequency.DAILY.name, ReleaseFrequency.DAILY.alias), ReleaseFrequency.DAILY.alias, false)
            addDataMap(pageResult, dataMap, Pair.of(StatisticalArea.NATIONAL.name, StatisticalArea.NATIONAL.alias), StatisticalArea.NATIONAL.alias, false)


            List<Map<String, Pair<String, String>>> dataList = new ArrayList<>()
            dataList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

            // 上传页面正文内容
            if (StringUtils.isNotBlank(content)) {
                pageResult.addOssFileByte(page.getBytes())
            }
        }

        return pageResult
    }

    // ==========================================
    // 【工具方法】
    // ==========================================

    /**
     * 文本清理：去除首尾空格，将中文括号替换为英文括号
     * @param input 原始文本
     * @return 清理后的文本
     */
    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        return input.trim()
                .replace('（', '(')
                .replace('）', ')')
    }

    /**
     * 添加数据字段到结果集
     * @param field Pair.of('字段代码', '字段中文名')
     * @param value 字段值
     * @param dedup 是否作为去重字段
     */
    private static void addDataMap(ParsePageResult pageResult, Map<String, Pair<String, String>> dataMap, Pair<String, String> field, String value, boolean dedup) {
        if (dedup) {
            pageResult.addDedupKey(field.left)
        }
        dataMap.put(field.left, Pair.of(field.right, value))
    }

    /**
     * 创建GET子请求
     * @param pageName 下一阶段标识（parser参数值）
     * @param requestUrl 请求网址
     * @param pageParams 传递给下一阶段的参数Map
     */
    private static void sendGet(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        if (pageParams != null) {
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        req.setPriority(99)
        req.putExtra("trustAllCerts", true)
        pageResult.addChildRequest(req)
    }
    /**
     * 披露频率枚举
     */
    private enum ReleaseFrequency {

        DAILY("release_frequency", "日频"),
        WEEKLY("release_frequency", "周频"),
        MONTHLY("release_frequency", "月频"),
        QUARTERLY("release_frequency", "季频"),
        YEARLY("release_frequency", "年频")

        final String name
        final String alias

        ReleaseFrequency(String name, String alias) {
           this.name = name
            this.alias = alias
        }
    }

    /**
     * 统计区域枚举
     */
    private enum StatisticalArea {

        NATIONAL("statistic_area", "全国"),
        BEIJING("statistic_area", "北京"),
        SHANGHAI("statistic_area", "上海"),
        GUANGDONG("statistic_area", "广东")


        final String name
        final String alias

        StatisticalArea(String name, String alias) {
            this.name = name
            this.alias = alias
        }
    }


    /**
     * 指标单位枚举
     */
    private enum IndicatorUnit {

        YUAN("indicator_unit","元"),
        TEN_THOUSAND_YUAN("indicator_unit","万元"),
        HUNDRED_MILLION_YUAN("indicator_unit","亿元"),
        PERCENT("indicator_unit","%"),
        PERSON("indicator_unit","人"),
        TON("indicator_unit","吨")

        final String name
        final String alias

        IndicatorUnit(String name, String alias) {
            this.name = name
            this.alias = alias
        }
    }
    /**
     * 数据目标表
     */
    private enum DataTargetTable {

        XHGSJBHG("data_target_table", "usrXHGSJBHG"),
        XHGSJBQY("data_target_table", "usrXHGSJBQY"),
        XHGSJBHYCW("data_target_table", "usrXHGSJBHYCW"),
        XHGSJBHGMY("data_target_table", "usrXHGSJBHGMY"),
        HYSJB("data_target_table", "usrHYSJB"),
        DFJJYXSJB("data_target_table", "usrDFJJYXSJB"),
        GJHGSJB("data_target_table", "usrGJHGSJB")


        final String name
        final String alias

        DataTargetTable(String name, String alias) {
            this.name = name
            this.alias = alias
        }
    }

}
