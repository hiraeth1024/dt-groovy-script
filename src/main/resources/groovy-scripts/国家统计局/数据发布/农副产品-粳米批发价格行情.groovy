import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import org.apache.commons.lang3.tuple.Pair
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.StringUtils
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Json
import com.gildata.spider.base.starter.groovy.util.SpiderManagerUtils

import java.time.LocalDate

/**
 * 【农副产品-粳米批发价格行情-json格式数据】采集脚本
 *
 * 目标：
 *
 * 数据流程：
 * 1. 入口：获取数据库数据根节点（pid=空, code=12）
 * 2. 遍历：递归遍历指标树（queryIndexTreeAsync接口）到四级节点（isLeaf=true）
 * 3. 获取五级指标：调用 queryIndicatorsByCid 获取真正的数据指标列表
 * 4. 查询：对每个五级指标，使用easyquery.htm接口查询近24个月数据
 * 5. 解析：提取returndata.datanodes中的数值数据
 */


class script0001 implements IparseScript {


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
        String webUrl = page.getUrl().get()
        String parser = page.request.getExtra("parser")
        // ==========================================
        // 【第一阶段】初始化：获取数据库数据根节点
        // ==========================================
        if (parser == null) {
            /**
             * 入口URL：
             */
            def indexs = [
                ["cateId":"210010","name":"硬米"],
                ["cateId":"210020","name":"籼米"],
                ["cateId":"210030","name":"籼米"],
                ["cateId":"220010","name":"豆油"],
                ["cateId":"220020","name":"豆油"],
                ["cateId":"220030","name":"菜籽油"],
                ["cateId":"220050","name":"菜籽油"]
            ]

            for(def index : indexs){
                String cateId = index.cateId
                String name = index.name
                Map<String , Object> pageParams = new HashMap<>()
                pageParams.put("titlePath", name)
                String date = LocalDate.now().minusDays(2).toString()

                String requestUrl =  "https://cif.mofcom.gov.cn/cif/getEnterpriseListForDate.fhtml?cateId=${cateId}&&searchDate=${date}"
                sendGet(pageResult,"遍历数据", requestUrl, pageParams)

            }
        }

        // ==========================================
        // 【第二阶段】递归遍历指标树
        // ==========================================
        if (parser == "遍历数据") {
            // 获取父级传递下来的标题路径
            String parentTitle = page.request.getExtra("titlePath")
            String rawEndDate = page.getJson().jsonPath("date").get() ?: "2026-05-05"//截止日期
            def nodes = page.getJson().jsonPath("\$.datas[*]").all() //获取datas
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            for (def node : nodes) {
                def it = new Json(node)
                String areaName = ParseToolsUtils.safeGetJsonPath(it, "COUNTY_NAME") //地区
                String goodsName = ParseToolsUtils.safeGetJsonPath(it, "COMMDITYNAME")  // 商品名
                String companyName = ParseToolsUtils.safeGetJsonPath(it, "NAME") //公司名
                String price1 = ParseToolsUtils.safeGetJsonPath(it, "PRICE1") //当日价格
                String price2 = ParseToolsUtils.safeGetJsonPath(it, "PRICE2") // 前一日价格
                String price3 = ParseToolsUtils.safeGetJsonPath(it, "PRICE3") //环比
                String areaCode = ParseToolsUtils.safeGetJsonPath(it, "AREA") // 地区代码
                String prevReportDate = ParseToolsUtils.safeGetJsonPath(it, "RPT_DATE") // 前一日发布日期
                String companyCode = ParseToolsUtils.safeGetJsonPath(it, "ENTERID") //公司代码

                String pageTitle = "中华人民共和国商务部\\商务预报\\数据中心\\农副产品\\农副产品\\粮油\\" + parentTitle
                String rawInfoDate = LocalDate.now().toString() //发布日期
                String infoDate = ParseToolsUtils.formatPushTimeDateTime(rawInfoDate)
                String endDate = ParseToolsUtils.formatPushTimeDateTime(rawEndDate)
                String prevEndDate = ParseToolsUtils.formatPushTimeDateTime(prevReportDate)

                def indicators = [
                        [name: goodsName + "\\" + companyName + "\\" + "价格", value: price2, unit:"", date: endDate],
                        [name: goodsName + "\\" + companyName + "\\" + "价格", value: price1, unit:"", date: prevEndDate],
                        [name: goodsName + "\\" + companyName + "\\" + "环比", value: price3, unit:"%", date: endDate],
                ]

                for (def ind: indicators){
                    if (StringUtils.isBlank(ind.value)) {
                        continue
                    }
                    Map<String, Pair<String, String>> dataMap = new HashMap<>()
                    addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), "日", true)
                    addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_name", "指标名称"), ind.name, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_data", "指标数据"), ind.value, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_unit", "指标单位"), ind.unit, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), rawInfoDate, true)
                    addDataMap(pageResult, dataMap, Pair.of("info_date", "信息发布日期"), infoDate, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_end_date", "原始截止日期"), rawEndDate, true)
                    addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), endDate, true)
                    addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), areaName, true)
                    addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "useHYSJB", true)
                    addDataMap(pageResult, dataMap, Pair.of("backup_filed1", "公司ID"), companyCode, true)

                    dataMapList.add(dataMap)
                }

            }
            if(!dataMapList.isEmpty()) {
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)
            }


        }
        return pageResult

    }

    // ==========================================
    // 【工具方法】
    // ==========================================
    /**
     * 【新增】文本清理方法
     * 1. 去除首尾空格
     * 2. 将中文括号（）替换为英文括号()
     * @param input 原始文本
     * @return 清理后的文本
     */
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

    /**
     * 添加数据字段到结果集
     * @param field Pair.of('字段代码', '字段中文名')
     * @param value 字段值
     * @param dedup 是否作为去重字段（true表示该字段组合用于判断重复数据）
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
     * 创建POST请求
     * @param pageName 下一阶段名称
     * @param requestUrl 请求网址
     * @param pageParams 传递给下一阶段的参数
     * @param postParams 请求参数
     * @param paramsType 参数类型(form 或者 json)
     */
    private static sendPost(ParsePageResult pageResult, String pageName, String requestUrl, Map<String, Object> pageParams = null, Map<String, Object> postParams, String paramsType = "form"){
        //对下阶段的请求构建post请求
        //创建请求对象
        Double random = Math.random()
        Request req = new Request(requestUrl + "?random=${random}")
        req.setPriority(99)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        if (pageParams != null){
            pageParams.each { key, value ->
                req.putExtra(key, value)
            }
        }
        //form参数-看请求标头Content-Type
        if (paramsType == 'form')
            SpiderManagerUtils.handlePostFormRequest(req, postParams)
        //json参数-看请求标头Content-Type
        if (paramsType == 'json')
            SpiderManagerUtils.handlePostJSonRequest(req, postParams)
        //将请求添加到子请求中
        pageResult.addChildRequest(req)
    }

    /**
     * 将"yyyyMM"格式的字符串转换为"yyyy年MM月"格式
     * @param input 输入字符串，如"202603MM"
     * @return 转换后的字符串，如"2026年03月"
     */
    private static String parseDate(String input) {
        if (input == null || input.length() < 6 || !input.substring(0, 6).isNumber()) {
            return ""
        }

        // 提取年份和月份
        def year = input.substring(0, 4)
        def month = input.substring(4, 6)

        // 返回格式化后的字符串
        return "${year}年${month}月"
    }

}