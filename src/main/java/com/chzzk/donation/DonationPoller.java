package com.chzzk.donation;

import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DonationPoller {
    private static ScheduledExecutorService scheduler;
    private static final Gson GSON = new Gson();

    // ── 연동 상태 알림 (입장 시 / 상태 변경 시 채팅으로 안내) ──────────────────
    public static final int STATUS_ENABLED  = 1;
    public static final int STATUS_DISABLED = 2;
    public static final int STATUS_ERROR    = 3;

    private static final AtomicReference<Integer> lastState = new AtomicReference<>(null);
    private static final AtomicBoolean needsAnnounce = new AtomicBoolean(false);

    private static void updateState(int newState) {
        Integer prev = lastState.getAndSet(newState);
        if (prev == null || !prev.equals(newState)) needsAnnounce.set(true);
    }

    /** 월드 (재)입장 시 호출: 이미 알고 있는 상태를 다시 한번 안내하도록 표시 */
    public static void forceReannounce() {
        if (lastState.get() != null) needsAnnounce.set(true);
    }

    /** 매 틱 호출: 안내할 상태가 있으면 반환 후 소비, 없으면 null */
    public static Integer consumeStatusChange() {
        return needsAnnounce.compareAndSet(true, false) ? lastState.get() : null;
    }

    public static String statusMessage(int state) {
        if (state == STATUS_ENABLED)  return "[치지직 연동] ✅ 연동 활성화됨 - 후원 이펙트가 정상 작동합니다";
        if (state == STATUS_DISABLED) return "[치지직 연동] ⏸ 연동이 꺼져 있습니다 - 대시보드에서 활성화해주세요";
        return "[치지직 연동] ⚠ 서버 연결 실패 - chzzk_donation.json 설정을 확인해주세요";
    }

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
                updateState(STATUS_ERROR);
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
                boolean enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
                updateState(enabled ? STATUS_ENABLED : STATUS_DISABLED);
            }
            conn.disconnect();
        } catch (Exception e) {
            // 서버 연결 실패는 조용히 무시 (게임 중 잠깐 끊겨도 괜찮음) - 단, 상태는 갱신해서 채팅으로 안내
            updateState(STATUS_ERROR);
        }
    }
}
