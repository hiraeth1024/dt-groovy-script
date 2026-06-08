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
 * 爬虫名称：中国人民银行-上海总部-金融数据-上海市金融统计数据
 * 结构：三层 — 百度入口 → 列表页 → 下载页
 * 起始URL：http://www.baidu.com
 */
class ShanghaiJinrongTongji implements IparseScript {

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
            String listUrl = "https://shanghai.pbc.gov.cn/fzhshanghai/113592/index.html"
            Request req = new Request(listUrl)
            req.putExtra("parser", "列表页")

            req.putExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), listUrl)

            req.putExtra("trustAllCerts", true)
            req.setPriority(99)
            pageResult.addChildRequest(req)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第二阶段】列表页 → 提取下载链接
        // ==========================================
        if (parser == "列表页") {

            List<Selectable> all = page.html.xpath("//div[@id=\"twaincenter\"]//div[@role=\"region\"]//li").nodes()
            List<Selectable> nodes = all.take(12)
            if (nodes == null || nodes.isEmpty()) {
                return pageResult
            }

            for (Selectable node : nodes) {
                String title = node.xpath("//a/text()").get()
                String href = node.xpath("//a/@href").get()


                title = cleanText(title)
                if (StringUtils.isBlank(title) || StringUtils.isBlank(href)) {
                    continue
                }

                String itemUrl = ParseToolsUtils.resolve(page.getUrl().toString(), href)

                Request nextReq = new Request(itemUrl)
                nextReq.putExtra("parser", "详情页")
                nextReq.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), itemUrl)
                nextReq.putExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName(),page.request.getExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName()))
                nextReq.putExtra("trustAllCerts", true)
                nextReq.putExtra("title", title)
                nextReq.setPriority(99)
                pageResult.addChildRequest(nextReq)

            }
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第三阶段】详情页 → 下载页
        // ==========================================
        if (parser == "详情页") {
            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())


            String fileHref = page.html.xpath("//td[@class=\"content\"]//a/@href").get()
            String fileName = page.html.xpath("//td[@class=\"content\"]//a/text()").get()
            String pushTime = page.html.xpath("//span[@id=\"shijian\"]/text()").get()


            if (StringUtils.isBlank(fileHref) || StringUtils.isBlank(fileName) || StringUtils.isBlank(pushTime)) {
                return pageResult
            }
            pushTime = ParseToolsUtils.formatPushTimeDateTime(pushTime)

            String fileUrl = ParseToolsUtils.resolve(page.getUrl().toString(), fileHref)

            Request downReq = new Request(fileUrl)
            downReq.putExtra("parser", "下载页")
            downReq.putExtra("trustAllCerts", true)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), fileUrl)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), detailUrl)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), pushTime)
            downReq.putExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), page.request.getExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName()))

            downReq.setPriority(99)
            pageResult.addChildRequest(downReq)
            page.setSkip(true)
            return pageResult
        }

        // ==========================================
        // 【第四阶段】 下载页 → 提起文件
        // ==========================================
        if (parser == "下载页") {


            String title = page.request.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            String detailUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String fileUrl = page.request.getExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
            String pushTime = page.request.getExtra(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())

            if (StringUtils.isBlank(fileUrl) || StringUtils.isBlank(title)) {
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
            dataMap.put("release_frequency", Pair.of("披露频率", "月频"))
            dataMap.put("statistical_area", Pair.of("统计区域", "上海"))
            dataMap.put("data_target_table", Pair.of("数据目标表", "userXHGSJBHG"))
            dataMap.put("pageTitle", Pair.of("路径", "中国人民银行上海总部\\金融数据\\上海市金融统计数据"))
            dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.name,
                    Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.alias, page.request.getExtra(CrawlerDataCommonFieldsEnum.WEB_URL.getName())))

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


    // ==========================================
    // 【工具方法】
    // ==========================================

    /**
     * 文本清理：去除首尾空格，将中文括号替换为英文括号
     */
    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        return input.trim()
                .replace('（', '(')
                .replace('）', ')')
    }



}
