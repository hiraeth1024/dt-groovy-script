import com.gildata.spider.base.common.constant.CrawlerDataConstants
import com.gildata.spider.base.starter.groovy.IparseScript
import com.gildata.spider.base.starter.groovy.enums.CrawlerDataCommonFieldsEnum
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest
import com.gildata.spider.base.starter.groovy.model.ParsePageResult
import com.gildata.spider.base.starter.groovy.util.ParseToolsUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Request
import us.codecraft.webmagic.selector.Html
import us.codecraft.webmagic.selector.Json

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List
import java.util.Map

/**
 * 配置ID: 1456075716348084224
 * 配置名称: 中华人民共和国商务部-商务预报-首页-数据中心-周度数据-圆白菜价格
 * 起始请求链接: https://cif.mofcom.gov.cn/cif/html/
 * 起始请求方式: POST
 * 起始请求参数: {"indexId":"224057","startDate":"2003-01-01"}
 *
 * 自动生成脚本，需进一步修改，请务必验证脚本细节是否正确
 */
class ParseClass_1456075716348084224 implements IparseScript {
    @Override
    ParsePageResult doParse(ParsePageRequest request) {
        // page - 下载的页面  ｜  pageResult - 返回的信息
        Page page = request.getPage()
        ParsePageResult pageResult = new ParsePageResult()
        // parser - 标识当前请求应进入哪个页面解析分支
        String parser = page.getRequest().getExtra("parser")
        if (StringUtils.isBlank(parser)) {
            // 起始页为：'0001'
            parser = '0001'
        }

        if (StringUtils.equals(parser, '0001')) {
            // 页面: kaishi
            // 列表选择器 - 抽取多个片段进行单独处理
            def nodes = new Json(page.getRawText()).jsonPath('datas.[-2:]').all() ?: []
            if (nodes == null || nodes.isEmpty()) {
                // 没有匹配到任何列表节点时，当前页面无需继续处理
                return pageResult
            }
            nodes.each { node ->
                // 处理各个独立片段
                Map<String, Object> resultMap = new LinkedHashMap<>()
                List<Map<String, Object>> candidatePool = new ArrayList<>()
                String jzrqCandidateValue_0 = new Json(node.toString()).jsonPath('DATADATE').get()
                candidatePool.add([fieldName: 'jzrq', value: jzrqCandidateValue_0, priority: 0, pageIndex: 0])
                String gjcCandidateValue_1 = new Json(node.toString()).jsonPath('NAME').get()
                candidatePool.add([fieldName: 'gjc', value: gjcCandidateValue_1, priority: 0, pageIndex: 0])
                String sjCandidateValue_2 = new Json(node.toString()).jsonPath('DATA').get()
                candidatePool.add([fieldName: 'sj', value: sjCandidateValue_2, priority: 0, pageIndex: 0])
                String fbrqCandidateValue_3 = null
                candidatePool.add([fieldName: 'fbrq', value: fbrqCandidateValue_3, priority: 0, pageIndex: 0])
                String PageTitleCandidateValue_4 = "中华人民共和国商务部-商务预报-首页-数据中心-周度数据-圆白菜价格"
                candidatePool.add([fieldName: 'PageTitle', value: PageTitleCandidateValue_4, priority: 0, pageIndex: 0])
                Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
                candidatePool.each { candidate ->
                    if (StringUtils.isBlank((String) candidate.value)) { return }
                    def best = bestCandidateMap.get(candidate.fieldName)
                    if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                        bestCandidateMap.put(candidate.fieldName, candidate)
                    }
                }
                bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
                String jzrq = resultMap.get('jzrq')
                String gjc = resultMap.get('gjc')
                String sj = resultMap.get('sj')
                String fbrq = resultMap.get('fbrq')
                String PageTitle = resultMap.get('PageTitle')

                // 缺失发布时间，以当前时间为默认值，需要再次检查
                String pushtime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                // 返回采集到的数据
                List<Map<String, Pair<String, Object>>> dataList = new ArrayList<>()
                Map<String, Pair<String, Object>> dataMap = new LinkedHashMap<>()
                dataMap.put('jzrq', Pair.of('截止日期', jzrq))
                dataMap.put('gjc', Pair.of('关键词', gjc))
                dataMap.put('sj', Pair.of('数据', sj))
                dataMap.put('fbrq', Pair.of('信息发布日期', fbrq))
                dataMap.put('PageTitle', Pair.of('页面标题（用于追溯问题）', PageTitle))
                dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
                dataList.add(dataMap)
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

                // 添加去重键
                pageResult.addDedupKey('jzrq')
                pageResult.addDedupKey('gjc')
                pageResult.addDedupKey('sj')
                pageResult.addDedupKey('PageTitle')
            }
        }

        return pageResult
    }
}
