import org.apache.commons.lang3.tuple.Pair
import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import com.gildata.spider.base.starter.groovy.util.SpiderManagerUtils
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.Page
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import us.codecraft.webmagic.selector.Json
import org.apache.commons.lang3.StringUtils


class SampleClass implements IparseScript {

    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        Page page = request.getPage()
        // 返回对象
        ParsePageResult pageResult = new ParsePageResult()
        // 判断通过百度链接作为起始页
        if (page.getUrl().get().contains("baidu.com")) {
            //实际的起始页
            Request req = new Request("http://www.shxsj.com/serve/api/article_api/get_cid_article")
            //构造表单请求参数
            Map<String, String> params = new HashMap<>()
            params.put("cid", "52")
            params.put("limit", "10")
            params.put("page", "1")
            SpiderManagerUtils.handlePostFormRequest(req, params)
            req.setPriority(99)
            req.putExtra("parser", "跳转页")
            //添加到子请求
            pageResult.addChildRequest(req)
            page.setSkip(true)
        }else if (page.getRequest().getExtra("parser") == "跳转页") {

            //列表页
            def res = page.getJson().jsonPath("\$.data.list.data[*]").all()

            for (final def r in res){
                //遍历列表中每项
                def json = new Json(r)
                def title = ParseToolsUtils.safeGetJsonPath(json, "\$.title")
                if (title == "") {
                    title = ParseToolsUtils.safeGetJsonPath(json, "\$.project.issuer")
                }
                def releaseDate = ParseToolsUtils.safeGetJsonPath(json, "\$.add_time")
                def webUrl = "http://www.shxsj.com/page?template=4&pageid=52&mid=3"
                def downUrl = ParseToolsUtils.safeGetJsonPath(json, "\$.pdf[0].url")
                def downUrl1 = ParseToolsUtils.safeGetJsonPath(json, "\$.pdf[1].url")

                if (StringUtils.isNotBlank(downUrl)) {
                    downUrl = "http://www.shxsj.com" + downUrl
                    def req = new Request(downUrl)
                    req.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
                    req.putExtra(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), releaseDate)
                    req.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), webUrl)
                    req.putExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), downUrl)
                    req.putExtra("parser", "下载页")
                    //添加到子请求
                    pageResult.addChildRequest(req)
                }
                if (StringUtils.isNoneBlank(downUrl1)) {
                    downUrl1 = "http://www.shxsj.com" + downUrl1
                    def req1 = new Request(downUrl1)
                    req1.putExtra(CrawlerDataCommonFieldsEnum.TITLE.getName(), title)
                    req1.putExtra(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), releaseDate)
                    req1.putExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), webUrl)
                    req1.putExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), downUrl1)
                    req1.putExtra("parser", "下载页")
                    //添加到子请求
                    pageResult.addChildRequest(req1)
                }

            }
            page.setSkip(true)
        }else if (page.getRequest().getExtra("parser") == "下载页") {
            // 下载页
            def req = page.getRequest()
            //标题
            String title = req.getExtra(CrawlerDataCommonFieldsEnum.TITLE.getName())
            //发布时间
            String releaseDate = req.getExtra(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())
            releaseDate = ParseToolsUtils.formatPushTimeDateTime(releaseDate)
            //网页链接
            String url = req.getExtra(CrawlerDataCommonFieldsEnum.DETAILURL.getName())
            //下载链接
            String downUrl = req.getExtra(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())

            //关键字段判空
            if (StringUtils.isBlank(url) || StringUtils.isBlank(releaseDate) || StringUtils.isBlank(downUrl)) {
                return pageResult
            }

            //构造返回数据
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            Map<String, Pair<String, String>> dataMap = new HashMap<>()

            dataMap.put(CrawlerDataCommonFieldsEnum.DETAILURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DETAILURL.getAlias(), url))
            dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
            dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), releaseDate))
            dataMap.put(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getAlias(), downUrl))
            dataMap.put(CrawlerDataCommonFieldsEnum.FILENAME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.FILENAME.getAlias(), ParseToolsUtils.getFileName(downUrl)))

            dataMapList.add(dataMap)
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            //（需要文件下载时）添加文件流
            pageResult.addOssFileByte(page.getBytes())
        }

        //添加去重key
        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.PUSHTIME.getName())
                .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())

        return pageResult
    }
}