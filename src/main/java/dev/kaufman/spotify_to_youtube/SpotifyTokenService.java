package dev.kaufman.spotify_to_youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SpotifyTokenService {
    public static String getToken() throws IOException, InterruptedException {

        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://accounts.spotify.com/api/token"))
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=ccccbddf7c114243ae440afa4ad75db6&client_secret=eb06b1672b5746e1902bb2b5675d8b8a"))
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Spotify token received...");

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode tokenNode = rootNode.path("access_token");
                return tokenNode.textValue();
            } else {
                Thread.sleep(5000);
                return getToken();
            }
        }
    }
}
