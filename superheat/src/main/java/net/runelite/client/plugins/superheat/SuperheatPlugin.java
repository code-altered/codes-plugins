/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.superheat;

import com.google.inject.Provides;
import com.owain.chinbreakhandler.ChinBreakHandler;

import java.awt.*;
import java.util.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.Point;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuOpcode;
import net.runelite.api.GameObject;


import net.runelite.api.events.*;
import net.runelite.api.events.Menu;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.game.ItemManager;


import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.eventbus.*;


import static net.runelite.client.plugins.superheat.SuperheatState.*;

import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.InterfaceUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.PlayerUtils;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.plugins.iutils.BankUtils;
import net.runelite.client.plugins.iutils.ActionQueue;


import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;


@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
        name = "Superheat",
        enabledByDefault = false,
        description = "Superheat",
        tags = {"Magic", "Superheat", "Smithing", "Casting"},
        type = PluginType.SKILLING
)
@Slf4j
public class SuperheatPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private iUtils utils;

    @Inject
    private BankUtils bank;

    @Inject
    private MouseUtils mouse;

    @Inject
    private PlayerUtils playerUtils;

    @Inject
    private InventoryUtils inventory;

    @Inject
    private InterfaceUtils interfaceUtils;

    @Inject
    private CalculationUtils calc;

    @Inject
    private ObjectUtils object;

    @Inject
    private MenuUtils menu;

    @Inject
    private ActionQueue action;

    @Inject
    private SuperheatConfig config;

    @Inject
    PluginManager pluginManager;

    @Inject
    OverlayManager overlayManager;

    @Inject
    SuperheatOverlay overlay;

    @Inject
    private ChinBreakHandler chinBreakHandler;


    CastType castType;
    Spells selectedSpell;
    SuperheatState state;
    Instant botTimer;
    MenuEntry targetMenu;
    GameObject targetObject;
    LocalPoint beforeLoc = new LocalPoint(0, 0); //initiate to mitigate npe
    Player player;
    WidgetItem targetItem;
    Rectangle clickBounds;

    int itemID = -1;
    int timeout = 0;
    int failureCount = 0;
    long sleepLength = 0;
    boolean startBot;
    private static final String OUT_OF_RUNES_MSG = "You do not have enough";
    private static final String UNREACHABLE_MSG = "I can't reach that";
    private final int MAX_FAILURE = 10;
    private final Set<Integer> requiredIds = new HashSet<>();

    @Override
    protected void startUp() {
        chinBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() {
        resetVals();
        chinBreakHandler.unregisterPlugin(this);
    }

    @Provides
    SuperheatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SuperheatConfig.class);
    }

    @Subscribe ///STATING
    private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
        if (!configButtonClicked.getGroup().equalsIgnoreCase("Superheat")) {
            return;
        }
        log.info("button {} pressed!", configButtonClicked.getKey());
        switch (configButtonClicked.getKey()) {
            case "startButton":
                if (!startBot) {
                    startBot = true;
                    chinBreakHandler.startPlugin(this);
                    botTimer = Instant.now();
                    state = null;
                    targetMenu = null;
                    timeout = 0;
                    botTimer = Instant.now();
                    initVals();
                    overlayManager.add(overlay);
                    requiredIds.add(561);
                } else {
                    resetVals();
                }
                break;
        }
    }

    public void initVals() {
        castType = config.getSpellType();
        itemID = config.itemID();
    }

    public void resetVals() {
        overlayManager.remove(overlay);
        chinBreakHandler.stopPlugin(this);
        startBot = false;
        castType = null;
        selectedSpell = null;
        botTimer = null;
        failureCount = 0;
        itemID = -1;
        timeout = 0;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (event.getGroup() != "Superheat") {
            return;
        }
        switch (event.getKey()) {
            case "itemID":
                itemID = config.itemID();
                log.debug("Item ID set to {}", itemID);
                break;
            case "getSpellType":
                castType = config.getSpellType();
                log.debug("Spell cast type set to {}", castType.getName());
                break;
//            case "getSpell":
//                selectedSpell = config.getSpell();
//                log.debug("Spell set to {}", selectedSpell.getName());
//                break;
        }
    }
    private long sleepDelay()
    {
        sleepLength = calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
        return sleepLength;
    }

    private int tickDelay()
    {
        int tickLength = (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
        log.debug("tick delay for {} ticks", tickLength);
        return tickLength;
    }
    private WidgetItem getItem()
    {
        log.debug("finding item");
        return inventory.getWidgetItem(itemID);
    }

    public SuperheatState getState() {
        if (timeout > 0) {
            return SuperheatState.TIMEOUT;
            }
        if (chinBreakHandler.shouldBreak(this)) {
            return SuperheatState.HANDLE_BREAK;
        }
        if (bank.isOpen()) {
            return getBankState();
        }
        if (inventory.containsItem(itemID)) {
            targetItem = getItem();
            return (targetItem != null && targetItem.getQuantity() > 0) ? SuperheatState.FIND_ITEM : SuperheatState.ITEM_NOT_FOUND;
        }
        if (inventory.containsItem(ItemID.IRON_BAR)) {
            return FIND_BANK;
        }
        return IDLING;
    }
    @Subscribe /// E D I T T H I S!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private void onGameTick(GameTick tick)
    {
        if (!startBot || chinBreakHandler.isBreakActive(this))
        {
            return;
        }
        player = client.getLocalPlayer();
        if (client != null && player != null && client.getGameState() == GameState.LOGGED_IN)
        {
            if (!client.isResized())
            {
                utils.sendGameMessage("illu - client must be set to resizable");
                startBot = false;
                return;
            }
            playerUtils.handleRun(40, 20);
            state = getState();
            beforeLoc = player.getLocalLocation();
            switch (state)
            {
                case TIMEOUT:
                    timeout--;
                    return;
                case ITEM_NOT_FOUND:
                    log.info("Item not found, config: {}");
                    if (config.logout())
                    {
                        interfaceUtils.logout();
                        resetVals();
                    }
                    else
                    {
                        timeout = tickDelay();
                    }
                    break;
                case HANDLE_BREAK:
                    chinBreakHandler.startBreak(this);
                    timeout = 10;
                    break;
                case WITHDRAWING_ITEMS:
                    withdrawItems();
                    break;
               case MISSING_REQUIRED_ITEMS:
                    startBot = false;
                    utils.sendGameMessage("Missing required items IDs: " + String.valueOf(requiredIds) + " from inventory. Stopping.");
                    resetVals();
                    break;
                case FIND_ITEM:
                    castSpell();
                    break;
                case FIND_BANK:
                    openBank();
                case DEPOSIT_ITEMS:
                    bank.depositAllExcept(requiredIds);
                    timeout = tickDelay();
                    break;
                case IDLING:
                    if (!inventory.containsItem(itemID))
                        openBank();
                    else if (inventory.containsItem(itemID))
                        bank.close();
                        castSpell();
            }
        }
        else
        {
            log.debug("client/player is null or bot isn't started");
        }
    }
    private void castSpell() {
        targetMenu = new MenuEntry("Cast", "", targetItem.getId(), MenuOpcode.ITEM_USE_ON_WIDGET.getId(), targetItem.getIndex(), 9764864, true);
        timeout = 2 + tickDelay();
        utils.oneClickCastSpell(WidgetInfo.SPELL_SUPERHEAT_ITEM, targetMenu, targetItem.getCanvasBounds().getBounds(), sleepDelay());
        return;
    }
    private void openBank() {
        GameObject bank = object.findNearestGameObject(config.bankID());
        if (bank != null) {
            targetMenu = new MenuEntry("", "", bank.getId(), MenuOpcode.GAME_OBJECT_SECOND_OPTION.getId(),
                    bank.getSceneMinLocation().getX(), bank.getSceneMinLocation().getY(), false);
            Rectangle rectangle = (bank.getConvexHull() != null) ? bank.getConvexHull().getBounds() :
                    new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
            ;
            utils.doActionMsTime(targetMenu, rectangle, sleepDelay());
        }
    }
    private SuperheatState getBankState() {
        if (inventory.containsItem(itemID)) {
            return FIND_ITEM;
        }
        if (inventory.containsItem(ItemID.IRON_BAR)) {
            return DEPOSIT_ITEMS;
        }
        if (!inventory.containsItem(itemID)) {
            return WITHDRAWING_ITEMS;
        } else {
            utils.sendGameMessage("Ran out of Items");
            shutDown();
        }
        return IDLING;
    }
//    private void withdrawItems() {
//        if (inventory.containsItem(ItemID.IRON_BAR))
//            bank.depositAllExcept(requiredIds);
//        else if (!inventory.containsItem(ItemID.IRON_ORE) && bank.isOpen()) {
//            bank.withdrawItemAmount(440,27);
//            tickDelay();
   /// }
           private void withdrawItems() {
              if (inventory.containsItem(ItemID.IRON_BAR))
                 bank.depositAllOfItem(ItemID.IRON_BAR);
             else if (!inventory.containsItem(itemID) && bank.isOpen()) {
                withdrawX(440); //440 = Iron Ore
                tickDelay();
            }
        }
    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) { log.info(event.toString()); }
    private void withdrawX(int ID){
    if(client.getVarbitValue(3960)!=27){
         bank.withdrawItemAmount(ID,27);
         timeout+=3;
     } else {
         targetMenu = new MenuEntry("", "", (client.getVarbitValue(6590) == 3) ? 1 : 5, MenuOpcode.CC_OP.getId(), bank.getBankItemWidget(ID).getIndex(), 786444, false);
         menu.setEntry(targetMenu);
         clickBounds = bank.getBankItemWidget(ID).getBounds()!=null ? bank.getBankItemWidget(ID).getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
         mouse.delayMouseClick(clickBounds,sleepDelay());
            }
     }
 }