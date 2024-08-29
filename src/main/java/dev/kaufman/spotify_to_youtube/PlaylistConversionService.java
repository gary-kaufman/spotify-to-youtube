package dev.kaufman.spotify_to_youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import java.security.GeneralSecurityException;
import java.util.stream.Collectors;

@Service
public class PlaylistConversionService {

    private static String youTubeKey;

    public PlaylistConversionService(@Value("${youtube.apikey}") String youTubeKey) {
        PlaylistConversionService.youTubeKey = youTubeKey;
    }

    private static final String CLIENT_SECRETS= "/client_secret.json";
    private static final Collection<String> SCOPES =
            List.of("https://www.googleapis.com/auth/youtube " +
                    "https://www.googleapis.com/auth/youtube.force-ssl " +
                    "https://www.googleapis.com/auth/youtubepartner");
    private static final String APPLICATION_NAME = "spotify-to-youtube";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();




    static String convertPlaylist(String playlistId) {

        String spotifyToken;
        ArrayList<String> spotifyArtistAndTitleList;
        String playlistTitle;

        try {
            spotifyToken = SpotifyTokenService.getToken();
            spotifyArtistAndTitleList = getArtistAndTitleList(playlistId, spotifyToken);
            playlistTitle = getSpotifyPlaylistTitle(playlistId, spotifyToken);
        } catch (Exception e) {
            return "Error collecting artists and titles from Spotify playlist...";
        }

        // Create YouTube Playlist by searching with the SpotifyArtistAndTitlePlaylist
        try {
            String youTubePlaylistUrl = createYouTubePlaylist(playlistTitle, spotifyArtistAndTitleList);
            return "https://www.youtube.com/playlist?list=" + youTubePlaylistUrl;
        } catch (Exception e) {
            return "Error creating YouTube playlist...";
        }
    }

    private static String getSpotifyPlaylistTitle(String playlistId, String spotifyToken) throws IOException, InterruptedException {

        // Make curl request to Spotify to receive the title of the playlist
        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.spotify.com/v1/playlists/" + playlistId + "?fields=name"))
                    .GET()
                    .setHeader("Authorization",
                            "Bearer " + spotifyToken)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());

                return rootNode.path("name").textValue();
            } else {
                return "Playlist Title";
            }
        }
    }

    static ArrayList<String> getArtistAndTitleList(String playlistId, String spotifyToken) throws IOException, InterruptedException {

        ArrayList<String> artistAndTitleList = new ArrayList<>();

        // Make curl request to Spotify to receive the results of the playlist data
        try (HttpClient client = HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.spotify.com/v1/playlists/" + playlistId + "?fields=tracks%28items%28track%28name%2C+artists%28name%29%29"))
                    .GET()
                    .setHeader("Authorization",
                            "Bearer " + spotifyToken)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode itemsNode = rootNode.path("tracks").path("items");

                for (int i=0; i < itemsNode.size(); i++) {
                    String title = itemsNode.get(i).path("track").path("name").textValue();
                    String artist = itemsNode.get(i).path("track").path("artists").get(0).path("name").textValue();
                    artistAndTitleList.add(artist + " " + title);
                }

            }
        }
        return artistAndTitleList;
    }

    static String createYouTubePlaylist(String playlistName, ArrayList<String> spotifyArtistsAndTitleList) throws GeneralSecurityException, IOException, InterruptedException {

        ArrayList<String> YouTubeVideoIds = getYouTubeVideoIds(spotifyArtistsAndTitleList);

        YouTube youtubeService = getService();

        // Define the Playlist object, which will be uploaded as the request body.
        Playlist playlist = new Playlist();

        // Add the snippet object property to the Playlist object.
        PlaylistSnippet snippet = new PlaylistSnippet();
        snippet.setTitle(playlistName);
        playlist.setSnippet(snippet);

        // Define and execute the API request
        YouTube.Playlists.Insert request = youtubeService.playlists()
                .insert(Collections.singletonList("snippet"), playlist);
        Playlist response = request.execute();
        System.out.println(response.getStatus());
        String playlistId = response.getId();

        // Add YouTubeVideoIds to Playlist
        // Define the PlaylistItem object, which will be uploaded as the request body.
        PlaylistItem playlistItem = new PlaylistItem();

        for (String videoId : YouTubeVideoIds) {
            // Add the snippet object property to the PlaylistItem object.
            PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
            playlistItemSnippet.setPlaylistId(playlistId);
            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#video");
            resourceId.setVideoId(videoId);
            playlistItemSnippet.setResourceId(resourceId);
            playlistItem.setSnippet(playlistItemSnippet);

            // Define and execute the API request
            YouTube.PlaylistItems.Insert playlistItemRequest = youtubeService.playlistItems()
                    .insert(Collections.singletonList("snippet"), playlistItem);
            PlaylistItem playListItemResponse = playlistItemRequest.execute();
            System.out.println(playListItemResponse.getStatus());
        }

        return playlistId;
    }

    private static ArrayList<String> getYouTubeVideoIds(ArrayList<String> spotifyArtistsAndTitleList) throws IOException, InterruptedException {
        ArrayList<String> youTubeVideoIds = new ArrayList<>();
        for (String artistAndTitle : spotifyArtistsAndTitleList) {

            String searchQuery = formatArtistAndTitle(artistAndTitle);

            // Make curl request to YouTube to receive the url of first video result
            try (HttpClient client = HttpClient.newHttpClient()) {

                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(
                                        "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&q=" +
                                                searchQuery +
                                                "&type=video&key=" +
                                                youTubeKey))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(response.body());
                    System.out.println("Adding videoId: " + rootNode.path("items").path(0).path("id").path("videoId").textValue());
                    youTubeVideoIds.add(rootNode.path("items").path(0).path("id").path("videoId").textValue());
                }
            }
        }

        return youTubeVideoIds;
    }

    private static String formatArtistAndTitle(String artistAndTitle) {
        String[] stringList = artistAndTitle.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
        ArrayList<String> words = new ArrayList<>(Arrays.asList(stringList));

        return words.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("%20"));
    }

    public static YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static Credential authorize(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets.
        InputStream in = PlaylistConversionService.class.getResourceAsStream(CLIENT_SECRETS);
        assert in != null;
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
}
