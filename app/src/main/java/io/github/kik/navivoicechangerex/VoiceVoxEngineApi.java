package io.github.kik.navivoicechangerex;

import androidx.core.util.Consumer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class VoiceVoxEngineApi {
    private final String url;
    private final String username;
    private final String password;

    private final OkHttpClient client;

    public VoiceVoxEngineApi(String url, String username, String password)
    {
        this.url = url;
        this.username = username;
        this.password = password;
        this.client = new OkHttpClient();
    }

    private Request.Builder requestBuilder(String command, Consumer<HttpUrl.Builder> buildUrl)
    {
        var uriBuilder = HttpUrl.parse(url).resolve(command).newBuilder();
        if (buildUrl != null) {
            buildUrl.accept(uriBuilder);
        }
        var builder = new Request.Builder()
                .url(uriBuilder.build());
        if (!username.isEmpty() && !password.isEmpty()) {
            builder.header("Authorization", Credentials.basic(username, password));
        }
        return builder;
    }

    private Request.Builder requestBuilder(String command)
    {
        return requestBuilder(command, null);
    }

    public List<Player> speakers() throws IOException
    {
        var req = requestBuilder("speakers")
                .get()
                .build();
        try (var res = client.newCall(req).execute()) {
            var mapper = new ObjectMapper();
            return mapper.readValue(res.body().string(), new TypeReference<List<Player>>() {});
        }
    }

    public String audio_query(int styleId, String text) throws IOException
    {
        var req = requestBuilder("audio_query",
                urlBuilder -> urlBuilder.addQueryParameter("speaker", Integer.toString(styleId))
                        .addQueryParameter("text", text))
                .post(RequestBody.create("", null))
                .build();
        try (var res = client.newCall(req).execute()) {
            return res.body().string();
        }
    }

    public byte[] synthesis(int styleId, String json) throws IOException
    {
        var req = requestBuilder("synthesis",
                urlBuilder -> urlBuilder.addQueryParameter("speaker", Integer.toString(styleId)))
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (var res = client.newCall(req).execute()) {
            return res.body().bytes();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        public final String name;
        public final String uuid;
        public final String version;
        public final List<Style> styles;

        @JsonCreator
        public Player(@JsonProperty("name") String name, @JsonProperty("speaker_uuid") String uuid, @JsonProperty("version") String version, @JsonProperty("styles") List<Style> styles)
        {
            this.name = name;
            this.uuid = uuid;
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
