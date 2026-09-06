package com.chzzk.donation;

import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class DonationPoller {
    private static ScheduledExecutorService scheduler;
    private static final Gson GSON = new Gson();

    public static void start(ModConfig config) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chzzk-donation-poller");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
            () -> poll(config),
            2000,
            config.pollIntervalMs,
            TimeUnit.MILLISECONDS
        );
        System.out.println("[ChzzkDonation] 폴링 시작: " + config.serverUrl + " (채널: " + config.channelId + ")");
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void poll(ModConfig config) {
        if (!config.isConfigured()) return;
        try {
            String urlStr = config.serverUrl + "/api/channels/" + config.channelId
                + "/minecraft/events?token=" + URLEncoder.encode(config.pollToken, StandardCharsets.UTF_8);
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            if (status != 200) {
                conn.disconnect();
                return;
            }

            try (InputStream is = conn.getInputStream();
                 Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonArray events = root.getAsJsonArray("events");
                if (events != null) {
                    for (JsonElement el : events) {
                        JsonObject ev = el.getAsJsonObject();
                        String nickname = ev.get("nickname").getAsString();
                        int    amount   = ev.get("amount").getAsInt();
                        String message  = ev.has("message") ? ev.get("message").getAsString() : "";
                        String effect   = ev.get("effect").getAsString();
                        DonationQueue.add(new DonationEvent(nickname, amount, message, effect));
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // 서버 연결 실패는 조용히 무시 (게임 중 잠깐 끊겨도 괜찮음)
        }
    }
}
