package dev.kaufman.spotify_to_youtube;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestController {

    @GetMapping("/convert_playlist")
    String convertPlaylist(@RequestParam("playlistId") String playlistId) {
        return PlaylistConversionService.convertPlaylist(playlistId);
    }
}
