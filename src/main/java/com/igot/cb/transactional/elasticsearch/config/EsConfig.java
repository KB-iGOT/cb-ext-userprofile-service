package com.igot.cb.transactional.elasticsearch.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class EsConfig {
    @Value("${elasticsearch.sbESClient.host}")
    private String sbESClientHost;

    @Value("${elasticsearch.sbESClient.port}")
    private String sbESClientPort;

    @Value("${elasticsearch.sbESClient.username}")
    private String sbESClientUsername;

    @Value("${elasticsearch.sbESClient.password}")
    private String sbESClientPassword;

    @Value("${elasticsearch.igotESClient.host}")
    private String igotESClientHost;

    @Value("${elasticsearch.igotESClient.port}")
    private String igotESClientPort;

    @Value("${elasticsearch.igotESClient.username}")
    private String igotESClientUsername;

    @Value("${elasticsearch.igotESClient.password}")
    private String igotESClientPassword;

    @Bean(name = "sbESClient")
    @Primary
    public RestHighLevelClient sbESClient() {
        return createRestHighLevelClient(sbESClientHost, sbESClientPort);
    }

    @Bean(name = "igotESClient")
    public RestHighLevelClient igotESClient() {
        return createRestHighLevelClient(igotESClientHost, igotESClientPort);
    }

    private RestHighLevelClient createRestHighLevelClient(String hostsStr, String portsStr) {

        List<Integer> ports = new ArrayList<>();
        String[] hostArr = hostsStr.split(",");
        String[] portArr = portsStr.split(",");
        List<String> hosts = new ArrayList<>(Arrays.asList(hostArr));
        for (String val : portArr) {
            ports.add(Integer.parseInt(val));
        }
        HttpHost[] httpHosts = new HttpHost[hosts.size()];
        for (int i = 0; i < hosts.size(); i++) {
            httpHosts[i] = new HttpHost(hosts.get(i), ports.get(i));
        }

        RestClientBuilder builder = RestClient.builder(httpHosts)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(5000) // 5 seconds connect timeout
                        .setSocketTimeout(60000) // 60 seconds socket timeout
                )
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setConnectionRequestTimeout(60000) // 60 seconds max retry timeout
                                .build())
                ); // 60 seconds max retry timeout

        RestHighLevelClient restClient = new RestHighLevelClient(builder);
        log.info("ElasticsearchConfig:: RestHighLevelClient initialisation done.");
        return restClient;
    }
}
