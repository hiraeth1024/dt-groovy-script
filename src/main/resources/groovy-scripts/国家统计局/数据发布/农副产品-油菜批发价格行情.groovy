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

class RDSJ_SC implements IparseScript {

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

        if (parser == null) {
            def indexs = [
                    [cateId:"170020",name:"油菜"],
                    [cateId:"170040",name:"芹菜"],
                    [cateId:"170050",name:"生菜"],
                    [cateId:"170480",name:"韭菜"],
                    [cateId:"170090",name:"洋葱"],
                    [cateId:"170060",name:"大白菜"],
                    [cateId:"170010",name:"圆白菜"],
                    [cateId:"170070",name:"白萝卜"],
                    [cateId:"170250",name:"大葱"],
                    [cateId:"170260",name:"胡萝卜"],
                    [cateId:"170080",name:"土豆"],
                    [cateId:"170270",name:"莲藕"],
                    [cateId:"170280",name:"莴笋"],
                    [cateId:"170290",name:"绿豆芽"],
                    [cateId:"170100",name:"蒜头"],
                    [cateId:"170120",name:"西红柿"],
                    [cateId:"170110",name:"生姜"],
                    [cateId:"170140",name:"茄子"],
                    [cateId:"170150",name:"尖椒"],
                    [cateId:"170160",name:"青椒"],
                    [cateId:"170130",name:"黄瓜"],
                    [cateId:"170180",name:"冬瓜"],
                    [cateId:"170200",name:"苦瓜"],
                    [cateId:"170330",name:"西葫芦"],
                    [cateId:"170340",name:"西兰花"],
                    [cateId:"170440",name:"菠菜"],
                    [cateId:"170450",name:"菜花"],
                    [cateId:"170500",name:"山药"],
                    [cateId:"170470",name:"南瓜"],
                    [cateId:"170170",name:"豆角"]
            ]
            for(def index :indexs){
                String cateId = index.cateId
                String name = index.name
                Map<String, Object> pageParams = new HashMap()
                pageParams.put("titlePath", name)
                String requestUrl = "https://cif.mofcom.gov.cn/cif/getEnterpriseByCateId2019.fhtml?cateId=${cateId}"
                sendGet(pageResult, "遍历数据", requestUrl, pageParams)
            }

        }

        if (parser == "遍历数据") {
            String parentTitle = page.request.getExtra("titlePath")
            String raw_end_date = page.getJson().jsonPath("date").get()//截止日期
            def nodes = page.getJson().jsonPath("\$.datas[*]").all()
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            for (def node : nodes) {
                def it = new Json(node)
                String arlea = ParseToolsUtils.safeGetJsonPath(it,"COUNTY_NAME")//地区
                String NAME = ParseToolsUtils.safeGetJsonPath(it,"NAME")//公司名称
                String ENTERID = ParseToolsUtils.safeGetJsonPath(it,"ENTERID")//公司ID
                String PRICE2 = ParseToolsUtils.safeGetJsonPath(it,"PRICE2")//当日价格
                String PRICE1 = ParseToolsUtils.safeGetJsonPath(it,"PRICE1")//前一日价格
                String PRICE3 = ParseToolsUtils.safeGetJsonPath(it,"PRICE3")//环比
                String raw_end_date_prev = ParseToolsUtils.safeGetJsonPath(it,"RPT_DATE")//前一日日期
                String COMMDITYNAME = ParseToolsUtils.safeGetJsonPath(it,"COMMDITYNAME")//商品名
                String pageTitle = "中华人民共和国商务部\\商务预报\\数据中心\\农副产品\\日度监测数据\\蔬菜" + "\\" + parentTitle
                String raw_info_date = LocalDate.now().toString()//发布日期
                String info_date = ParseToolsUtils.formatPushTimeDateTime(raw_info_date)
                String end_date = ParseToolsUtils.formatPushTimeDateTime(raw_end_date)
                String end_date_prev = ParseToolsUtils.formatPushTimeDateTime(raw_end_date_prev)
                def indicators = [
                        [name: COMMDITYNAME + "\\" + NAME + "\\" + "价格", value: PRICE2, du_name: "",date: end_date],
                        [name: COMMDITYNAME + "\\" + NAME + "\\" + "价格", value: PRICE1, du_name: "",date: end_date_prev],
                        [name: COMMDITYNAME + "\\" + NAME + "\\" + "环比", value: PRICE3, du_name: "%",date: end_date]
                ]
                for (def ind : indicators) {
                    if (StringUtils.isBlank(ind.value)) {
                        continue
                    }

                    Map<String, Pair<String, String>> dataMap = new HashMap<>()
                    addDataMap(pageResult, dataMap, Pair.of("release_frequency", "披露频率"), '日', true)
                    addDataMap(pageResult, dataMap, Pair.of("pageTitle", "路径"), pageTitle, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_name", "指标名称"), ind.name, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_data", "指标数据"), ind.value, true)
                    addDataMap(pageResult, dataMap, Pair.of("indicator_unit", "指标单位"), ind.du_name, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_info_date", "原始信息发布日期"), raw_info_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("info_date", "信息发布日期"), info_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("raw_end_date", "原始截止日期"), raw_end_date, true)
                    addDataMap(pageResult, dataMap, Pair.of("end_date", "截止日期"), end_date, true)
                    //addDataMap(pageResult, dataMap, Pair.of("statistic_area", "统计区域"), area, true)
                    addDataMap(pageResult, dataMap, Pair.of("data_target_table", "数据目标表"), "usrHYSJB", true)
                    addDataMap(pageResult, dataMap, Pair.of("backup_field1", "公司ID"), ENTERID, true)
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

    private static String parseDate(String input) {
        if (input == null || input.length() < 8 || !input.substring(0, 8).isNumber()) {
            return ""
        }

        // 提取年份和月份
        def year = input.substring(0, 4)
        def month = input.substring(4, 6)
        def day = input.substring(6, 8)

        // 返回格式化后的字符串
        return "${year}-${month}-${day}"
    }

}