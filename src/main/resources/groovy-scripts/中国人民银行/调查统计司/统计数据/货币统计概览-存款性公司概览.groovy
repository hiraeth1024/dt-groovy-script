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

/**
 * 爬虫名称：中国人民银行-调查统计司-统计数据-货币统计概览-存款性公司概览
 * 结构：六层 — 百度入口 → 年份列表 → 数据列表 → 详情页 → 数据表页 → 表格数据提取
 * 起始URL：http://www.baidu.com
 */
class RmyhHbckxgsgl implements IparseScript {

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
            List<Selectable> nodes = page.html.xpath("//div[@class=\"portlet\"]//td/div[1]").nodes()
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

            List<Selectable> nodes = page.html.xpath("//div[@class=\"portlet\"]/div[2]/table[5]//tr/td").nodes()
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
        // 【第四阶段】详情页 → 提取数据表链接
        // ==========================================
        if (parser == "详情页") {
            page = ParseToolsUtils.convertRelativeUrlsToAbsolute(page)

            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String yearTitle = page.request.getExtra("yearTitle")

            String releaseDate = page.html.regex("\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}").get()

            // 提取数据表页面链接
            String dataHref = page.html.xpath("//table[@class=\"border_nr\"]//table[3]//td[2]/a/@href").get()

            if (StringUtils.isBlank(dataHref)) {
                return pageResult
            }
            String dataUrl = ParseToolsUtils.resolve(page.getUrl().toString(), dataHref)

            Request dataReq = new Request(dataUrl)
            dataReq.putExtra("parser", "数据表页")
            dataReq.putExtra("trustAllCerts", true)
            dataReq.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
            dataReq.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), detailUrl)
            dataReq.putExtra(CrawlerDataCommonFieldsEnum.DATA_URL.getName(), dataUrl)
            dataReq.putExtra("yearTitle", yearTitle)
            if (StringUtils.isNotBlank(releaseDate)) {
                dataReq.putExtra("releaseDate", releaseDate)
            }
            dataReq.setPriority(99)
            pageResult.addChildRequest(dataReq)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第五阶段】数据表页 → 提取表格数据
        // ==========================================
        if (parser == "数据表页") {
            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String dataUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DATA_URL.getName())
            String yearTitle = page.request.getExtra("yearTitle")
            String releaseDate = page.request.getExtra("releaseDate")

            List<Selectable> rows = page.html.xpath("//body/div/table//tr").nodes().drop(5)
            if (rows.isEmpty()) {
                return pageResult
            }

            String pushTime = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()

            for (Selectable row : rows) {
                // 提取各行单元格
                String col1 = row.xpath("//td[1]/allText()").get()
                String col2 = row.xpath("//td[2]/text()").get()
                String col3 = row.xpath("//td[3]/text()").get()
                String col4 = row.xpath("//td[4]/text()").get()
                String col5 = row.xpath("//td[5]/text()").get()
                String col6 = row.xpath("//td[6]/text()").get()
                String col7 = row.xpath("//td[7]/text()").get()
                String col8 = row.xpath("//td[8]/text()").get()
                String col9 = row.xpath("//td[9]/text()").get()
                String col10 = row.xpath("//td[10]/text()").get()
                String col11 = row.xpath("//td[11]/text()").get()
                String col12 = row.xpath("//td[12]/text()").get()
                String col13 = row.xpath("//td[13]/text()").get()
                String col14 = row.xpath("//td[14]/text()").get()

                // 跳过全空行
                if (StringUtils.isBlank(col1) && StringUtils.isBlank(col2) && StringUtils.isBlank(col3)) {
                    continue
                }

                Map<String, Pair<String, String>> dataMap = new HashMap<>()
                dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(),
                        Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
                dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(),
                        Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl))
                dataMap.put(CrawlerDataCommonFieldsEnum.DATA_URL.getName(),
                        Pair.of(CrawlerDataCommonFieldsEnum.DATA_URL.getAlias(), dataUrl))
                dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                        Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushTime))
                dataMap.put(CrawlerDataCommonFieldsEnum.SUBTITLE.getName(),
                        Pair.of(CrawlerDataCommonFieldsEnum.SUBTITLE.getAlias(), yearTitle))
                dataMap.put("col01", Pair.of("第一列", col1))
                dataMap.put("col02", Pair.of("第二列", col2))
                dataMap.put("col03", Pair.of("第三列", col3))
                dataMap.put("col04", Pair.of("第四列", col4))
                dataMap.put("col05", Pair.of("第五列", col5))
                dataMap.put("col06", Pair.of("第六列", col6))
                dataMap.put("col07", Pair.of("第七列", col7))
                dataMap.put("col08", Pair.of("第八列", col8))
                dataMap.put("col09", Pair.of("第九列", col9))
                dataMap.put("col10", Pair.of("第十列", col10))
                dataMap.put("col11", Pair.of("第十一列", col11))
                dataMap.put("col12", Pair.of("第十二列", col12))
                dataMap.put("col13", Pair.of("第十三列", col13))
                dataMap.put("col14", Pair.of("第十四列", col14))
                dataMapList.add(dataMap)
            }

            if (!dataMapList.isEmpty()) {
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            }
        }

        // 去重键
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DATA_URL.getName())
                .addDedupKey("col01")

        return pageResult
    }
}
