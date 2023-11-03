package io.github.kik.navivoicechangerex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class VoiceVoxEngineApi {
    private final String url;
    private final String username;
    private final String passowrd;

    public VoiceVoxEngineApi(String url, String username, String password)
    {
        this.url = url;
        this.username = username;
        this.passowrd = password;
    }

    public List<Player> players() throws IOException
    {
        var req = new Request.Builder()
                .url(HttpUrl.parse(url).resolve("speakers"))
                .get()
                .build();
        try (var res = new OkHttpClient().newCall(req).execute()) {
            var mapper = new ObjectMapper();
            return mapper.readValue(res.body().string(), new TypeReference<List<Player>>() {});
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        public final String name;
        public final String version;
        public final List<Style> styles;

        @JsonCreator
        public Player(@JsonProperty("name") String name, @JsonProperty("version") String version, @JsonProperty("styles") List<Style> styles)
        {
            this.name = name;
            this.version = version;
            this.styles = Collections.unmodifiableList(styles);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Style {
        public final String name;
        public final int id;

        @JsonCreator
        public Style(@JsonProperty("name") String name, @JsonProperty("id") int id)
        {
            this.name = name;
            this.id = id;
        }
    }
}
