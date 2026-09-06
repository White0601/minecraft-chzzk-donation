package com.chzzk.donation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public class ChzzkDonationMod implements ClientModInitializer {
    private static boolean wasInWorld = false;

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();

        if (!config.isConfigured()) {
            System.out.println("[ChzzkDonation] 설정 미완료. .minecraft/chzzk_donation.json 을 편집 후 재시작하세요.");
        } else {
            DonationPoller.start(config);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.player != null;
            // 월드 (재)입장 시점에 현재 연동 상태를 다시 안내
            if (inWorld && !wasInWorld) DonationPoller.forceReannounce();
            wasInWorld = inWorld;
            if (!inWorld) return;

            DonationEffects.tick(client);

            Integer statusChange = DonationPoller.consumeStatusChange();
            if (statusChange != null) {
                client.player.sendSystemMessage(Component.literal(DonationPoller.statusMessage(statusChange)));
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(DonationPoller::stop));
    }
}
