package com.echestmod.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

    private static final int INTERVAL = 2400; // 2 daqiqa (keyingi takrorlanish uchun)
    private static final int MAX_PER_SLOT = 32; // 32 tadan oshmaslik qoidasi

    private static final List<Item> VALUABLES = List.of(
        Items.DIAMOND_BLOCK,
        Items.IRON_BLOCK,
        Items.EMERALD_BLOCK,
        Items.GOLD_BLOCK
    );

    private int tickCount = 0;
    private int step = 0; // 0: Kutish, 1: Echest buyrug'ini yuborish, 2: Oyna ochilishini kutish, 3: Depozit qilish
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

            // MODNI YOQISH
            while (startKey.wasPressed()) {
                enabled = true;
                step = 1; // 2 daqiqa kutmasdan, DARHOL birinchi amalga sakraymiz!
                tickCount = 0;
                waitTick = 0;
                client.player.sendMessage(Text.literal("§aEChest Mod ishga tushirildi!"), false);
            }

            // MODNI O'CHIRISH
            while (stopKey.wasPressed()) {
                enabled = false;
                step = 0;
                client.player.sendMessage(Text.literal("§cEChest Mod to'xtatildi!"), false);
            }

            if (!enabled) return;

            // AVTOMATIK SIKL LOGIKASI (Step-by-step drayver)
            switch (step) {
                case 0: // Oddiy kutish tartibi (Har 2 daqiqada bir marta tekshiradi)
                    tickCount++;
                    if (tickCount >= INTERVAL) {
                        tickCount = 0;
                        if (hasValuables(client)) {
                            step = 1; // Qimmatbaho blok topilsa, keyingi stepga o'tadi
                        }
                    }
                    break;

                case 1: // Chatga komanda yuborish
                    if (client.player.networkHandler != null) {
                        client.player.networkHandler.sendCommand("team echest");
                        client.player.sendMessage(Text.literal("§e[1] /team echest yuborildi. Oyna kutilmoqda..."), false);
                        waitTick = 0;
                        step = 2; // Oyna ochilishini kutish bosqichi
                    } else {
                        step = 0; // Agar tarmoq xatosi bo'lsa, ortga qaytadi
                    }
                    break;

                case 2: // EChest GUI oynasi ochilishini kutish (Lag va internet sekinligini hisobga oladi)
                    waitTick++;
                    if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                        GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                        // Echest odatda 3 qatorlik (27 slot) bo'ladi
                        if (handler.getRows() == 3) {
                            client.player.sendMessage(Text.literal("§e[2] EChest ochildi. Bloklar saralanmoqda..."), false);
                            
                            // Oyna ichidagi amallarni xavfsiz oqimda silliq bajarish
                            executeSafeDeposit(client, handler);
                            
                            waitTick = 0;
                            step = 0; // Sikl tugadi, yana 2 daqiqalik kutish rejimiga o'tadi
                        }
                    }
                    
                    // Agar internet judayam sekin bo'lib, 5 soniyada ham oyna ochilmasa, siklni qayta tiklaydi
                    if (waitTick > 100) {
                        client.player.sendMessage(Text.literal("§c[Xato] Internet sekin: EChest ochilmadi!"), false);
                        waitTick = 0;
                        step = 0; 
                    }
                    break;
            }
        });
    }

    // Xavfsiz depozit algoritmi (O'yinni qotirmasdan, drayver darajasida bosadi)
    private void executeSafeDeposit(MinecraftClient client, GenericContainerScreenHandler handler) {
        if (client.interactionManager == null || client.player == null) return;

        // Inventardagi 36 ta slotni aylanib chiqish
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack invStack = client.player.getInventory().getStack(invSlot);
            if (invStack.isEmpty() || !VALUABLES.contains(invStack.getItem())) continue;

            int handlerInvSlot = (invSlot < 9) ? (54 + invSlot) : (27 + (invSlot - 9));
            Item currentItem = invStack.getItem();

            // 1. Blokni kursorga ilib olish (Chap klik)
            client.interactionManager.clickSlot(handler.syncId, handlerInvSlot, 0, SlotActionType.PICKUP, client.player);

            // EChest ichidagi 27 ta slotga joylash xaritasi
            for (int es = 0; es < 27; es++) {
                ItemStack esStack = handler.slots.get(es).getStack();

                // Slot butunlay bo'sh bo'lsa
                if (esStack.isEmpty()) {
                    ItemStack cursorStack = handler.getCursorStack();
                    if (cursorStack.isEmpty()) break;

                    int toPut = Math.min(cursorStack.getCount(), MAX_PER_SLOT); // ko'pi bilan 32 ta

                    if (toPut == cursorStack.getCount()) {
                        // Agar kursordagi hamma blok 32 tadan kam bo'lsa, hammasini bittada qo'yadi
                        client.interactionManager.clickSlot(handler.syncId, es, 0, SlotActionType.PICKUP, client.player);
                    } else {
                        // 32 tadan ko'p bo'lsa, o'ng klik bilan roppa-rosa 32 ta tashlaydi
                        for (int k = 0; k < toPut; k++) {
                            client.interactionManager.clickSlot(handler.syncId, es, 1, SlotActionType.PICKUP, client.player);
                        }
                    }
                } 
                // Slotda o'sha blokdan bo'lsa va uning soni 32 tadan kam bo'lsa
                else if (esStack.getItem() == currentItem && esStack.getCount() < MAX_PER_SLOT) {
                    int spaceLeft = MAX_PER_SLOT - esStack.getCount(); // 32 tagacha qancha bo'sh joy borligi

                    for (int k = 0; k < spaceLeft; k++) {
                        if (handler.getCursorStack().isEmpty()) break;
                        client.interactionManager.clickSlot(handler.syncId, es, 1, SlotActionType.PICKUP, client.player);
                    }
                }

                // Agar kursor (qo'limiz) bo'shagan bo'lsa, keyingi slotga o'tamiz
                if (handler.getCursorStack().isEmpty()) {
                    break;
                }
            }

            // Agar kursorda blok ortib qolgan bo'lsa, uni o'z joyiga qaytaradi
            if (!handler.getCursorStack().isEmpty()) {
                client.interactionManager.clickSlot(handler.syncId, handlerInvSlot, 0, SlotActionType.PICKUP, client.player);
            }
        }

        // Ish tugagach oynani avtomatik yopish
        if (client.currentScreen != null) {
            client.currentScreen.close();
            client.player.sendMessage(Text.literal("§a§l[Muvaffaqiyatli] Bloklar (Maks 32) EChestga solindi va oyna yopildi!"), false);
        }
    }

    private boolean hasValuables(MinecraftClient client) {
        if (client.player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && VALUABLES.contains(s.getItem())) return true;
        }
        return false;
    }
}
