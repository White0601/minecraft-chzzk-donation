package com.chzzk.donation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class DonationEffects {
    private static final Random RAND = new Random();
    private static final int SPIN_TICKS = 40;
    private static final int COOLDOWN_TICKS = 20; // 다음 이펙트 전 대기시간 (1초, 20 TPS)

    // ── 룰렛 타입 ─────────────────────────────────────────────────────────────
    private enum SpinType { ITEM, MOB, POTION, INVEN }

    // ── 룰렛 애니메이션 상태 ──────────────────────────────────────────────────
    private record SpinState(
        SpinType type,
        DonationEvent event,
        String winnerDisplay,  // 결과 표시 문자열
        String winnerLabel,    // 마지막 스핀 프레임에 고정 표시할 당첨 이름 (pool과 동일 포맷)
        int winnerSlot,        // INVEN: 삭제 슬롯 번호, POTION: 레벨(amplifier), 나머지: -1
        Object winnerData,     // Item / EntityType<?> / Holder<MobEffect> / null
        List<String> pool,     // 애니메이션 중 표시할 이름 목록
        int ticks
    ) {}

    private static volatile SpinState _spin = null;
    private static volatile int _cooldown = 0;

    // ── 전체 몹 목록 ──────────────────────────────────────────────────────────
    private static final List<EntityType<?>> ALL_MOBS = List.of(
        EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER,
        EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.ENDERMAN,
        EntityTypes.WITCH, EntityTypes.PILLAGER, EntityTypes.VINDICATOR,
        EntityTypes.PHANTOM, EntityTypes.DROWNED, EntityTypes.HUSK,
        EntityTypes.STRAY, EntityTypes.WITHER_SKELETON, EntityTypes.BLAZE,
        EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.ZOMBIE_VILLAGER,
        EntityTypes.SILVERFISH, EntityTypes.RAVAGER, EntityTypes.GUARDIAN,
        EntityTypes.SHULKER, EntityTypes.EVOKER, EntityTypes.VEX,
        EntityTypes.HOGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.ZOGLIN,
        EntityTypes.ELDER_GUARDIAN,
        EntityTypes.PIGLIN, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.WOLF,
        EntityTypes.BEE, EntityTypes.POLAR_BEAR, EntityTypes.IRON_GOLEM,
        EntityTypes.LLAMA, EntityTypes.TRADER_LLAMA, EntityTypes.ENDERMITE,
        EntityTypes.GOAT,
        EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.CHICKEN,
        EntityTypes.HORSE, EntityTypes.DONKEY, EntityTypes.MULE, EntityTypes.RABBIT,
        EntityTypes.FOX, EntityTypes.CAT, EntityTypes.OCELOT, EntityTypes.PARROT,
        EntityTypes.TURTLE, EntityTypes.PANDA, EntityTypes.DOLPHIN,
        EntityTypes.COD, EntityTypes.SALMON, EntityTypes.TROPICAL_FISH,
        EntityTypes.PUFFERFISH, EntityTypes.SQUID, EntityTypes.GLOW_SQUID,
        EntityTypes.AXOLOTL, EntityTypes.FROG, EntityTypes.ALLAY,
        EntityTypes.CAMEL, EntityTypes.SNIFFER, EntityTypes.ARMADILLO,
        EntityTypes.SNOW_GOLEM, EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER
    );

    // ── 야생 획득 가능 아이템 목록 ────────────────────────────────────────────
    static final List<Item> SURVIVAL_ITEMS = List.of(
        Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
        Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT,
        Items.COOKED_SALMON, Items.COOKED_COD, Items.GOLDEN_APPLE,
        Items.CARROT, Items.BAKED_POTATO, Items.BEETROOT,
        Items.MELON_SLICE, Items.PUMPKIN_PIE, Items.COOKIE,
        Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.GOLDEN_CARROT,
        Items.GLISTERING_MELON_SLICE, Items.HONEY_BOTTLE,
        Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
        Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE,
        Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE,
        Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL,
        Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE,
        Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.SHEARS,
        Items.BOW, Items.CROSSBOW, Items.ARROW, Items.SPECTRAL_ARROW, Items.TRIDENT,
        Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
        Items.GOLDEN_SWORD, Items.DIAMOND_SWORD,
        Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
        Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
        Items.SHIELD, Items.TURTLE_HELMET,
        Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD,
        Items.COAL, Items.COPPER_INGOT, Items.LAPIS_LAZULI,
        Items.AMETHYST_SHARD, Items.QUARTZ, Items.IRON_NUGGET, Items.GOLD_NUGGET,
        Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL,
        Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
        Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG,
        Items.OAK_PLANKS, Items.GLASS, Items.CHEST, Items.CRAFTING_TABLE,
        Items.FURNACE, Items.OBSIDIAN, Items.TNT, Items.BOOKSHELF,
        Items.IRON_ORE, Items.GOLD_ORE, Items.COAL_ORE, Items.DIAMOND_ORE,
        Items.EMERALD_ORE, Items.COPPER_ORE, Items.LAPIS_ORE, Items.REDSTONE_ORE,
        Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP,
        Items.STRING, Items.FEATHER, Items.LEATHER, Items.BONE,
        Items.GUNPOWDER, Items.FLINT, Items.STICK, Items.PAPER,
        Items.BLAZE_ROD, Items.ENDER_PEARL, Items.ENDER_EYE,
        Items.GHAST_TEAR, Items.SLIME_BALL, Items.SPIDER_EYE,
        Items.BONE_MEAL, Items.INK_SAC, Items.REDSTONE,
        Items.TORCH, Items.BUCKET, Items.WATER_BUCKET, Items.COMPASS,
        Items.CLOCK, Items.BOOK, Items.SADDLE, Items.NAME_TAG,
        Items.LEAD, Items.TOTEM_OF_UNDYING, Items.EXPERIENCE_BOTTLE,
        Items.ENDER_CHEST, Items.ENCHANTING_TABLE, Items.ANVIL,
        Items.ELYTRA, Items.FIREWORK_ROCKET
    );

    // ── 포션 효과 목록 ────────────────────────────────────────────────────────
    private static final List<Holder<MobEffect>> POTION_EFFECTS = List.of(
        MobEffects.SPEED, MobEffects.SLOWNESS, MobEffects.HASTE,
        MobEffects.MINING_FATIGUE, MobEffects.STRENGTH, MobEffects.JUMP_BOOST,
        MobEffects.NAUSEA, MobEffects.REGENERATION, MobEffects.RESISTANCE,
        MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY,
        MobEffects.BLINDNESS, MobEffects.NIGHT_VISION, MobEffects.HUNGER,
        MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
        MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION, MobEffects.LEVITATION,
        MobEffects.LUCK, MobEffects.UNLUCK, MobEffects.SLOW_FALLING,
        MobEffects.GLOWING, MobEffects.DARKNESS
    );

    // ── 이벤트 진입점 ─────────────────────────────────────────────────────────
    public static void apply(DonationEvent event, Minecraft client) {
        if (client.player == null) return;
        switch (event.effect()) {
            case "title"              -> showTitle(event, client);
            case "delete_item"        -> startInvenSpin(event, client);
            case "spawn_mobs"         -> startMobSpin(event, client);
            case "lightning"          -> spawnLightning(event, client);
            case "tnt"                -> spawnTnt(event, client);
            case "instant_kill"       -> instantKill(event, client);
            case "keep_inventory_on"  -> setKeepInventory(true, event, client);
            case "keep_inventory_off" -> setKeepInventory(false, event, client);
            case "random_item"        -> startItemSpin(event, client);
            case "random_potion"      -> startPotionSpin(event, client);
        }
    }

    // ── 매 틱 처리: 큐 디스패치 + 쿨다운 + 룰렛 애니메이션 ────────────────────
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        if (_spin != null) {
            tickSpin(client);
            return;
        }

        if (_cooldown > 0) {
            _cooldown--;
            return;
        }

        DonationEvent event = DonationQueue.poll();
        if (event == null) return;
        apply(event, client);
        if (_spin == null) {
            // 즉시 처리되는 이펙트(번개/TNT/즉사/인벤세이브/타이틀)는 스핀이 없으므로
            // 여기서 바로 다음 이펙트까지의 대기시간을 시작한다.
            _cooldown = COOLDOWN_TICKS;
        }
    }

    // ── 룰렛 애니메이션 진행 ─────────────────────────────────────────────────
    private static void tickSpin(Minecraft client) {
        SpinState spin = _spin;
        int t = spin.ticks;

        if (t <= 0) {
            // 당첨 확정
            showEffectTitle(spin.winnerDisplay, spin.event, client, 5, 60, 20);
            client.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            finishSpin(spin, client);
            _spin = null;
            _cooldown = COOLDOWN_TICKS;
            return;
        }

        // 슬롯머신 속도: 빠르게 → 점점 느리게
        int interval;
        float pitch;
        if (t > 25) { interval = 1;  pitch = 2.0f; }
        else if (t > 10) { interval = 2; pitch = 1.5f; }
        else { interval = 3; pitch = 1.0f; }

        if (t == 1) {
            // 마지막 프레임은 항상 당첨 아이템으로 고정해서 결과 발표와 자연스럽게 이어지게 함
            client.gui.hud.setTitle(Component.literal(spinTitleFor(spin.type)));
            client.gui.hud.setSubtitle(Component.literal(spin.winnerLabel));
            client.gui.hud.setTimes(0, 4, 0);
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, pitch);
        } else if (t % interval == 0) {
            String label = spin.pool.get(RAND.nextInt(spin.pool.size()));
            client.gui.hud.setTitle(Component.literal(spinTitleFor(spin.type)));
            client.gui.hud.setSubtitle(Component.literal(label));
            client.gui.hud.setTimes(0, 4, 0);
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, pitch);
        }

        _spin = new SpinState(spin.type, spin.event, spin.winnerDisplay, spin.winnerLabel,
            spin.winnerSlot, spin.winnerData, spin.pool, t - 1);
    }

    private static String spinTitleFor(SpinType type) {
        return switch (type) {
            case ITEM   -> "아이템 추첨 중...";
            case MOB    -> "몹 추첨 중...";
            case POTION -> "포션 추첨 중...";
            case INVEN  -> "삭제될 아이템...";
        };
    }

    // ── 당첨 후 실제 이펙트 실행 ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static void finishSpin(SpinState spin, Minecraft client) {
        ServerPlayer sp  = getServerPlayer(client);
        ServerLevel level = getServerLevel(client);

        switch (spin.type) {
            case ITEM -> {
                if (sp != null && spin.winnerData instanceof Item item) {
                    ItemStack stack = new ItemStack(item, 1);
                    if (!sp.getInventory().add(stack) || !stack.isEmpty()) sp.drop(stack, false);
                    sp.inventoryMenu.broadcastChanges();
                }
            }
            case MOB -> {
                if (level != null && spin.winnerData instanceof EntityType<?> type) {
                    var pos = client.player.position();
                    double angle = RAND.nextDouble() * Math.PI * 2;
                    double dist  = 2 + RAND.nextDouble() * 2;
                    var entity = type.create(level, EntitySpawnReason.COMMAND);
                    if (entity != null) {
                        entity.setPos(pos.x + Math.cos(angle) * dist, pos.y, pos.z + Math.sin(angle) * dist);
                        level.addFreshEntity(entity);
                    }
                }
            }
            case POTION -> {
                if (sp != null && spin.winnerData instanceof Holder<?> h) {
                    Holder<MobEffect> effect = (Holder<MobEffect>) h;
                    int duration  = (5 + RAND.nextInt(6)) * 20;
                    int amplifier = spin.winnerSlot; // 시작 시 결정된 레벨
                    sp.addEffect(new MobEffectInstance(effect, duration, amplifier));
                }
            }
            case INVEN -> {
                if (sp != null && spin.winnerSlot >= 0) {
                    sp.getInventory().setItem(spin.winnerSlot, ItemStack.EMPTY);
                    sp.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    // ── 공통 타이틀 표시 ─────────────────────────────────────────────────────
    private static void showEffectTitle(String result, DonationEvent event, Minecraft client) {
        showEffectTitle(result, event, client, 5, 50, 15);
    }

    private static void showEffectTitle(String result, DonationEvent event, Minecraft client,
                                        int fadeIn, int stay, int fadeOut) {
        String sub = event.nickname() + "  " + String.format("%,d", event.amount()) + "원";
        client.gui.hud.setTitle(Component.literal(result));
        client.gui.hud.setSubtitle(Component.literal(sub));
        client.gui.hud.setTimes(fadeIn, stay, fadeOut);
        client.player.sendSystemMessage(Component.literal(
            "[후원 이펙트] " + event.nickname() + " " + String.format("%,d", event.amount()) + "원 - " + result));
    }

    // ── 인벤세이브 상태 조회 (/인벤세이브 명령어용) ────────────────────────────
    public static Boolean isKeepInventoryOn(Minecraft client) {
        ServerLevel level = getServerLevel(client);
        if (level == null) return null;
        return level.getGameRules().get(GameRules.KEEP_INVENTORY);
    }

    // ── 서버 헬퍼 ────────────────────────────────────────────────────────────
    private static ServerLevel getServerLevel(Minecraft client) {
        if (client.getSingleplayerServer() == null) return null;
        return client.getSingleplayerServer().getLevel(client.player.level().dimension());
    }

    private static ServerPlayer getServerPlayer(Minecraft client) {
        if (client.getSingleplayerServer() == null) return null;
        return client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
    }

    // ── 타이틀 메시지 ─────────────────────────────────────────────────────────
    private static void showTitle(DonationEvent event, Minecraft client) {
        String amountStr = String.format("%,d", event.amount()) + "원 후원!";
        client.gui.hud.setTitle(Component.literal(event.nickname() + "님"));
        client.gui.hud.setSubtitle(Component.literal(amountStr));
        client.gui.hud.setTimes(10, 60, 20);
        client.player.sendSystemMessage(
            Component.literal("[후원] " + event.nickname() + " " + event.amount() + "원: " + event.message()));
    }

    // ── 인벤토리 룰렛 (delete_item) ──────────────────────────────────────────
    private static void startInvenSpin(DonationEvent event, Minecraft client) {
        ServerPlayer sp = getServerPlayer(client);
        if (sp == null) return;
        List<Integer> slots = new ArrayList<>();
        List<String> names  = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ItemStack stack = sp.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                slots.add(i);
                names.add(stack.getHoverName().getString());
            }
        }
        if (slots.isEmpty()) {
            showEffectTitle("인벤토리가 비어있어 살았다!", event, client);
            return;
        }
        int idx        = RAND.nextInt(slots.size());
        int winnerSlot = slots.get(idx);
        String winnerName = names.get(idx);
        _spin = new SpinState(SpinType.INVEN, event, winnerName + " 삭제!", winnerName,
            winnerSlot, null, names, SPIN_TICKS);
    }

    // ── 몹 소환 룰렛 (spawn_mobs) ────────────────────────────────────────────
    private static void startMobSpin(DonationEvent event, Minecraft client) {
        EntityType<?> winner = ALL_MOBS.get(RAND.nextInt(ALL_MOBS.size()));
        List<String> pool = ALL_MOBS.stream()
            .map(t -> t.getDescription().getString())
            .collect(Collectors.toList());
        String winnerName = winner.getDescription().getString();
        _spin = new SpinState(SpinType.MOB, event, winnerName + " 소환!", winnerName,
            -1, winner, pool, SPIN_TICKS);
    }

    // ── 번개 소환 (즉시, 룰렛 없음) ──────────────────────────────────────────
    private static void spawnLightning(DonationEvent event, Minecraft client) {
        ServerLevel level = getServerLevel(client);
        if (level == null) return;
        var pos = client.player.position();
        double angle = RAND.nextDouble() * Math.PI * 2;
        double dist  = 1 + RAND.nextDouble() * 2;
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
        if (bolt == null) return;
        bolt.setPos(pos.x + Math.cos(angle) * dist, pos.y, pos.z + Math.sin(angle) * dist);
        level.addFreshEntity(bolt);
        showEffectTitle("번개 소환!", event, client);
    }

    // ── TNT 소환 (플레이어 주변, 즉시) ───────────────────────────────────────
    private static void spawnTnt(DonationEvent event, Minecraft client) {
        ServerLevel level = getServerLevel(client);
        if (level == null) return;
        var pos = client.player.position();
        double angle = RAND.nextDouble() * Math.PI * 2;
        double dist  = 2 + RAND.nextDouble() * 2;
        PrimedTnt tnt = new PrimedTnt(level,
            pos.x + Math.cos(angle) * dist, pos.y + 1, pos.z + Math.sin(angle) * dist, null);
        tnt.setFuse(60);
        level.addFreshEntity(tnt);
        // 바닐라 TntBlock 점화 시와 동일하게 도화선 소리를 직접 재생해줘야 함
        // (PrimedTnt 엔티티 스폰만으로는 소리가 나지 않음)
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
        showEffectTitle("TNT!", event, client);
    }

    // ── 즉사 ─────────────────────────────────────────────────────────────────
    private static void instantKill(DonationEvent event, Minecraft client) {
        ServerPlayer sp   = getServerPlayer(client);
        ServerLevel level = getServerLevel(client);
        if (sp == null || level == null) return;
        showEffectTitle("즉사!", event, client);
        // fellOutOfWorld: 허공/심연 데미지 — 저항·갑옷 무시하고 즉사
        sp.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    // ── 인벤세이브 ON/OFF ────────────────────────────────────────────────────
    private static void setKeepInventory(boolean enabled, DonationEvent event, Minecraft client) {
        if (client.getSingleplayerServer() == null) return;
        ServerLevel level = getServerLevel(client);
        if (level == null) return;
        level.getGameRules().set(GameRules.KEEP_INVENTORY, enabled, client.getSingleplayerServer());
        showEffectTitle("인벤세이브 " + (enabled ? "ON" : "OFF") + "!", event, client);
    }

    // ── 랜덤 아이템 룰렛 (random_item) ──────────────────────────────────────
    private static void startItemSpin(DonationEvent event, Minecraft client) {
        Item winner = SURVIVAL_ITEMS.get(RAND.nextInt(SURVIVAL_ITEMS.size()));
        List<String> pool = SURVIVAL_ITEMS.stream()
            .map(i -> i.getName(new ItemStack(i)).getString())
            .collect(Collectors.toList());
        String winnerName = winner.getName(new ItemStack(winner)).getString();
        _spin = new SpinState(SpinType.ITEM, event, winnerName + " 획득!", winnerName,
            -1, winner, pool, SPIN_TICKS);
    }

    // ── 랜덤 포션 룰렛 (random_potion) ──────────────────────────────────────
    private static void startPotionSpin(DonationEvent event, Minecraft client) {
        Holder<MobEffect> winner = POTION_EFFECTS.get(RAND.nextInt(POTION_EFFECTS.size()));
        int amplifier = RAND.nextInt(2);
        List<String> pool = POTION_EFFECTS.stream()
            .map(e -> e.value().getDisplayName().getString())
            .collect(Collectors.toList());
        String winnerName = winner.value().getDisplayName().getString();
        String winnerDisplay = winnerName + " " + (amplifier + 1) + "레벨!";
        // winnerSlot 필드에 amplifier 저장
        _spin = new SpinState(SpinType.POTION, event, winnerDisplay, winnerName,
            amplifier, winner, pool, SPIN_TICKS);
    }
}
