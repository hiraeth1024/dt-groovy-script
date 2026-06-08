import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Selectable

// 起始链接：http://czt.hebei.gov.cn/root17/3003/3047/3050/list_292.htm
class ParseClass_1727191943194083329 implements IparseScript {
    @Override
    ParsePageResult doParse(ParsePageRequest req) {
        // 结果存储对象
        ParsePageResult pageResult = new ParsePageResult()
        // 下载的页面
        Page page = req.getPage()
        // 该页面的源请求
        Request request = page.request
        // 选择的页面解析方式
        String parser = request.getExtra("parser")
        // 列表页的解析
        if (parser == null) {
            List<Selectable> list = page.html.xpath("//div[@id=\"documentContainer\"]/table").nodes()
            for (def item : list) {
                // 标题解析
                String title = item.xpath("//a/text()|//a/@title").get()
                // 发布时间解析
                String releaseDate = item.regex("\\d{4}-\\d{1,2}-\\d{1,2}").get()
                // 网页链接常量值
                String webUrl = "https://www.xichong.gov.cn/zwgk/fdzdgknr/tjsj/"
                // 下载链接，直接获取为 ../../../zfxx/202510/t20251013_2270374.html，需要进行拼接
                String downUrl = item.xpath("//a/@href").get()
                downUrl = "https://czt.hebei.gov.cn/root17/" + downUrl.replace("../../../", "")
                // 返回数据
                HashMap<String, Pair<String, String>> dataMap = [
                        "title"      : Pair.of("标题", title),
                        "releaseDate": Pair.of("公告日期", releaseDate),
                        "webUrl"     : Pair.of("网站地址", webUrl),
                        "downUrl"    : Pair.of("下载链接", downUrl),
                        "filename"   : Pair.of("文件名", ParseToolsUtils.getFileName(downUrl))
                ]
                // 构建详情页请求
                Request newRequest = new Request(downUrl)
                newRequest.putExtra("parser", "详情页") // 使用下方的详情页解析方法
                newRequest.putExtra("dataMap", dataMap) // 携带数据到详情页
                // 添加次级请求
                pageResult.addChildRequest(newRequest)
            }
        } else if (parser == "详情页"){
            // 详情页的解析
            Map<String, Pair<String, String>> dataMap = request.getExtra("dataMap")
            // 通过工具，将页面当作的相对链接转换为直链
            page = ParseToolsUtils.convertRelativeUrlsToAbsolute(page)
            // 通过正则表达式获取正文
            String content = page.html.regex("[\\s\\S]+").get()
            // 将正文内容存储到 dataMap 当中
            dataMap["content"] = Pair.of("正文", content)
            // 将数据存储到 pageResult 当作
            pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, [dataMap])
            // 下载这个页面的内容
            pageResult.addOssFileByte(page.getBytes())
            // 添加用于去重的字段
            pageResult.addDedupKey("title")
                    .addDedupKey("releaseDate")
                    .addDedupKey("downUrl")
                    .addDedupKey("webUrl")
        }
        return pageResult
    }
}

