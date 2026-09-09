package org.icedamericanomall.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * ElasticSearch 7.17 客户端配置 — 仅在 search.elasticsearch.enabled=true 时激活。
 * Docker 部署 xpack.security.enabled=false，无需认证。
 */
@Slf4j
@Configuration
public class ElasticSearchConfig {

    private final SearchProperties properties;

    public ElasticSearchConfig(SearchProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
                        HttpHost.create(properties.getHost() + ":" + properties.getPort()))
                .build();

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(transport);

        log.info("ElasticSearch client connected to {}:{}", properties.getHost(), properties.getPort());
        return client;
    }

    @Bean
    @ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
    public ElasticsearchOperations elasticsearchOperations(ElasticsearchClient client) {
        return new ElasticsearchTemplate(client);
    }
}
