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
 * 配置ID: 1611189672430534656
 * 配置名称: 中国人民银行-福州中心支行-金融数据-金融统计数据摘要
 * 起始请求链接: https://www.baidu.com/
 * 起始请求方式: GET
 *
 * 自动生成脚本，需进一步修改，请务必验证脚本细节是否正确
 */
class ParseClass_1611189672430534656 implements IparseScript {
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
            // 页面: url
            Map<String, Object> resultMap = new LinkedHashMap<>()
            List<Map<String, Object>> candidatePool = new ArrayList<>()
            String urlCandidateValue_0 = "http://fuzhou.pbc.gov.cn/fuzhou/126786/index.html"
            candidatePool.add([fieldName: 'url', value: urlCandidateValue_0, priority: 0, pageIndex: 0])
            String FPCandidateValue_1 = "${urlCandidateValue_0}"
            candidatePool.add([fieldName: 'FP', value: FPCandidateValue_1, priority: 0, pageIndex: 0])
            Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
            candidatePool.each { candidate ->
                if (StringUtils.isBlank((String) candidate.value)) { return }
                def best = bestCandidateMap.get(candidate.fieldName)
                if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                    bestCandidateMap.put(candidate.fieldName, candidate)
                }
            }
            bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
            String url = resultMap.get('url')
            String FP = resultMap.get('FP')

            if (StringUtils.isNotBlank(FP)) {
                // 当前字段作为跳转链接，构造子页面请求并透传上下文字段
                String nextUrl = ParseToolsUtils.resolve(page.getRequest().getUrl(), FP)
                Request nextRequest = new Request(nextUrl)
                nextRequest.putExtra("parser", '0002')
                nextRequest.putExtra('url', url)
                nextRequest.putExtra('FP', nextUrl)
                // 添加子请求
                pageResult.addChildRequest(nextRequest)
            }
        }
        else if (StringUtils.equals(parser, '0002')) {
            // 页面: list
            // 提取上一个页面传递的字段信息
            String url = page.getRequest().getExtra('url')
            String FP = page.getRequest().getExtra('FP')

            // 列表选择器 - 抽取多个片段进行单独处理
            def nodes = new Html(page.getRawText()).xpath('//div[@id="r_con"]/div/table//tr[position()<5]//table//td').nodes() ?: []
            if (nodes == null || nodes.isEmpty()) {
                // 没有匹配到任何列表节点时，当前页面无需继续处理
                return pageResult
            }
            nodes.each { node ->
                // 处理各个独立片段
                Map<String, Object> resultMap = new LinkedHashMap<>()
                List<Map<String, Object>> candidatePool = new ArrayList<>()
                candidatePool.add([fieldName: 'url', value: url, priority: 0, pageIndex: 0])
                candidatePool.add([fieldName: 'FP', value: FP, priority: 0, pageIndex: 0])
                String PageTitleCandidateValue_0 = new Html(node.toString()).xpath('//a/text()').get()
                candidatePool.add([fieldName: 'PageTitle', value: PageTitleCandidateValue_0, priority: 0, pageIndex: 1])
                String titleCandidateValue_1 = "${PageTitleCandidateValue_0}"
                candidatePool.add([fieldName: 'title', value: titleCandidateValue_1, priority: 0, pageIndex: 1])
                String releaseDateCandidateValue_2 = new Html(node.toString()).xpath('//span[2]/text()').get()
                candidatePool.add([fieldName: 'releaseDate', value: releaseDateCandidateValue_2, priority: 0, pageIndex: 1])
                String webUrlCandidateValue_3 = new Html(node.toString()).xpath('//a/@href').get()
                candidatePool.add([fieldName: 'webUrl', value: webUrlCandidateValue_3, priority: 0, pageIndex: 1])
                Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
                candidatePool.each { candidate ->
                    if (StringUtils.isBlank((String) candidate.value)) { return }
                    def best = bestCandidateMap.get(candidate.fieldName)
                    if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                        bestCandidateMap.put(candidate.fieldName, candidate)
                    }
                }
                bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
                url = resultMap.get('url') ?: url
                FP = resultMap.get('FP') ?: FP
                String PageTitle = resultMap.get('PageTitle')
                String title = resultMap.get('title')
                String releaseDate = resultMap.get('releaseDate')
                String webUrl = resultMap.get('webUrl')

                if (StringUtils.isBlank(PageTitle)) {
                    // 必填字段缺失，不使用该条数据
                    return
                }
            }
        }
        else if (StringUtils.equals(parser, '0003')) {
            // 页面: text
            // 提取上一个页面传递的字段信息
            String url = page.getRequest().getExtra('url')
            String FP = page.getRequest().getExtra('FP')
            String PageTitle = page.getRequest().getExtra('PageTitle')
            String title = page.getRequest().getExtra('title')
            String releaseDate = page.getRequest().getExtra('releaseDate')
            String webUrl = page.getRequest().getExtra('webUrl')

            // 列表选择器 - 抽取多个片段进行单独处理
            def nodes = new Html(page.getRawText()).xpath('//div[@id="zoom"]').nodes() ?: []
            if (nodes == null || nodes.isEmpty()) {
                // 没有匹配到任何列表节点时，当前页面无需继续处理
                return pageResult
            }
            nodes.each { node ->
                // 处理各个独立片段
                Map<String, Object> resultMap = new LinkedHashMap<>()
                List<Map<String, Object>> candidatePool = new ArrayList<>()
                candidatePool.add([fieldName: 'url', value: url, priority: 0, pageIndex: 0])
                candidatePool.add([fieldName: 'FP', value: FP, priority: 0, pageIndex: 0])
                candidatePool.add([fieldName: 'PageTitle', value: PageTitle, priority: 0, pageIndex: 1])
                candidatePool.add([fieldName: 'title', value: title, priority: 0, pageIndex: 1])
                candidatePool.add([fieldName: 'releaseDate', value: releaseDate, priority: 0, pageIndex: 1])
                candidatePool.add([fieldName: 'webUrl', value: webUrl, priority: 0, pageIndex: 1])
                candidatePool.add([fieldName: 'FP', value: FP, priority: 0, pageIndex: 1])
                String BWBDKDQZCandidateValue_0 = new Html(node.toString()).regex('本外币各项贷款余额.*亿元，同比.*%，\\d+月份当月.*亿元，同比.*亿元，其中人民币贷款..\\d+\\.?\\d*亿元').get()
                candidatePool.add([fieldName: 'BWBDKDQZ', value: BWBDKDQZCandidateValue_0, priority: 0, pageIndex: 2])
                String RMBDKDQZCandidateValue_1 = new Html(node.toString()).regex('本外币各项贷款余额.*亿元，同比.*%，\\d+月份当月.*亿元，同比.*亿元，其中人民币贷款..\\d+\\.?\\d*亿元').get()
                candidatePool.add([fieldName: 'RMBDKDQZ', value: RMBDKDQZCandidateValue_1, priority: 0, pageIndex: 2])
                String BWBCKDQZCandidateValue_2 = new Html(node.toString()).regex('存款余额.*亿元，同比.*%，\\d+月份当月.*亿元，同比.*亿元，其中人民币存款.*亿元').get()
                candidatePool.add([fieldName: 'BWBCKDQZ', value: BWBCKDQZCandidateValue_2, priority: 0, pageIndex: 2])
                String RMBCKDQZCandidateValue_3 = new Html(node.toString()).regex('存款余额.*亿元，同比.*%，\\d+月份当月.*亿元，同比.*亿元，其中人民币存款.*亿元').get()
                candidatePool.add([fieldName: 'RMBCKDQZ', value: RMBCKDQZCandidateValue_3, priority: 0, pageIndex: 2])
                String BWBDKLJZCandidateValue_4 = new Html(node.toString()).regex('1-\\d+月份本外币各项贷款累计...*亿元').get()
                candidatePool.add([fieldName: 'BWBDKLJZ', value: BWBDKLJZCandidateValue_4, priority: 0, pageIndex: 2])
                String DQDKCandidateValue_5 = new Html(node.toString()).regex('短期贷款和中长期贷款分别...*亿元和.*亿元；').get()
                candidatePool.add([fieldName: 'DQDK', value: DQDKCandidateValue_5, priority: 0, pageIndex: 2])
                String ZCQDKCandidateValue_6 = new Html(node.toString()).regex('短期贷款和中长期贷款分别...*亿元和.*亿元；').get()
                candidatePool.add([fieldName: 'ZCQDK', value: ZCQDKCandidateValue_6, priority: 0, pageIndex: 2])
                String QSYDWDKCandidateValue_7 = new Html(node.toString()).regex('企（事）业单位贷款((?:(?!,|，|。|；|、).)*?)亿元').get()
                candidatePool.add([fieldName: 'QSYDWDK', value: QSYDWDKCandidateValue_7, priority: 0, pageIndex: 2])
                String ZHDKCandidateValue_8 = new Html(node.toString()).regex('住户贷款((?:(?!,|，|。|；|、).)*?)亿元').get()
                candidatePool.add([fieldName: 'ZHDK', value: ZHDKCandidateValue_8, priority: 0, pageIndex: 2])
                String BWBCKLJZCandidateValue_9 = new Html(node.toString()).regex('本外币各项存款累计((?:(?!,|，|。|；|、).)*?)亿元').get()
                candidatePool.add([fieldName: 'BWBCKLJZ', value: BWBCKLJZCandidateValue_9, priority: 0, pageIndex: 2])
                String FJRQYCKCandidateValue_10 = new Html(node.toString()).regex('非金融企业存款...*亿元，').get()
                candidatePool.add([fieldName: 'FJRQYCK', value: FJRQYCKCandidateValue_10, priority: 0, pageIndex: 2])
                String ZHCKCandidateValue_11 = new Html(node.toString()).regex('住户存款...*亿元').get()
                candidatePool.add([fieldName: 'ZHCK', value: ZHCKCandidateValue_11, priority: 0, pageIndex: 2])
                String fkCandidateValue_12 = "${BWBDKDQZCandidateValue_0}${RMBDKDQZCandidateValue_1}${BWBCKDQZCandidateValue_2}${RMBCKDQZCandidateValue_3}${BWBDKLJZCandidateValue_4}${DQDKCandidateValue_5}${ZCQDKCandidateValue_6}${QSYDWDKCandidateValue_7}${ZHDKCandidateValue_8}${BWBCKLJZCandidateValue_9}${ZHCKCandidateValue_11}"
                candidatePool.add([fieldName: 'fk', value: fkCandidateValue_12, priority: 0, pageIndex: 2])
                Map<String, Map<String, Object>> bestCandidateMap = new LinkedHashMap<>()
                candidatePool.each { candidate ->
                    if (StringUtils.isBlank((String) candidate.value)) { return }
                    def best = bestCandidateMap.get(candidate.fieldName)
                    if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.pageIndex > best.pageIndex)) {
                        bestCandidateMap.put(candidate.fieldName, candidate)
                    }
                }
                bestCandidateMap.each { k, v -> resultMap.put(k, v.value) }
                url = resultMap.get('url') ?: url
                FP = resultMap.get('FP') ?: FP
                PageTitle = resultMap.get('PageTitle') ?: PageTitle
                title = resultMap.get('title') ?: title
                releaseDate = resultMap.get('releaseDate') ?: releaseDate
                webUrl = resultMap.get('webUrl') ?: webUrl
                String BWBDKDQZ = resultMap.get('BWBDKDQZ')
                String RMBDKDQZ = resultMap.get('RMBDKDQZ')
                String BWBCKDQZ = resultMap.get('BWBCKDQZ')
                String RMBCKDQZ = resultMap.get('RMBCKDQZ')
                String BWBDKLJZ = resultMap.get('BWBDKLJZ')
                String DQDK = resultMap.get('DQDK')
                String ZCQDK = resultMap.get('ZCQDK')
                String QSYDWDK = resultMap.get('QSYDWDK')
                String ZHDK = resultMap.get('ZHDK')
                String BWBCKLJZ = resultMap.get('BWBCKLJZ')
                String FJRQYCK = resultMap.get('FJRQYCK')
                String ZHCK = resultMap.get('ZHCK')
                String fk = resultMap.get('fk')

                // 缺失发布时间，以当前时间为默认值，需要再次检查
                String pushtime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                // 返回采集到的数据
                List<Map<String, Pair<String, Object>>> dataList = new ArrayList<>()
                Map<String, Pair<String, Object>> dataMap = new LinkedHashMap<>()
                dataMap.put('url', Pair.of('url', url))
                dataMap.put(CrawlerDataCommonFieldsEnum.WEB_URL.getName(), Pair.of(CrawlerDataCommonFieldsEnum.WEB_URL.getAlias(), webUrl))
                dataMap.put('FP', Pair.of('反爬', FP))
                dataMap.put('PageTitle', Pair.of('页面标题（用于追溯问题）', PageTitle))
                dataMap.put(CrawlerDataCommonFieldsEnum.TITLE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.TITLE.getAlias(), title))
                dataMap.put(CrawlerDataCommonFieldsEnum.RELEASE_DATE.getName(), Pair.of(CrawlerDataCommonFieldsEnum.RELEASE_DATE.getAlias(), releaseDate))
                dataMap.put('BWBDKDQZ', Pair.of('本外币贷款当期值', BWBDKDQZ))
                dataMap.put('RMBDKDQZ', Pair.of('人民币贷款当期值', RMBDKDQZ))
                dataMap.put('BWBCKDQZ', Pair.of('本外币存款当期值', BWBCKDQZ))
                dataMap.put('RMBCKDQZ', Pair.of('人民币存款当期值', RMBCKDQZ))
                dataMap.put('BWBDKLJZ', Pair.of('本外币贷款累计值', BWBDKLJZ))
                dataMap.put('DQDK', Pair.of('短期贷款', DQDK))
                dataMap.put('ZCQDK', Pair.of('中长期贷款', ZCQDK))
                dataMap.put('QSYDWDK', Pair.of('企(事)业单位贷款', QSYDWDK))
                dataMap.put('ZHDK', Pair.of('住户贷款', ZHDK))
                dataMap.put('BWBCKLJZ', Pair.of('本外币存款累计值', BWBCKLJZ))
                dataMap.put('FJRQYCK', Pair.of('非金融企业存款', FJRQYCK))
                dataMap.put('ZHCK', Pair.of('住户存款', ZHCK))
                dataMap.put(CrawlerDataCommonFieldsEnum.PUSHTIME.getName(), Pair.of(CrawlerDataCommonFieldsEnum.PUSHTIME.getAlias(), pushtime))
                dataList.add(dataMap)
                pageResult.addDataMap(CrawlerDataConstants.CRAWLER_DATA_KEY, dataList)

                // 添加去重键
                pageResult.addDedupKey('url')
                pageResult.addDedupKey('FP')
                pageResult.addDedupKey('PageTitle')
                pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.TITLE.getName())
                pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.RELEASE_DATE.getName())
                pageResult.addDedupKey(CrawlerDataCommonFieldsEnum.WEB_URL.getName())
                pageResult.addDedupKey('BWBDKDQZ')
                pageResult.addDedupKey('RMBDKDQZ')
                pageResult.addDedupKey('BWBCKDQZ')
                pageResult.addDedupKey('RMBCKDQZ')
                pageResult.addDedupKey('BWBDKLJZ')
                pageResult.addDedupKey('DQDK')
                pageResult.addDedupKey('ZCQDK')
                pageResult.addDedupKey('QSYDWDK')
                pageResult.addDedupKey('ZHDK')
                pageResult.addDedupKey('BWBCKLJZ')
                pageResult.addDedupKey('FJRQYCK')
                pageResult.addDedupKey('ZHCK')
                pageResult.addDedupKey('fk')
            }
        }

        return pageResult
    }
}
