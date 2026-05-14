package com.rabbithole.config;

import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class OkHttpConfig {

    @Value("${http.client.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${http.client.read-timeout-ms:30000}")
    private long readTimeoutMs;

    @Value("${http.client.write-timeout-ms:30000}")
    private long writeTimeoutMs;

    @Value("${http.client.call-timeout-ms:35000}")
    private long callTimeoutMs;

    @Value("${http.client.slow-call-ms:2000}")
    private long slowCallMs;

    @Bean
    public OkHttpClient okHttpClient() {
        try {
            // Trust all certificates (needed for Netease CDN)
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAll, new java.security.SecureRandom());

            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .addInterceptor(loggingInterceptor())
                    .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .build();
        } catch (Exception e) {
            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .addInterceptor(loggingInterceptor())
                    .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .build();
        }
    }

    private Interceptor loggingInterceptor() {
        return chain -> {
            Request request = chain.request();
            long start = System.currentTimeMillis();
            try {
                Response response = chain.proceed(request);
                long cost = System.currentTimeMillis() - start;
                if (!response.isSuccessful() || cost >= slowCallMs) {
                    log.warn("outbound_http method={} url={} status={} costMs={}",
                            request.method(), request.url(), response.code(), cost);
                }
                return response;
            } catch (IOException e) {
                long cost = System.currentTimeMillis() - start;
                log.warn("outbound_http_failed method={} url={} costMs={} error={}",
                        request.method(), request.url(), cost, e.toString());
                throw e;
            }
        };
    }
}
