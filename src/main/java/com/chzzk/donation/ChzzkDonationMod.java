package com.chzzk.donation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("인벤세이브").executes(ctx -> {
                Boolean keepInv = DonationEffects.isKeepInventoryOn(ctx.getSource().getClient());
                String msg = (keepInv == null)
                    ? "[치지직 연동] 지금은 확인할 수 없어요 (월드에 접속되어 있어야 합니다)"
                    : "[치지직 연동] 인벤세이브 현재 상태: " + (keepInv ? "ON (사망해도 아이템 유지)" : "OFF (사망 시 아이템 드랍)");
                ctx.getSource().sendFeedback(Component.literal(msg));
                return 1;
            }));
        });

        Runtime.getRuntime().addShutdownHook(new Thread(DonationPoller::stop));
    }
}
