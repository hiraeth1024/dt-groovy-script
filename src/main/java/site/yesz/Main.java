package site.yesz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.dtflys.forest.config.ForestConfiguration;
import com.gildata.spider.base.common.constant.CrawlerDataConstants;
import com.gildata.spider.base.common.enums.ServiceAppIdEnum;
import com.gildata.spider.base.common.enums.TaskInstanceTypeEnum;
import com.gildata.spider.base.common.exception.BizException;
import com.gildata.spider.base.common.exception.BizExceptionEnum;
import com.gildata.spider.base.common.manage.FileManage;
import com.gildata.spider.base.common.util.SpiderAssertUtil;
import com.gildata.spider.base.starter.groovy.GroovyScriptUtil;
import com.gildata.spider.base.starter.groovy.IparseScript;
import com.gildata.spider.base.starter.groovy.constants.GroovyConstants;
import com.gildata.spider.base.starter.groovy.enums.AntiDownloadTypeEnum;
import com.gildata.spider.base.starter.groovy.enums.CustomRequestTypeEnum;
import com.gildata.spider.base.starter.groovy.model.AntiPageRequest;
import com.gildata.spider.base.starter.groovy.model.ParsePageRequest;
import com.gildata.spider.base.starter.groovy.model.ParsePageResult;
import com.gildata.spider.base.starter.groovy.util.SpiderManagerUtils;
import com.gildata.spider.config.ProxyConfig;
import com.gildata.spider.downloader.OkHttp3WithProxyDownloader;
import com.gildata.spider.proxy.LocalHttpProxyProvider;
import com.gildata.spider.proxy.api.ProxyApiService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.time.Duration;
import java.util.*;

import static com.gildata.spider.constant.CrawlerConstants.REQUEST_DATA_DEDUP_KEY;

@Slf4j
@Getter
public class Main implements PageProcessor {
    private final static String LOG_LEVEL = "WARN";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; SpiderBot/1.0)";
    private final Site site = Site.me().setRetryTimes(3).setSleepTime(1000).setUserAgent(DEFAULT_USER_AGENT)
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
            .addHeader("Cache-Control", "no-cache");
    public final static String uploadUrl = "http://10.106.0.171:9988/api/file/uploadFile";
    public final static String downloadUrl = "http://10.106.0.171:9988/api/file/uploadFile";

    RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofSeconds(300))
            .build();
    private FileManage fileManage = new FileManage();

    private String groovyFileName = "地方经济四期/内蒙古自治区各区县预决算报告/鄂尔多斯-伊金霍洛旗-预算.groovy";
//    String startUrl = "https://www.energytrend.cn/solar-price.html";
    String startUrl = "https://czt.nmg.gov.cn/yjs/business/basic/select?id=150627zf&type=2&p=1";
