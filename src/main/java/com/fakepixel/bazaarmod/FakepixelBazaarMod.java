package com.fakepixel.bazaarmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(modid = "fakepixelbazaarmod", name = "Fakepixel Bazaar Mod", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class FakepixelBazaarMod {

    public static boolean showPanel = true;
    public static int panelX = 10;
    public static int panelY = 50;
    public static int currentPage = 0;
    private boolean isDragging = false;
    private int dragStartX, dragStartY;

    // Price Cache structure to hold scraped data
    public static class MarketData {
        public String name;
        public long buyPrice = 0;
        public long sellPrice = 0;
        public long buyVol = 0;
        public long sellVol = 0;
        public long highAH = 0;
        public long lowAH = 0;
        public long highBIN = 0;
        public long lowBIN = 0;
        public long profit = 0;
    }

    public static final Map<String, MarketData> marketCache = new HashMap<>();
    public static List<MarketData> sortedFlips = new ArrayList<>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new ToggleCommand());
        // Hidden internal commands for handling the chat button clicks smoothly
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new InternalPrevCommand());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new InternalNextCommand());
    }

    // Main /b Command Handler
    public class ToggleCommand extends CommandBase {
        @Override
        public String getCommandName() { return "b"; }
        @Override
        public String getCommandUsage(ICommandSender sender) { return "/b"; }
        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            showPanel = !showPanel;
            sendPageNavigationChat();
        }
    }

    // Hidden command execution links for chat mechanics
    public class InternalPrevCommand extends CommandBase {
        @Override
        public String getCommandName() { return "b_internal_prev"; }
        @Override
        public String getCommandUsage(ICommandSender sender) { return ""; }
        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (currentPage > 0) currentPage--;
            sendPageNavigationChat();
        }
    }

    public class InternalNextCommand extends CommandBase {
        @Override
        public String getCommandName() { return "b_internal_next"; }
        @Override
        public String getCommandUsage(ICommandSender sender) { return ""; }
        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            int maxPages = (int) Math.ceil(sortedFlips.size() / 8.0) - 1;
            if (currentPage < Math.max(0, maxPages)) currentPage++;
            sendPageNavigationChat();
        }
    }

    // Chat Interface Setup with Clickable [◀ Prev] and [Next ▶]
    private void sendPageNavigationChat() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        IChatComponent message = new ChatComponentText(EnumChatFormatting.GOLD + "Bazaar Pages: ");
        
        IChatComponent prevBtn = new ChatComponentText(EnumChatFormatting.GREEN + "[◀ Prev] ");
        prevBtn.getChatStyle().setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND, "/b_internal_prev"));
        
        IChatComponent nextBtn = new ChatComponentText(EnumChatFormatting.GREEN + "[Next ▶]");
        nextBtn.getChatStyle().setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND, "/b_internal_next"));
        
        message.appendSibling(prevBtn).appendSibling(nextBtn);
        player.addChatMessage(message);
    }

    // Screen Scraper Engine
    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.BackgroundDrawnEvent event) {
        GuiScreen currentScreen = event.gui;
        if (currentScreen == null) return;
        scrapeOpenInventory();
    }

    private void scrapeOpenInventory() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.openContainer == null) return;

        for (ItemStack item : mc.thePlayer.openContainer.getInventory()) {
            if (item == null || !item.hasTagCompound()) continue;
            
            String name = EnumChatFormatting.getTextWithoutFormattingCodes(item.getDisplayName());
            NBTTagCompound tag = item.getTagCompound();
            
            if (tag.hasKey("display") && tag.getCompoundTag("display").hasKey("Lore")) {
                parseLoreStrings(name, tag.getCompoundTag("display").getTagList("Lore", 8).toString(), tag);
            }
        }
        recalculateTopFlips();
    }

    // Parsing data directly from the NBT layout data
    private void parseLoreStrings(String itemName, String loreRaw, NBTTagCompound tag) {
        MarketData data = marketCache.getOrDefault(itemName, new MarketData());
        data.name = itemName;

        String lore = EnumChatFormatting.getTextWithoutFormattingCodes(loreRaw).replace("\"", "");

        // Buy & Sell Price tracking patterns
        Matcher buyMatch = Pattern.compile("Buy(?: Price)?:?\\s*([\\d,]+)").matcher(lore);
        if (buyMatch.find()) data.buyPrice = Long.parseLong(buyMatch.group(1).replace(",", ""));

        Matcher sellMatch = Pattern.compile("Sell(?: Price)?:?\\s*([\\d,]+)").matcher(lore);
        if (sellMatch.find()) data.sellPrice = Long.parseLong(sellMatch.group(1).replace(",", ""));

        // Volume metric structural processing
        Matcher volMatch = Pattern.compile("([\\d,]+)/([\\d,]+)").matcher(lore);
        if (volMatch.find()) {
            data.buyVol = Long.parseLong(volMatch.group(1).replace(",", ""));
            data.sellVol = Long.parseLong(volMatch.group(2).replace(",", ""));
        }

        // AH and BIN internal data processing targets
        Matcher ahMatch = Pattern.compile("Highest AH:\\s*([\\d,]+).*?Lowest AH:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE).matcher(lore);
        if (ahMatch.find()) {
            data.highAH = Long.parseLong(ahMatch.group(1).replace(",", ""));
            data.lowAH = Long.parseLong(ahMatch.group(2).replace(",", ""));
        }

        Matcher binMatch = Pattern.compile("Highest BIN:\\s*([\\d,]+).*?Lowest BIN:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE).matcher(lore);
        if (binMatch.find()) {
            data.highBIN = Long.parseLong(binMatch.group(1).replace(",", ""));
            data.lowBIN = Long.parseLong(binMatch.group(2).replace(",", ""));
        }

        // Compute overall raw coin values 
        data.profit = data.sellPrice - data.buyPrice;
        
        // Skip junk items that have no structural value data recorded
        if (data.buyPrice > 0 || data.sellPrice > 0 || data.highAH > 0 || data.highBIN > 0) {
            marketCache.put(itemName, data);
        }
    }

    private void recalculateTopFlips() {
        sortedFlips = new ArrayList<>(marketCache.values());
        // Sorting sequence designed to keep highest absolute profits at top position
        Collections.sort(sortedFlips, (o1, o2) -> Long.compare(o2.profit, o1.profit));
    }

    // Customized Dynamic Tooltip Formatter
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null) return;
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(event.itemStack.getDisplayName());
        
        if (!marketCache.containsKey(name)) return;
        MarketData data = marketCache.get(name);
        
        // Completely replace existing lines to conform strictly to layout targets
        event.toolTip.clear();
        event.toolTip.add(EnumChatFormatting.BOLD + name);
        event.toolTip.add("Buy Price: " + EnumChatFormatting.YELLOW + String.format("%,d", data.buyPrice) + " coins");
        event.toolTip.add("Sell Price: " + EnumChatFormatting.YELLOW + String.format("%,d", data.sellPrice) + " coins");

        boolean isBazaarMenu = Minecraft.getMinecraft().currentScreen != null && 
                              Minecraft.getMinecraft().currentScreen.toString().toLowerCase().contains("bazaar");

        if (isBazaarMenu) {
            event.toolTip.add(EnumChatFormatting.GREEN + String.format("%,d", data.buyVol) + "/" + String.format("%,d", data.sellVol));
        } else {
            // Only add AH line if the data is active
            if (data.highAH > 0 || data.lowAH > 0) {
                event.toolTip.add(EnumChatFormatting.WHITE + "" + data.highAH + "/" + data.lowAH);
            }
            // Only add BIN line if the data is active
            if (data.highBIN > 0 || data.lowBIN > 0) {
                event.toolTip.add(EnumChatFormatting.RED + "" + data.highBIN + "/" + data.lowBIN);
            }
        }
    }

    // Draggable Overlay Window Processing
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !showPanel) return;

        Minecraft mc = Minecraft.getMinecraft();
        int x = panelX;
        int y = panelY;
        int w = 240;
        int h = 130;

        // Mouse positional mechanics translation logic
        ScaledResolution scaled = new ScaledResolution(mc);
        int mx = Mouse.getX() * scaled.getScaledWidth() / mc.displayWidth;
        int my = scaled.getScaledHeight() - (Mouse.getY() * scaled.getScaledHeight() / mc.displayHeight) - 1;

        if (Mouse.isButtonDown(0)) {
            if (!isDragging && mx >= x && mx <= x + w && my >= y && my <= y + 16) {
                isDragging = true;
                dragStartX = mx - panelX;
                dragStartY = my - panelY;
            } else if (isDragging) {
                panelX = mx - dragStartX;
                panelY = my - dragStartY;
            }
        } else {
            isDragging = false;
        }

        // Graphic drawing sequence
        GuiScreen.drawRect(x, y, x + w, y + h, 0x99000000); // Frame body
        GuiScreen.drawRect(x, y, x + w, y + 16, 0xCC111111); // Title banner
        
        String titleText = "BAZAAR TOP FLIPS [Pg. " + (currentPage + 1) + "]";
        mc.fontRendererObj.drawStringWithShadow(titleText, x + 5, y + 4, 0xFFFAA00);

        int startIndex = currentPage * 8;
        int renderY = y + 22;
        
        for (int i = startIndex; i < startIndex + 8 && i < sortedFlips.size(); i++) {
            MarketData entry = sortedFlips.get(i);
            String row = (i + 1) + ". " + entry.name + " " + EnumChatFormatting.GREEN + "+" + String.format("%,d", entry.profit);
            mc.fontRendererObj.drawStringWithShadow(row, x + 6, renderY, 0xFFFFFF);
            renderY += 12;
        }
    }
  }
