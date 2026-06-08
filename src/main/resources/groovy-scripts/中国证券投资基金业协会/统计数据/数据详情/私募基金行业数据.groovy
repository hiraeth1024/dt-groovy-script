import com.dtflys.forest.utils.StringUtils
import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.Pair

import org.codehaus.jackson.map.ObjectMapper

import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Json
import us.codecraft.webmagic.selector.Selectable
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONPath;
@Slf4j
class 私募基金行业数据 implements IparseScript {

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
        // 【第一阶段】初始化：获取数据库数据根节点
        // ==========================================
        if (parser == null) {
            /**
             * 入口URL：https://www.amac.org.cn/sjtj/datastatistics/assetmanagementdata/(所需爬取的数据库URL，根据实际情况更改)
             */
            page.setSkip(true)
            String webUrl = "https://www.amac.org.cn/sjtj/datastatistics/assetmanagementdata/"





            List<Tuple3<String, Integer, String>> timeTypes = [
                    new Tuple3("月度数据", 1, "y"),
                    new Tuple3("季度数据", 2, "j"),
                    new Tuple3("年度数据", 3, "n")
            ]

            List<Tuple3<String, Integer, String>> productTypes = [
                    new Tuple3("私募资管", 1, "s"),
                    new Tuple3("证券公司", 2, "zq"),
                    new Tuple3("基金管理公司", 3, "jg"),
                    new Tuple3("基金子公司", 4, "jz"),
                    new Tuple3("期货公司", 5, "qh"),
                    new Tuple3("证券公司私募子公司", 6, "zz"),
                    new Tuple3("基金管理公司私募子公司", 7, "gq")
            ]



            for (timeType in timeTypes) {


//                List<String> subPageTitles = new ArrayList<>()
//                List<Selectable> nodes = page.html.xpath("//div[@class=\"layer_bor\"]/div[@style=\"display: block;\"]//li").nodes()
//                log.warn("nodes: ${nodes.size()}")
//                for(def node : nodes ) {
//                    String subPageTitle = node.xpath("//text()").get()
//                    log.warn("subPageTitle: ${subPageTitle}")
//                }



                for (productType in productTypes) {
                    String requestUrl = "https://www.amac.org.cn/portal/front/manager/getManagerIndDatas?timeType=${timeType.v2}&productType=${productType.v2}"
                    String pageTitle = timeType.v1 + "\\" + productType.v1
                    String jsonDataTemplate = timeType.v3 + productType.v3


                    List<Selectable> nodes = page.html.xpath("//div[@class=\"layer_bor\"]//div[@class=\"industry_xq type${productType.v2}\"]//li").nodes()
                    Set<String> subPageTitles = new LinkedHashSet<>()
//                    log.warn("nodes : ${nodes.size()}")
                    for(def node : nodes) {
                        String subPageTitle = node.xpath("//allText()").get()
                        subPageTitles.add(subPageTitle)
//                        log.warn("subPageTitle: ${subPageTitle}")
                    }

                    if (nodes.isEmpty()) {
                        return pageResult
                    }




                    Map<String, Object> pageParams = new HashMap()
                    pageParams.put("pageTitle", pageTitle)
                    pageParams.put("webUrl", webUrl)
                    pageParams.put("jsonDataTemplate", jsonDataTemplate)
                    pageParams.put("subPageTitles", subPageTitles)
                    pageParams.put("release_frequency", timeType.v1.substring(0, 1))
//                    log.warn("requestUrl : ${requestUrl}")
                    sendGet(pageResult, "遍历指标树", requestUrl, pageParams)

                }
            }


            return pageResult
        }

