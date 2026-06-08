import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.Pair
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.StringUtils
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request

/**
 * 爬虫名称：企业商品价格CGPI指数
 * Url:https://www.pbc.gov.cn/diaochatongjisi/116219/116319/index.html
 */
@Slf4j
class ParseClass_2049678314000814082 implements IparseScript {
    private static String cleanText(String input) {
        if (input == null) {
            return ""
        }
        // 1. 去除首尾空格
        // 2. 替换中文左括号
        // 3. 替换中文右括号
        return input.trim()
                .replace('（', '(')
                .replace('）', ')')
    }
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
        Page page = request.getPage();
        def parser = page.getRequest().getExtra("parser")
        ParsePageResult pageResult = new ParsePageResult();


        if (parser == null) {
            def detailUrls  = page.getHtml().xpath("//div[@aria-label=\"栏目信息内容区\"]//td/div").nodes()
//            def detailUrls  = page.html.xpath("//div[@class=\"portlet\"]//td/div").nodes()
            log.warn("detailUrls: ${detailUrls.size()}")
            for(int i = 0;i < 5; i++){
                def sj = detailUrls.get(i)
                // 解析每个子链接
                String url = sj.xpath("//a/@href").get()
                String nextUrl = "https://www.pbc.gov.cn/" + url

                Request req = new Request(nextUrl)
                req.putExtra("parser", "列表页")
                pageResult.addChildRequest(req)
            }

        } else if (parser == "列表页") {
            String TT = ParseToolsUtils.getXpathText(page, "//meta[@name=\"ColumnDescription\"]/@content")
            def NextUrls = page.getHtml().xpath("//div[@aria-label=\"栏目信息内容区\"]/div/div[2]/table").nodes()
            for (def NextUrl : NextUrls) {
                // 解析每个子链接
                String url = NextUrl.xpath("//a/@href").get()
                String title = NextUrl.xpath("//a/text()").get()
                String detailUrl = "https://www.pbc.gov.cn/" + url
                title = TT + title

                if (title.contains("企业商品价格")) {
                    Request req = new Request(detailUrl)
                    req.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), detailUrl)
                    req.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
                    req.putExtra("parser", "详情页")
                    pageResult.addChildRequest(req)
                }
            } page.setSkip(true)
        }
        else if (page.getRequest().getExtra("parser") == "详情页") {
            def req = page.getRequest()
            String detailUrl = req.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            String title = req.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            title = cleanText(title)
            String webUrl = "https://www.pbc.gov.cn/diaochatongjisi/116219/116319/index.html"


            def DDUrls = page.getHtml().xpath("//table[@class=\"border_nr\"]//td[@valign=\"top\"]/table").nodes()
            for (def DDUrl : DDUrls) {
                String downUrl = DDUrl.xpath("//td[3]/a[contains(@href,\"xls\")]/@href").get()
                String downname = DDUrl.xpath("//td[1]/div/text()").get()
                downname = downname + ParseToolsUtils.getByRegex(downUrl, "\\..*")
                String pushtime = ParseToolsUtils.getByRegex(downUrl, "/\\d{8}")
                pushtime = parseDate(pushtime)

                if (StringUtils.isNotBlank(pushtime)) {
                    pushtime = ParseToolsUtils.formatPushTimeDateTime(pushtime)
                }
                if (StringUtils.isBlank(detailUrl) || StringUtils.isBlank(title) || StringUtils.isBlank(pushtime)) {
                    return pageResult
                }

                if (StringUtils.isNotBlank(downUrl)) {
                    if (!downUrl.startsWith("http")) {
                        downUrl = "https://www.pbc.gov.cn/" + downUrl
                    }
                }


                // 构造返回数据
                Map<String, Pair<String, String>> dataMap = new HashMap<>();

                dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), detailUrl));
                dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl));
                dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title));
                dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime));
                dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downUrl));
                dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), downname));

                Request downloadReq = new Request(downUrl)
                downloadReq.putExtra("parser", "附件页")
                downloadReq.putExtra("dataMap", dataMap)
                downloadReq.putExtra("dedupKey", pageResult.getDeDupKeyList())
                downloadReq.setPriority(99)
                pageResult.addChildRequest(downloadReq)
            }
        } else if (parser == "附件页") {
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = page.request.getExtra("dataMap")
            List<String> dedupKeyList = page.request.getExtra("dedupKey")
            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            pageResult.addOssFileByte(page.getBytes())
            pageResult.setDeDupKeyList(dedupKeyList)
        }


        //添加去重key
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName());
        return pageResult;

    }
    private static String parseDate(String input) {
        if (input == null || input.length() < 9 || !input.substring(1, 9).isNumber()) {
            return ""
        }

        // 提取年份和月份
        def year = input.substring(1, 5)
        def month = input.substring(5, 7)
        def day = input.substring(7, 9)

        // 返回格式化后的字符串
        return "${year}-${month}-${day}"
    }

}