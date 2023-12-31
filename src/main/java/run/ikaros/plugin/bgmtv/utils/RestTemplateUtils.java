package run.ikaros.plugin.bgmtv.utils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import run.ikaros.plugin.bgmtv.repository.BgmTvRepositoryImpl;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestTemplateUtils {

    private static Map<String, RestTemplate> restTemplateProxyMap = new HashMap<>();
    private static RestTemplate restTemplate;
    private static RestTemplate httpComponentRestTemplate;
    public static final Integer DEFAULT_READ_TIMEOUT = 5000;
    public static final Integer DEFAULT_CONNECT_TIMEOUT = 5000;

    public static RestTemplate buildRestTemplate() {
        return buildRestTemplate(null, null);
    }

    public static synchronized RestTemplate buildRestTemplate(@Nullable Integer readTimeout,
                                                              @Nullable Integer connectTimeout) {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout);
            requestFactory.setConnectTimeout(
                connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout);
            restTemplate.setRequestFactory(requestFactory);
            log.debug("Build a no proxy rest template: {}", restTemplate);
        }
        return restTemplate;
    }

    public static synchronized RestTemplate buildProxyRestTemplate(
        @Nonnull Proxy proxy,
        @Nullable Integer readTimeout,
        @Nullable Integer connectTimeout
    ) {
        AssertUtils.notNull(proxy, "proxy");

        String key = proxy.address().toString();
        if (restTemplateProxyMap.containsKey(key)) {
            return restTemplateProxyMap.get(key);
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout);
        requestFactory.setConnectTimeout(
            connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout);
        requestFactory.setProxy(proxy);
        RestTemplate rt = new RestTemplate(requestFactory);
        restTemplateProxyMap.put(key, rt);
        return rt;
    }

    public static synchronized RestTemplate buildProxyRestTemplate(
        @Nonnull Proxy proxy
    ) {
        return buildProxyRestTemplate(proxy, null, null);
    }


    public static RestTemplate buildHttpProxyRestTemplate(
        @Nonnull String httpProxyHost,
        @Nonnull Integer httpProxyPort) {
        return buildHttpProxyRestTemplate(httpProxyHost, httpProxyPort, null, null);
    }

    public static RestTemplate buildHttpProxyRestTemplate(
        @Nonnull String httpProxyHost,
        @Nonnull Integer httpProxyPort,
        @Nullable Integer readTimeout,
        @Nullable Integer connectTimeout) {
        AssertUtils.notBlank(httpProxyHost, "httpProxyHost");
        AssertUtils.notNull(httpProxyPort, "httpProxyPort");
        InetSocketAddress inetSocketAddress =
            new InetSocketAddress(httpProxyHost, httpProxyPort);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, inetSocketAddress);
        return buildProxyRestTemplate(proxy, readTimeout, connectTimeout);
    }

    public static boolean testProxyConnect(@Nonnull String httpProxyHost,
                                           @Nonnull Integer httpProxyPort,
                                           @Nullable Integer readTimeout,
                                           @Nullable Integer connectTimeout) {
        AssertUtils.notBlank(httpProxyHost, "httpProxyHost");
        AssertUtils.notNull(httpProxyPort, "httpProxyPort");

        RestTemplate restTemplate =
            buildHttpProxyRestTemplate(httpProxyHost, httpProxyPort, readTimeout, connectTimeout);

        try {
            ResponseEntity<String> responseEntity =
                restTemplate.getForEntity("https://www.youtube.com/", String.class);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return true;
            }
        } catch (NestedRuntimeException exception) {
            Logger logger = LoggerFactory.getLogger(BgmTvRepositoryImpl.class);
            logger.warn("http proxy verify fail", exception);
        }

        return false;
    }
}