        // ==========================================
        // 【第二阶段】递归遍历指标树
        // ==========================================
        if (parser == "遍历指标树") {
            // 获取父级传递下来的标题路径
            String pageTitle = page.request.getExtra("pageTitle")
            String webUrl = page.request.getExtra("webUrl")
            String jsonDataTemplate = page.request.getExtra("jsonDataTemplate")
            Set subPageTitles = page.request.getExtra("subPageTitles")

            String release_frequency = page.request.getExtra("release_frequency")
            log.warn("release_frequency : ${release_frequency}")
            def jsonStr = page.getJson().jsonPath("\$.data.data").get()
            ObjectMapper mapper = new  ObjectMapper()
            Object jsonObj = JSON.parse(jsonStr)
            // 获取 data 对象的长度（属性个数）
            int dataSize = JSONPath.size(jsonObj, ".")
            log.warn("dataSize ${dataSize}")


//            List<Selectable> nodes = page.json.jsonPath("data[*]").nodes()
            List<Map<String, Pair<String, String>>> dataMapList = new ArrayList<>()
            for(int i = 0; i < dataSize; i ++) {
                String dataTemplate = ".${jsonDataTemplate}${i+1}Datas[*]"
                String finalPageTitle = pageTitle +  "\\" + subPageTitles[i].toString()
                log.warn("dataTemplate : ${dataTemplate}")
                log.warn("subPageTitles[${i} : ${subPageTitles[i].toString()}")
//                print("pageTitle : ${pageTitle}")
                def data = page.getJson().jsonPath(dataTemplate).all()



                for(def d : data) {
                    def it = new Json(d)
                    String raw_end_date = ParseToolsUtils.safeGetJsonPath(it, "excelTime")//截止日期
                    String total = ParseToolsUtils.safeGetJsonPath(it, "total")//合计
                    String fundCompany = ParseToolsUtils.safeGetJsonPath(it, "fundCompany")//基金子公司
                    String fundManageCompany = ParseToolsUtils.safeGetJsonPath(it, "fundManageCompany")//基金管理公司
                    String futuresInfoCompany = ParseToolsUtils.safeGetJsonPath(it, "futuresInfoCompany")//期货公司及其资管子公司
                    String securityInfoCompany = ParseToolsUtils.safeGetJsonPath(it, "securityInfoCompany")//证券公司及其资管子公司
                    String securityPriCompany = ParseToolsUtils.safeGetJsonPath(it, "securityPriCompany")//证券公司私募子公司
                    String assetManagePlain = ParseToolsUtils.safeGetJsonPath(it, "assetManagePlain")//集合资产管理计划
                    String singleAssetManagePlan = ParseToolsUtils.safeGetJsonPath(it, "singleAssetManagePlan")//单一资产管理计划
                    String commFinancialDerivative = ParseToolsUtils.safeGetJsonPath(it, "commFinancialDerivative")//期货和衍生品类
                    String equity = ParseToolsUtils.safeGetJsonPath(it, "equity")//权益类
                    String fixedIncome = ParseToolsUtils.safeGetJsonPath(it, "fixedIncome")//固收类
                    String mixed = ParseToolsUtils.safeGetJsonPath(it, "mixed")//混合类
                    String partnershipType = ParseToolsUtils.safeGetJsonPath(it, "partnershipType")//	合伙型
                    String companyType = ParseToolsUtils.safeGetJsonPath(it, "companyType")//公司型
                    String contractualType = ParseToolsUtils.safeGetJsonPath(it, "contractualType")//契约型
                    String end_date = ParseToolsUtils.formatPushTimeDateTime(raw_end_date)
                    Map<String, Pair<String, String>> dataMap = new HashMap<>()

                    def indicators = [
                            [data: fundCompany, name: "基金子公司"],
                            [data: fundManageCompany, name: "基金管理公司"],
                            [data: futuresInfoCompany, name: "期货公司及其资管子公司"],
                            [data: securityInfoCompany, name: "证券公司及其资管子公司"],
                            [data: securityPriCompany, name: "证券公司私募子公司"],
                            [data: assetManagePlain, name: "集合资产管理计划"],
                            [data: singleAssetManagePlan, name: "单一资产管理计划"],
                            [data: commFinancialDerivative, name: "期货和衍生品类"],
                            [data: equity, name: "权益类"],
                            [data: fixedIncome, name: "固收类"],
                            [data: mixed, name: "混合类"],
                            [data: partnershipType, name: "合伙型"],
                            [data: companyType, name: "公司型"],
                            [data: contractualType, name: "契约型"],
                            [data: total, name: "合计"]
                    ]
                    for(def ind in indicators){
                        dataMap.put("indicator_data", Pair.of("指标数据", ind.data))
                        dataMap.put("indicator_name", Pair.of("指标名称", ind.name))
                        dataMap.put("releaseTime", Pair.of("原始发布时间", raw_end_date))
                        dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(),
                                Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), end_date))
                        dataMap.put("pageTitle", Pair.of("路径", "中国证券投资基金业协会\\统计数据\\数据详情\\私募基金行业数据" + finalPageTitle))
                        dataMap.put("statistical_area", Pair.of("统计区域", "全国"))
                        dataMap.put("data_target_table", Pair.of("数据目标表", "userXHGSJBHG"))
                        dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.name,
                                Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.alias, webUrl))
                        dataMap.put("release_frequency", Pair.of("披露频率", release_frequency))
                        dataMap.put("total", Pair.of("合计", total))
                        if (total.contains(".")) {
                            dataMap.put("indicator_unit", Pair.of("指标单位", "亿元"))
                        }else {
                            dataMap.put("indicator_unit", Pair.of("指标单位", "只"))
                        }

                        dataMapList.add(dataMap)
                    }

                }

                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataMapList)


            }


        }

        // 去重键
//        pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
//                .addDedupKey(CrawlerDataCommonFieldsEnum.DOWNLOADURL.getName())
//                .addDedupKey(CrawlerDataCommonFieldsEnum.DETAILURL.getName())

        return pageResult

    }

    // ==========================================
    // 【工具方法】
    // ==========================================

    private static void sendGet(ParsePageResult pageResult,
                                String pageName,
                                String requestUrl,
                                Map<String, Object> pageParams = null) {
        Request req = new Request(requestUrl)
        req.putExtra("parser", pageName)
        req.putExtra("trustAllCerts", true)
        req.setPriority(99)
        if (pageParams != null) {
            pageParams.each { key, value -> req.putExtra(key, value) }
        }
        pageResult.addChildRequest(req)
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

}