//    String startUrl = "https://www.baidu.com/";
//    String startUrl = "https://www.spdrgoldshares.com/assets/dynamic/GLD/GLD_US_archive_EN.csv";
    String methodType = CustomRequestTypeEnum.GET.getMethodType();
    String contentType = CustomRequestTypeEnum.GET.getContentType();
    Map<String, Object> params = new HashMap<>();
    String taskType = TaskInstanceTypeEnum.PREVIEW_TASK.getType();
    boolean useProxy = false;


    public static void main(String[] args) throws Exception {
        Spider spider = null;

        try {
            setLogLevel(LOG_LEVEL);
            Main groovyTestProcessor = new Main();
            groovyTestProcessor.getFileManage().setRestTemplate(groovyTestProcessor.restTemplate);
            groovyTestProcessor.getFileManage().setUploadFileUrl(uploadUrl);
            groovyTestProcessor.getFileManage().setDownloadFileUrl(downloadUrl);

            LocalHttpProxyProvider proxyProvider = null;
            if(groovyTestProcessor.useProxy){
                ForestConfiguration cfg = ForestConfiguration.configuration();
                cfg.setBackendName("okhttp3");
                ProxyApiService proxyApiService = cfg.createInstance(ProxyApiService.class);
                proxyProvider = new LocalHttpProxyProvider(new ProxyConfig());
                proxyProvider.setProxyApiService(proxyApiService);
            }

            OkHttp3WithProxyDownloader okHttpDownloader = new OkHttp3WithProxyDownloader(proxyProvider);
            spider = Spider.create(groovyTestProcessor)
                    .setDownloader(okHttpDownloader)
                    .addRequest(groovyTestProcessor.createDynamicRequest())
                    .thread(5);
            spider.run();
        } catch (Exception e) {
            log.error("Spider运行失败", e);
            throw e;
        }
    }

    public Request createDynamicRequest() {
        Request request = new Request(this.startUrl);
        request.setMethod(this.methodType);

        if (CustomRequestTypeEnum.POST_JSON.getMethodType().equalsIgnoreCase(methodType)
                && CustomRequestTypeEnum.POST_JSON.getContentType().equalsIgnoreCase(contentType)) {
            SpiderManagerUtils.handlePostJSonRequest(request, params);
        } else if (CustomRequestTypeEnum.POST_FORM.getMethodType().equalsIgnoreCase(methodType)
                && CustomRequestTypeEnum.POST_FORM.getContentType().equalsIgnoreCase(contentType)) {
            SpiderManagerUtils.handlePostFormRequest(request, params);
        }

        handleAntiRequest(request);
        return request;
    }

    private void handleAntiRequest(Request request) {
        try {
            IparseScript script = GroovyScriptUtil.loadFromFile(11L, groovyFileName, 1);
            AntiPageRequest antiRequest = script.beforeDownloader(null);
            if (antiRequest == null) {
                log.warn("beforeDownloader解析为空,跳过反爬处理");
                return;
            }

            if (antiRequest.getAntiDownloadTypeEnum() == AntiDownloadTypeEnum.INNER_PYTHON) {
                String fanPaUrl = "http://10.106.25.22:1024/getHeaders";
                SpiderManagerUtils.handleInnerPythonAntiRequest(antiRequest, request, restTemplate, fanPaUrl);
            } else if (antiRequest.getAntiDownloadTypeEnum() == AntiDownloadTypeEnum.PLAYWRIGHT_COOKIE) {
                if (antiRequest.getRequest() != null && antiRequest.getRequest().getHeaders() != null) {
                    antiRequest.getRequest().getHeaders().forEach(request::addHeader);
                }
                request.putExtra("downloadTypeEnum", AntiDownloadTypeEnum.PLAYWRIGHT_COOKIE);
            } else {
                if (antiRequest.getRequest() != null && antiRequest.getRequest().getExtras() != null) {
                    antiRequest.getRequest().getExtras().forEach(request::putExtra);
                }
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            throw new BizException(BizExceptionEnum.SCRIPT_PARSE_ERROR, e);
        }
    }

    @Override
    public void process(Page page) {
        try {
            ParsePageRequest parsePageRequest = new ParsePageRequest();
            parsePageRequest.setPage(page);
            IparseScript script = GroovyScriptUtil.loadFromFile(11L, groovyFileName, 1);
            ParsePageResult pageResult = script.doParse(parsePageRequest);

            if (pageResult == null) {
                log.warn("解析结果为空对象");
                return;
            }

            if (!CollectionUtils.isEmpty(pageResult.getChildRequests())) {
                dealChildRequestHeaders(pageResult.getChildRequests(), page.getRequest());
                if (TaskInstanceTypeEnum.PREVIEW_TASK.getType().equals(taskType)) {
                    page.addTargetRequest(pageResult.getChildRequests().get(0));
                } else {
                    pageResult.getChildRequests().forEach(page::addTargetRequest);
                }
            }

            page.putField(REQUEST_DATA_DEDUP_KEY, pageResult.getDeDupKeyList());

            if (!CollectionUtils.isEmpty(pageResult.getOssFileBytes())) {
                SpiderAssertUtil.notNull(pageResult.getDataMap(), BizExceptionEnum.SCRIPT_PARSE_ERROR);
                @SuppressWarnings("unchecked")
                List<Map<String, Pair<String, String>>> dataMapList =
                    (List<Map<String, Pair<String, String>>>) pageResult.getDataMap()
                        .get(CrawlerDataConstants.CRAWLER_DATA_KEY);
                Map<String, Pair<String, String>> pageData = dataMapList.get(0);
                SpiderAssertUtil.notNull(pageData.get("filename"), BizExceptionEnum.SCRIPT_PARSE_ERROR, "文件名为空");

                String fileName = pageData.get("filename").getRight();
                String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
                byte[] rfFileBytes = pageResult.getOssFileBytes().get(0);

                String fileMd5 = fileManage.uploadFile(rfFileBytes, rfFileBytes.length,
                        fileName, ServiceAppIdEnum.DT_SPIDERCRAWLER_SRV);
                pageData.put("fileMd5", Pair.of("文件md5", fileMd5));
                pageData.put("fileType", Pair.of("文件类型", fileType));
                pageData.put("fileSize", Pair.of("文件大小", String.valueOf(rfFileBytes.length)));
                log.info("文件保存成功，md5={}", fileMd5);
            }

            if (pageResult.getDataMap() != null) {
                pageResult.getDataMap().forEach(page::putField);
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            throw new BizException(BizExceptionEnum.SCRIPT_PARSE_ERROR, e);
        }
    }

    private void dealChildRequestHeaders(List<Request> childRequestList, Request parentRequest) {
        if (CollectionUtils.isEmpty(childRequestList)) {
            return;
        }
        for (Request childRequest : childRequestList) {
            Object useLastRequestObj = childRequest.getExtra(GroovyConstants.USE_LAST_HEADERS);
            boolean useLastRequest = useLastRequestObj instanceof Boolean && (Boolean) useLastRequestObj;
            if (useLastRequest) {
                parentRequest.getHeaders().forEach(childRequest::addHeader);
            }
        }
    }

    public static void setLogLevel(String levelStr) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Level level = Level.valueOf(levelStr.toUpperCase());
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(level);
            Logger packageLogger = loggerContext.getLogger("com.gildata.spider");
            packageLogger.setLevel(level);
            log.warn("[Log Control] 日志级别已设置为: {}", levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[Log Control] 无效的日志级别: {}，可用值: TRACE, DEBUG, INFO, WARN, ERROR", levelStr);
        } catch (Exception e) {
            log.error("[Log Control] 设置日志级别失败: {}", e.getMessage());
        }
    }
}


