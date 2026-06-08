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

import javax.xml.crypto.Data

/**
 * 爬虫名称：中国人民银行-调查统计司-统计数据-企业商品价格（CGPI）指数-企业商品价格指数
 * 结构：五层 — 百度入口 → 年份列表 → 数据列表 → 详情页 → 下载页
 * 起始URL：http://www.baidu.com
 */

class dwadawd implements IparseScript {

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
        // 【第一阶段】百度入口 → 跳转到列表页
        // ==========================================
        if (parser == null) {
            String indexUrl = "https://www.pbc.gov.cn/diaochatongjisi/116219/116319/index.html"
            Request req = new Request(indexUrl)
            req.putExtra("parser", "年份列表")
            req.putExtra("trustAllCerts", true)
            req.setPriority(99)
            pageResult.addChildRequest(req)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第二阶段】年份列表 → 提取各年份链接
        // ==========================================
        if (parser == "年份列表") {
            List<Selectable> allTdDivs = page.html.xpath("//div[@class=\"portlet\"]//td/div").nodes()
            List<Selectable> nodes = allTdDivs.take(5)  // 取前5个，等效于 position()<6 但标准XPath方式


            if (nodes.isEmpty()) {
                return pageResult
            }

            for (Selectable node : nodes) {
                String yearHref = node.xpath("//a/@href").get()
                String yearTitle = node.xpath("//a/text()").get()

                if (StringUtils.isBlank(yearHref)) {
                    continue
                }
                String yearUrl = ParseToolsUtils.resolve(page.getUrl().toString(), yearHref)

                Request nextReq = new Request(yearUrl)
                nextReq.putExtra("parser", "数据列表")
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra("yearTitle", yearTitle)
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)
            }
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第三阶段】数据列表 → 提取各数据项链接
        // ==========================================
        if (parser == "数据列表") {
            String yearTitle = page.request.getExtra("yearTitle")

            List<Selectable> nodes = page.html.xpath("//div[@class=\"portlet\"]/div[2]/table[17]//tr/td").nodes()
            if (nodes.isEmpty()) {
                return pageResult
            }

            for (Selectable node : nodes) {
                String itemHref = node.xpath("//a/@href").get()
                String title = node.xpath("//a/text()").get()

                if (StringUtils.isBlank(itemHref)) {
                    continue
                }
                String itemUrl = ParseToolsUtils.resolve(page.getUrl().toString(), itemHref)

                Request nextReq = new Request(itemUrl)
                nextReq.putExtra("parser", "详情页")
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
                nextReq.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), itemUrl)
                nextReq.putExtra("yearTitle", yearTitle)
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)
            }
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第四阶段】详情页 → 提取文件下载链接
        // ==========================================
        if (parser == "详情页") {
            page = ParseToolsUtils.convertRelativeUrlsToAbsolute(page)

            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String yearTitle = page.request.getExtra("yearTitle")

            // 提取发布时间的备用逻辑
            String releaseDate = page.html.regex("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}[-/日]").get()

            // 提取文件下载链接
            String fileHref = page.html.xpath("//div[@id=\"con\"]/table[3]//tr/td[3]/a/@href").get()

            if (StringUtils.isBlank(fileHref)) {
                return pageResult
            }
            String fileUrl = ParseToolsUtils.resolve(page.getUrl().toString(), fileHref)

            Request downReq = new Request(fileUrl)
            downReq.putExtra("parser", "下载页")
            downReq.putExtra("trustAllCerts", true)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), detailUrl)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), fileUrl)
            downReq.putExtra("yearTitle", yearTitle)
            if (StringUtils.isNotBlank(releaseDate)) {
                downReq.putExtra("releaseDate", releaseDate)
            }
            downReq.setPriority(99)
            pageResult.addChildRequest(downReq)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第五阶段】下载页 → 产出数据与文件
        // ==========================================
        if (parser == "下载页") {
            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String fileUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
            String yearTitle = page.request.getExtra("yearTitle")
            String releaseDate = page.request.getExtra("releaseDate")
            title =  title + "\\" + yearTitle
            String pushTime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            if (StringUtils.isBlank(pushTime) ) {
                pushTime = ParseToolsUtils.getFileName(fileUrl).toString().substring(0,8)

            }
            if (StringUtils.isNoneBlank(pushTime)) {
                pushTime = parseDate(pushTime)
            }


            if (StringUtils.isBlank(fileUrl) || StringUtils.isBlank(title) || StringUtils.isBlank(pushTime)) {
                return pageResult
            }



            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()

            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), fileUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), ParseToolsUtils.getFileName(fileUrl)))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                    Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushTime))
            dataMap.put(DataTargetTable.XHGSJBHG.name,Pair.of(DataTargetTable.description, DataTargetTable.XHGSJBHG.alias))





            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            pageResult.addOssFileByte(page.getBytes())
        }

        // 去重键
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())

        return pageResult
    }


    private static String parseDate(String input) {

        // 提取年份和月份
        def year = input.substring(0, 4)
        def month = input.substring(4, 6)
        def day = input.substring(6, 8)

        // 返回格式化后的字符串
        return "${year}-${month}-${day}"
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

        static final String description = "数据目标表"
        final String name
        final String alias

        DataTargetTable(String name, String alias) {
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

        static final String description = "指标单位"
        final String name
        final String alias

        IndicatorUnit(String name, String alias) {
            this.name = name
            this.alias = alias
        }
    }



}
