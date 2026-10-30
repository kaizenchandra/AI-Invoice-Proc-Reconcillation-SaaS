package com.kaizen.invoice.infrastructure;

import com.kaizen.invoice.application.DocumentStorage;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class S3DocumentStorage implements DocumentStorage {
  private final S3Client s3;
  private final String bucket;

  public S3DocumentStorage(
      @Value("${app.storage.endpoint}") String endpoint,
      @Value("${app.storage.region}") String region,
      @Value("${app.storage.bucket}") String bucket,
      @Value("${app.storage.access-key}") String key,
      @Value("${app.storage.secret-key}") String secret) {
    var builder = S3Client.builder().region(Region.of(region)).forcePathStyle(true);
    if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
    if (!key.isBlank())
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret)));
    else builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    this.s3 = builder.build();
    this.bucket = bucket;
  }

  public void put(String key, byte[] data, String mime) {
    s3.putObject(b -> b.bucket(bucket).key(key).contentType(mime), RequestBody.fromBytes(data));
  }

  public byte[] get(String key) {
    return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
  }
}
