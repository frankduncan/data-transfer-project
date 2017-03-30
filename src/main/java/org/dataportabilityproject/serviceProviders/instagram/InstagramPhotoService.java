package org.dataportabilityproject.serviceProviders.instagram;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import org.dataportabilityproject.dataModels.Exporter;
import org.dataportabilityproject.dataModels.photos.Photo;
import org.dataportabilityproject.serviceProviders.instagram.model.MediaFeedData;
import org.dataportabilityproject.serviceProviders.instagram.model.MediaResponse;

final class InstagramPhotoService implements Exporter<Photo> {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);;
  private static final JacksonFactory JSON_FACTORY = new JacksonFactory();

  private final String token;
  private final HttpTransport httpTransport;

  InstagramPhotoService(String clientId, String clientSecret) {
    try {
      this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      this.token = getToken(clientId, clientSecret);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Problem getting token", e);
    }
  }

  @Override
  public Collection<Photo> export() throws IOException {
    MediaResponse response = makeRequest(
        "https://api.instagram.com/v1/users/self/media/recent",
        MediaResponse.class);

    ImmutableList.Builder<Photo> results = ImmutableList.builder();

    for (MediaFeedData photo : response.getData()) {
      results.add(new Photo("Instagram photo: " + photo.getId(),
          photo.getImages().getStandardResolution().getUrl(),
          photo.getCaption().getText(),
          null));
    }

    return results.build();
  }

  private String getToken(String clientId, String clientSecret)
      throws IOException, GeneralSecurityException {
    AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
        BearerToken.authorizationHeaderAccessMethod(), // Access Method
        httpTransport,
        JSON_FACTORY,
        new GenericUrl("https://api.instagram.com/oauth/access_token"), // GenericUrl
        new ClientParametersAuthentication(
            clientId,
            clientSecret),
        clientId, // clientId
        "https://api.instagram.com/oauth/authorize/") // encoded url
        .setScopes(ImmutableList.of("basic", "public_content")) // scopes
        .build();

    VerificationCodeReceiver receiver = new LocalServerReceiver.Builder()
        .setHost("localhost").setPort(12345).build();
    try {
      Credential result = new AuthorizationCodeInstalledApp(flow, receiver)
          .authorize("user");
      return result.getAccessToken();
    } catch (Exception e) {
      throw new IOException("Couldn't authorize", e);
    }
  }

  private <T> T makeRequest(String url, Class<T> clazz) throws IOException {
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
    HttpRequest getRequest = requestFactory.buildGetRequest(
        new GenericUrl(url + "?access_token=" + token));
    HttpResponse response = getRequest
        .execute();
    int statusCode = response.getStatusCode();
    if (statusCode != 200) {
      throw new IOException("Bad status code: " + statusCode + " error: "
          + response.getStatusMessage());
    }
    String result = CharStreams.toString(new InputStreamReader(
        response.getContent(), Charsets.UTF_8));
    return MAPPER.readValue(result, clazz);
  }
}