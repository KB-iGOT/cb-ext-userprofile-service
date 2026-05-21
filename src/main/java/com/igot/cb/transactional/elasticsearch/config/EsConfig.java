package com.igot.cb.transactional.elasticsearch.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
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

@Configuration
@Slf4j
public class EsConfig {
    @Value("${elasticsearch.userEsClient.host}")
    private String userEsClientHost;

    @Value("${elasticsearch.userEsClient.username}")
    private String userEsClientUsername;

    @Value("${elasticsearch.userEsClient.password}")
    private String userEsClientPassword;

    @Value("${elasticsearch.igotESClient.host}")
    private String igotESClientHost;

    @Value("${elasticsearch.igotESClient.username}")
    private String igotESClientUsername;

    @Value("${elasticsearch.igotESClient.password}")
    private String igotESClientPassword;

    public String[] getUserEsHostList() {
        return userEsClientHost.split(",", -1);
    }

    public String[] getIgotESClientHostList() {
        return igotESClientHost.split(",", -1);
    }

    @Bean(name = "userEsClient")
    @Primary
    public RestHighLevelClient userEsClient() {
        return createRestHighLevelClient(getUserEsHostList(), userEsClientUsername, userEsClientPassword);
    }

    @Bean(name = "igotESClient")
    public RestHighLevelClient igotESClient() {
        return createRestHighLevelClient(getIgotESClientHostList(), igotESClientUsername, igotESClientPassword);
    }

    private RestHighLevelClient createRestHighLevelClient(String[] hosts, String user, String password) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));

        HttpHost[] httpHosts = new HttpHost[hosts.length];
        for (int i = 0; i < httpHosts.length; i++) {
            String hostIp = hosts[i].split(":")[0];
            String hostPort = hosts[i].split(":")[1];
            httpHosts[i] = new HttpHost(hostIp, Integer.parseInt(hostPort));
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
