import cn.hutool.core.date.DateUtil
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

import java.time.LocalDateTime

/**
 * 【国家统计局-json格式数据】采集脚本
 *
 * 目标：采集国家统计局数据库栏目下所有五级指标数据（真正数据指标）的近24个月数据
 *
 * 数据流程：
 * 1. 入口：获取数据库数据根节点（pid=空, code=12）
 * 2. 遍历：递归遍历指标树（queryIndexTreeAsync接口）到四级节点（isLeaf=true）
 * 3. 获取五级指标：调用 queryIndicatorsByCid 获取真正的数据指标列表
 * 4. 查询：对每个五级指标，使用easyquery.htm接口查询近24个月数据
 * 5. 解析：提取returndata.datanodes中的数值数据
 */

class gb_stats_json implements IparseScript {


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
             * 入口URL：https://data.stats.gov.cn/dg/website/publicrelease/web/external/new/queryIndexTreeAsync?pid=&code=12(所需爬取的数据库URL，根据实际情况更改)
             */
            def nodes = page.getJson().jsonPath("data[*]").all()

            for (def node : nodes) {
                def it = new Json(node)
                String rootId = ParseToolsUtils.safeGetJsonPath(it, "_id")
                String rootName = ParseToolsUtils.safeGetJsonPath(it, "name")  // 获取根节点名称
                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath", rootName)  // 初始化标题路径

                // 获取二级指标
                String requestUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/new/queryIndexTreeAsync?pid=${rootId}&code=12"/*根据实际情况更改*/
                sendGet(pageResult, "遍历指标树", requestUrl, pageParams)

            }
            return pageResult
        }

        // ==========================================
        // 【第二阶段】递归遍历指标树
        // ==========================================
        if (parser == "遍历指标树") {
            // 获取父级传递下来的标题路径
            String parentTitle = page.request.getExtra("titlePath") ?: "三大经济体月度数据"/*根据实际情况更改*/

            def nodes = page.getJson().jsonPath("data[*]").all()
            for (def node : nodes) {
                def it = new Json(node)
                String nodeId = ParseToolsUtils.safeGetJsonPath(it, "_id")
                String nodeName = ParseToolsUtils.safeGetJsonPath(it, "name")  // 当前节点名称
                String isLeaf = ParseToolsUtils.safeGetJsonPath(it, "isLeaf")

                // 拼接标题路径：父路径-当前节点名
                String currentTitle = parentTitle + "\\" + nodeName
                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath", currentTitle)  // 传递给下一级

                if ("true".equals(isLeaf)) {
                    //叶子节点进入下一循坏
                    String requestUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/new/queryIndicatorsByCid?cid=${nodeId}&dt=&name="/*根据实际情况更改*/
                    sendGet(pageResult, "获取叶子节点", requestUrl, pageParams)
                } else {
                    // 【非叶子节点】继续递归
                    String requestUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/new/queryIndexTreeAsync?pid=${nodeId}&code=12"/*根据实际情况更改*/
                    sendGet(pageResult, "遍历指标树", requestUrl, pageParams)
                }
            }
            return pageResult
        }

        // ==========================================
        // 【第三阶段】获取叶子节点指标列表并构建数据查询
        // ==========================================
        if (parser == "获取叶子节点") {
            // 获取父级传递的标题路径（四级节点名称）
            String parentTitle = page.request.getExtra("titlePath") ?: "三大经济体月度数据"/*根据实际情况更改*/
            def indicators = page.getJson().jsonPath("data.list[*]").all()

            for (def indicator : indicators) {
                def it = new Json(indicator)

                String cid = ParseToolsUtils.safeGetJsonPath(it, "catalogid")
                String requestUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/getDaCatalogTreeByIndicatorCid?indicatorCid=${cid}"


                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath", parentTitle)
                pageParams.put("cid", cid)

                sendGet(pageResult, "地区列表",requestUrl,pageParams)
            }
        }


        if (parser == "地区列表"){
            String parentTitle = page.request.getExtra("titlePath")

            def nodes = page.getJson().jsonPath("data[*]").all()
            for (def node : nodes){
                def it = new Json(node)
                String _id = ParseToolsUtils.safeGetJsonPath(it, "_id")
                String requestUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/getDasByDaCatalogId?daCid=${_id}"
                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath", parentTitle)
                pageParams.put("cid", page.request.getExtra("cid"))
                sendGet(pageResult, "取地区",requestUrl,pageParams)
            }

        }

        if (parser == "取地区"){
            String postUrl = "https://data.stats.gov.cn/dg/website/publicrelease/web/external/getEsDataByCidAndDt"
            def nodes = page.getJson().jsonPath("data[*]").all()
            for (def node : nodes) {
                def it = new Json(node)
                String text = ParseToolsUtils.safeGetJsonPath(it, "show_name")
                String value = ParseToolsUtils.safeGetJsonPath(it, "name_value")
                String cid = page.request.getExtra("cid")
                //以当前时间为基准，取时间范围
                //月度数据
                LocalDateTime currentTime = LocalDateTime.now()
                LocalDateTime startTime = currentTime.minusMonths(24)
                LocalDateTime endTime = currentTime.minusMonths(1)
                String startDate = DateUtil.format(startTime, "yyyyMM") + "MM"
                String endDate = DateUtil.format(endTime, "yyyyMM") + "MM"
                //季度数据
                /*
                LocalDateTime currentTime = LocalDateTime.now()
                LocalDateTime startTime = currentTime.minusMonths(12)  // 12个月前 = 4个季度前
                LocalDateTime endTime = currentTime.minusMonths(3)      // 3个月前 = 上季度
                int startQuarter = ((startTime.getMonthValue() - 1) / 3) + 1
                int endQuarter = ((endTime.getMonthValue() - 1) / 3) + 1
                String startYear = DateUtil.format(startTime, "yyyy")
                String endYear = DateUtil.format(endTime, "yyyy")
                String startDate = startYear + String.format("%02d", startQuarter) + "SS"
                String endDate = endYear + String.format("%02d", endQuarter) + "SS"
                */
                //年度数据
                /*
                LocalDateTime currentTime = LocalDateTime.now()
                LocalDateTime startTime = currentTime.minusYears(5)
                LocalDateTime endTime = currentTime.minusYears(1)
                String startYear = DateUtil.format(startTime, "yyyy")+"YY"
                String endYear = DateUtil.format(endTime, "yyyy")+"YY"
                */
                Map<String, String> das = new HashMap<>()
                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath",page.request.getExtra("titlePath"))// 传递给最终数据阶段
                pageParams.put("location",text)
                das.put("text", text)
                das.put("value", value)
                def params = new HashMap()
                params.put("cid", cid)
                params.put("daCatalogId", "")
                params.put("das", [das])
                params.put("showType", "1")
                params.put("dts", [startDate+"-"+endDate])
                params.put("rootId", "0f2a950d12a347cfa02393487bf99f00")/*根据实际情况更改，根节点下的rootId*/
                sendPost(pageResult, "获取最终数据", postUrl, pageParams,params, "json")

            }
            return pageResult
        }

        // ==========================================
        // 【第四阶段】解析最终数据
        // ==========================================
        if (parser == "获取最终数据") {
            // 获取完整标题路径
            String fullTitle = page.request.getExtra("titlePath") ?: "三大经济体月度数据"
            String location = page.request.getExtra("location")
            def nodes = page.getJson().jsonPath("\$.data[*]").all()
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()

            // 解析每个数据节点
            for (def node : nodes) {
                def it = new Json(node)
                def items = it.jsonPath("\$.values[*]").all()
                String end_date = ParseToolsUtils.safeGetJsonPath(it, "code")
                end_date = parseDate(end_date)
                for (def item : items) {
                    def dataJson = new Json(item)
                    String i_showname = ParseToolsUtils.safeGetJsonPath(dataJson, "i_showname")
                    String value = ParseToolsUtils.safeGetJsonPath(dataJson, "value")
                    String du_name = ParseToolsUtils.safeGetJsonPath(dataJson, "du_name")
                    String pageTitle = "国家统计局" +"\\"+"国际数据"+ "\\" + fullTitle/*根据实际情况更改*/
                    String info_date
                    String release_frequency
                    //清洗指标
                    i_showname = cleanText(i_showname)
                    location = cleanText(location)
                    du_name = cleanText(du_name)
                    pageTitle = cleanText(pageTitle)

                    // 创建数据记录
                    Map<String, Pair<String, String>> dataMap = new HashMap<>()

                    // 【新增】页面标题（拼接的完整路径）
                    addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), '季', true)
                    addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_unit", "指标单位"), du_name, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_name", "指标名称"), indicator_name, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_data", "指标数据"), value, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), raw_info_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("info_date", "信息发布日期"), info_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_end_date", "原始截止日期"), raw_end_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), end_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), "全国", true)                    // 【新增】固定字段
                    addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrXHGSJBHG", true)      // 【新增】固定字段
                    if (StringUtils.isBlank(i_showname) | StringUtils.isBlank(value) | StringUtils.isBlank(end_date)) {
                        //return pageResult
                        continue  // 跳过空值，不要直接return
                    }
                    dataMapList.add(dataMap)
                }
            }
            if (!dataMapList.isEmpty()) {
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