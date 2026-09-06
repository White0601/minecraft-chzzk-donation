package com.chzzk.donation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getGameDir().resolve("chzzk_donation.json");

    public String serverUrl      = "http://서버주소:5000";
    public String channelId      = "채널ID";
    public String pollToken      = "토큰";
    public int    pollIntervalMs = 2000;

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                System.err.println("[ChzzkDonation] 설정 파일 로드 실패: " + e.getMessage());
            }
        }
        // 기본 설정 파일 생성
        ModConfig cfg = new ModConfig();
        cfg.save();
        System.out.println("[ChzzkDonation] 설정 파일 생성됨: " + CONFIG_PATH);
        System.out.println("[ChzzkDonation] .minecraft/chzzk_donation.json 을 편집하세요.");
        return cfg;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            System.err.println("[ChzzkDonation] 설정 파일 저장 실패: " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return !serverUrl.contains("서버주소") && !channelId.equals("채널ID") && !pollToken.equals("토큰");
    }
}
