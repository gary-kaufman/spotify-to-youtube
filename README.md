# Spotify to YouTube Playlist Converter 🎶➡️📺

This Java application exposes a localhost endpoint to which you can send requests with your Spotify playlistId in the parameters and it will return to you the link to a newly created YouTube playlist with those songs.

## To run locally

You will need a Google Developer Account so that you can download a `clientsecret.json` file that must be put inside the `resources` directory of this project.

You will also need to add the line `youtube.apikey=<Enter your api key here>` to the `application.properties` file of this project.

To start you will need to run the Maven commands `mvn clean install` and once it builds you can start the application.

Once the application is started you can make requests to the following endpoint: `http://localhost:8080/convert_playlist?playlistId=<Enter your Spotify playlistId here>`. I used Insomnia to make requests, Postman will work the same.

### Spotify Playlist Id

To find your Spotify playlistId, open the playlist in a browser and inspect the URL. 
You are looking for the random letters and numbers following `/playlist/`.
E.g.: For the URL, `https://open.spotify.com/playlist/37i9dQZF1DWUb0uBnlJuTi`,
the playlistId is `37i9dQZF1DWUb0uBnlJuTi`.
