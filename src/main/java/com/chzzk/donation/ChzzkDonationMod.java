package com.chzzk.donation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ChzzkDonationMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();

        if (!config.isConfigured()) {
            System.out.println("[ChzzkDonation] 설정 미완료. .minecraft/chzzk_donation.json 을 편집 후 재시작하세요.");
        } else {
            DonationPoller.start(config);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            DonationEffects.tick(client);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(DonationPoller::stop));
    }
}
