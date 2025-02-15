package com.shelfsync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS SDK v2 clients. When {@code shelfsync.aws.endpoint-override} is set (e.g. LocalStack),
 * every client is pointed at that endpoint and S3 uses path-style addressing.
 */
@Configuration
public class AwsConfig {

    private final ShelfSyncProperties.Aws aws;

    public AwsConfig(ShelfSyncProperties properties) {
        this.aws = properties.aws();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return configure(DynamoDbClient.builder()).build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public S3Client s3Client() {
        return configure(S3Client.builder())
                .serviceConfiguration(s3Configuration())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(aws.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(s3Configuration());
        if (aws.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(aws.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public LambdaClient lambdaClient() {
        return configure(LambdaClient.builder()).build();
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(aws.hasEndpointOverride())
                .build();
    }

    private <B extends AwsClientBuilder<B, C>, C> B configure(B builder) {
        builder.region(Region.of(aws.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (aws.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(aws.endpointOverride()));
        }
        return builder;
    }
}
