package com.echestmod.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public class EChestMod implements ClientModInitializer {

    private static final int INTERVAL = 6000; // 5 daqiqa
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

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

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
                    waitingEchest = true;
                    waitTick = 0;
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!waitingEchest) return;
            if (!(screen instanceof GenericContainerScreen)) return;

            GenericContainerScreen containerScreen = (GenericContainerScreen) screen;
            GenericContainerScreenHandler handler = containerScreen.getScreenHandler();

            if (handler.getRows() != 3) return; // 27 slot = 3 row

            waitingEchest = false;
            waitTick = 0;

            new Thread(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                deposit(client, handler);
            }).start();
        });
    }

    private void deposit(MinecraftClient client, GenericContainerScreenHandler handler) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            // Player inventory: handler slots 27-62 (27 echest + 27 inv + 9 hotbar)
            int playerHandlerSlot = 27 + invSlot;
            if (playerHandlerSlot >= handler.slots.size()) continue;

            ItemStack stack = handler.slots.get(playerHandlerSlot).getStack();
            if (stack.isEmpty() || !VALUABLES.contains(stack.getItem())) continue;

            Item item = stack.getItem();
            int remaining = stack.getCount();

            // Avval bor slotlarni to'ldir
            for (int es = 0; es < 27 && remaining > 0; es++) {
                ItemStack esStack = handler.slots.get(es).getStack();
                if (esStack.isEmpty() || esStack.getItem() != item) continue;
                int cur = esStack.getCount();
                if (cur >= MAX_PER_SLOT) continue;
                int toAdd = Math.min(MAX_PER_SLOT - cur, remaining);
                remaining = doClick(client, handler, es, playerHandlerSlot, item, remaining, toAdd);
            }

            // Bo'sh slotlarga sol
            for (int es = 0; es < 27 && remaining > 0; es++) {
                if (!handler.slots.get(es).getStack().isEmpty()) continue;
                int toAdd = Math.min(MAX_PER_SLOT, remaining);
                remaining = doClick(client, handler, es, playerHandlerSlot, item, remaining, toAdd);
            }
        }
    }

    private int doClick(MinecraftClient client, GenericContainerScreenHandler handler,
                        int echestSlot, int playerSlot, Item item,
                        int remaining, int toAdd) {
        if (client.interactionManager == null) return remaining;

        client.interactionManager.clickSlot(
            handler.syncId,
            echestSlot,
            0,
            SlotActionType.QUICK_MOVE,
            client.player
        );

        return remaining - toAdd;
    }

    private boolean hasValuables(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && VALUABLES.contains(s.getItem())) return true;
        }
        return false;
    }
}
