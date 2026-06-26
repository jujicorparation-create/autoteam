package com.echestmod.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class EChestMod implements ClientModInitializer {

    private static final int INTERVAL = 2400;
    private static final int MAX_PER_SLOT = 32;

    private static final List<Item> VALUABLES = List.of(
        Items.DIAMOND_BLOCK,
        Items.IRON_BLOCK,
        Items.EMERALD_BLOCK,
        Items.GOLD_BLOCK
    );

    private int tickCount = 0;
    private boolean waitingEchest = false;
    private int waitTick = 0;
    private boolean enabled = false;

    private static KeyBinding startKey;
    private static KeyBinding stopKey;

    @Override
    public void onInitializeClient() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Start EChest", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "EChest Mod"
        ));
        stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Stop EChest", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "EChest Mod"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (startKey.wasPressed()) {
                enabled = true;
                tickCount = 0;
                client.player.sendMessage(Text.literal("§aEChest Mod yoqildi!"), false);
            }
            while (stopKey.wasPressed()) {
                enabled = false;
                waitingEchest = false;
                client.player.sendMessage(Text.literal("§cEChest Mod o'chirildi!"), false);
            }

            if (!enabled) return;

            if (waitingEchest) {
                waitTick++;
                if (waitTick > 60) {
                    waitingEchest = false;
                    waitTick = 0;
                }
                return;
            }

            tickCount++;
            if (tickCount >= INTERVAL) {
                tickCount = 0;
                if (hasValuables(client)) {
                    client.player.networkHandler.sendChatCommand("team echest");
                    client.player.sendMessage(Text.literal("§e/team echest yozildi..."), false);
                    waitingEchest = true;
                    waitTick = 0;
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!enabled || !waitingEchest) return;
            if (!(screen instanceof GenericContainerScreen containerScreen)) return;

            GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
            if (handler.getRows() != 3) return;

            waitingEchest = false;
            waitTick = 0;

            new Thread(() -> {
                try { Thread.sleep(250); } catch (InterruptedException e) { return; }
                client.execute(() -> {
                    depositAll(client, handler);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§aDepozit qilindi!"), false);
                    }
                });
            }).start();
        });
    }

    private void depositAll(MinecraftClient client, GenericContainerScreenHandler handler) {
        if (client.player == null) return;

        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack stack = client.player.getInventory().getStack(invSlot);
            if (stack.isEmpty() || !VALUABLES.contains(stack.getItem())) continue;

            Item item = stack.getItem();

            for (int es = 0; es < 27; es++) {
                ItemStack esStack = handler.slots.get(es).getStack();

                boolean canPlace = esStack.isEmpty() ||
                    (esStack.getItem() == item && esStack.getCount() < MAX_PER_SLOT);
                if (!canPlace) continue;

                int handlerPlayerSlot = 27 + invSlot;

                client.interactionManager.clickSlot(
                    handler.syncId, handlerPlayerSlot, 0,
                    SlotActionType.PICKUP, client.player);

                ItemStack cursor = handler.getCursorStack();
                int curEchestCount = esStack.isEmpty() ? 0 : esStack.getCount();
                int toPlace = Math.min(cursor.getCount(), MAX_PER_SLOT - curEchestCount);

                for (int t = 0; t < toPlace; t++) {
                    client.interactionManager.clickSlot(
                        handler.syncId, es, 1,
                        SlotActionType.PICKUP, client.player);
                }

                if (!handler.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(
                        handler.syncId, handlerPlayerSlot, 0,
                        SlotActionType.PICKUP, client.player);
                }
                break;
            }
        }
    }

    private boolean hasValuables(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && VALUABLES.contains(s.getItem())) return true;
        }
        return false;
    }
                        }
