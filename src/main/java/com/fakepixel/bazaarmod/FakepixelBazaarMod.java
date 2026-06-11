package com.fakepixel.bazaarmod;

import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

@Mod(modid = FakepixelBazaarMod.MODID, version = FakepixelBazaarMod.VERSION, clientSideOnly = true)
public class FakepixelBazaarMod {
    public static final String MODID = "bazaarmod";
    public static final String VERSION = "1.0";
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // This tells Minecraft to listen for our mod's events
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Prints a message to your game logs when Minecraft starts up
        System.out.println("Fakepixel Bazaar Mod Loaded Successfully!");
    }

    // Example feature: Changes color or highlights bazaar text when it pops up in chat
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String message = event.message.getUnformattedText();
        
        // If the chat message mentions the Bazaar, add a custom tag to it
        if (message.contains("Bazaar") || message.contains("BAZAAR")) {
            String originalText = event.message.getFormattedText();
            event.message = new ChatComponentText(EnumChatFormatting.GOLD + "[BazaarHelper] " + EnumChatFormatting.RESET + originalText);
        }
    }
}
