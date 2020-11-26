package net.runelite.client.plugins.glassmaker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.api.MenuEntry;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.iutils.BankUtils;
import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.InterfaceUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.NPCUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.PlayerUtils;
import net.runelite.client.plugins.iutils.WalkUtils;
import net.runelite.client.plugins.iutils.KeyboardUtils;
import net.runelite.client.plugins.iutils.iUtils;



import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.glassmaker.glassmakerState.*;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "Oofie GlassMaker",
	enabledByDefault = false,
	description = "Makes Glass",
	tags = {"glass, maker, crafting, oofie"},
	type = PluginType.SKILLING
)
@Slf4j
public class glassmakerPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private glassmakerConfiguration config;

	@Inject
	private iUtils utils;

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
	private MenuUtils menu;

	@Inject
	private ObjectUtils object;

	@Inject
	private BankUtils bank;

	@Inject
	private NPCUtils npc;

	@Inject
	private KeyboardUtils key;

	@Inject
	private WalkUtils walk;

	@Inject
	private ConfigManager configManager;

	@Inject
	PluginManager pluginManager;

	@Inject
	OverlayManager overlayManager;

	@Inject
	private glassmakerOverlay overlay;


	glassmakerState state;
	GameObject targetObject;
	NPC targetNPC;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;


	WorldArea EDGE = new WorldArea(new WorldPoint(3084, 3486, 0), new WorldPoint(3100, 3501, 0));
	WorldPoint FURNACE = new WorldPoint(3109, 3499, 0);


	int timeout = 0;
	long sleepLength;
	boolean startGlassMaker;
	private final Set<Integer> itemIds = new HashSet<>();
	private final Set<Integer> requiredIds = new HashSet<>();
	Rectangle clickBounds;

	@Provides
	glassmakerConfiguration provideConfig(ConfigManager configManager) {
		return configManager.getConfig(glassmakerConfiguration.class);
	}

	private void resetVals() {
		overlayManager.remove(overlay);
		state = null;
		timeout = 0;
		botTimer = null;
		skillLocation = null;
		startGlassMaker = false;
		requiredIds.clear();
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("glassmaker")) {
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton")) {
			if (!startGlassMaker) {
				startGlassMaker = true;
				state = null;
				targetMenu = null;
				botTimer = Instant.now();
				setLocation();
				overlayManager.add(overlay);
			} else {
				resetVals();
			}
		}
	}

	@Override
	protected void shutDown() {
		// runs on plugin shutdown
		overlayManager.remove(overlay);
		log.info("Plugin stopped");
		startGlassMaker = false;
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("plankmaker")) {
			return;
		}
		startGlassMaker = false;
	}

	public void setLocation() {
		if (client != null && client.getLocalPlayer() != null && client.getGameState().equals(GameState.LOGGED_IN)) {
			skillLocation = client.getLocalPlayer().getWorldLocation();
			beforeLoc = client.getLocalPlayer().getLocalLocation();
		} else {
			log.debug("Tried to start bot before being logged in");
			skillLocation = null;
			resetVals();
		}
	}

	private long sleepDelay() {
		sleepLength = calc.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		return sleepLength;
	}

	private int tickDelay() {
		int tickLength = (int) calc.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
		log.debug("tick delay for {} ticks", tickLength);
		return tickLength;
	}

	private void openBank() {
		targetNPC = npc.findNearestNpc(1618);
		if (npc != null) {
			targetMenu = new MenuEntry("", "",
					targetNPC.getIndex(), MenuOpcode.NPC_THIRD_OPTION.getId(), 0, 0, false);
			menu.setEntry(targetMenu);
			mouse.delayMouseClick(targetNPC.getConvexHull().getBounds(), sleepDelay());
		}
	}

	private void useFurnace() {
		targetObject = object.findNearestGameObject(16469);
		if (targetObject != null) {
			targetMenu = new MenuEntry("Smelt", "<col=ffff>Furnace", targetObject.getId(), 4, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			menu.setEntry(targetMenu);
			mouse.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
	}

	private Point getRandomNullPoint() {
		if (client.getWidget(161, 34) != null) {
			Rectangle nullArea = client.getWidget(161, 34).getBounds();
			return new Point((int) nullArea.getX() + calc.getRandomIntBetweenRange(0, nullArea.width), (int) nullArea.getY() + calc.getRandomIntBetweenRange(0, nullArea.height));
		}

		return new Point(client.getCanvasWidth() - calc.getRandomIntBetweenRange(0, 2), client.getCanvasHeight() - calc.getRandomIntBetweenRange(0, 2));
	}

	private glassmakerState getBankState() {
		if (inventory.containsItem(ItemID.BUCKET_OF_SAND) && inventory.containsItem(ItemID.SODA_ASH)) {
			return WALK_TO_FURNACE;
		}
		if (inventory.containsItem(ItemID.MOLTEN_GLASS)) {
			return DEPOSIT_ITEMS;
		}
		if (!inventory.containsItem(ItemID.MOLTEN_GLASS)) {
			return WITHDRAWING_ITEMS;
		} else {
			utils.sendGameMessage("Ran out of Items");
			shutDown();
		}
		return IDLE;
	}

	public glassmakerState getState() {
		if (timeout > 0) {
			return TIMEOUT;
		}
		if (playerUtils.isMoving(beforeLoc)) {
			timeout = 1 + tickDelay();
			return MOVING;
		}
		if (bank.isOpen()) {
			return getBankState();
		}
		Widget lvlup = client.getWidget(WidgetInfo.LEVEL_UP_SKILL);
		if (lvlup != null && !lvlup.isHidden()) {
			if (inventory.containsItem(ItemID.SODA_ASH)) {
				return WALK_TO_FURNACE;
			} else {
				return FIND_BANK;
			}
		}
		if (player.getAnimation() == 899 || player.getAnimation() == 827) {
			return ANIMATING;
		}
		if ((inventory.getItemCount(ItemID.BUCKET, false) > 13 && inventory.getItemCount(ItemID.MOLTEN_GLASS, false) > 13) || (player.getWorldArea().intersectsWith(EDGE) && inventory.isFull())) {
			return FIND_BANK;
		}
		if (inventory.isFull()) {
			return getGlassMakerState();
		}
		if (player.getWorldArea().intersectsWith(EDGE) && !inventory.isEmpty()) {
			openBank();
		}
		return IDLE;
	}

	@Subscribe
	private void onGameTick(GameTick tick) {
		if (!startGlassMaker) {
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null) {
			if (!client.isResized()) {
				utils.sendGameMessage("Client must be set to resizable");
				startGlassMaker = false;
				return;
			}
			state = getState();
			beforeLoc = player.getLocalLocation();
			switch (state) {
				case TIMEOUT:
					playerUtils.handleRun(30, 20);
					timeout--;
					break;
				case WALK_TO_FURNACE:
					useFurnace();
					break;
				case MAKE_GLASS:
					makeGlass();
					timeout = tickDelay();
					break;
				case WITHDRAWING_ITEMS:
					withdrawItems();
					break;
				case ANIMATING:
					timeout = 1;
					break;
				case MOVING:
					playerUtils.handleRun(30, 20);
					timeout = tickDelay();
					break;
				case FIND_BANK:
					openBank();
					timeout = tickDelay();
					break;
				case DEPOSIT_ITEMS:
					depositItems();
					timeout = tickDelay();
					break;
				case IDLE:
					if (!inventory.containsItem(ItemID.SODA_ASH))
						openBank();
					else if (inventory.containsItem(ItemID.SODA_ASH))
						useFurnace();
			}

		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN && startGlassMaker) {
			state = TIMEOUT;
			timeout = 2;
		}
	}

	private void withdrawItems() {
		if (inventory.containsItem(ItemID.MOLTEN_GLASS) && inventory.containsItem(ItemID.BUCKET))
			bank.depositAll();
		else if (inventory.isEmpty() && bank.isOpen()) {
			withdrawX(1783);
			tickDelay();
		}
		if (inventory.containsItem(ItemID.BUCKET_OF_SAND))
			withdrawX(1781);
	}

	private void depositItems() {
		if (inventory.isFull() && bank.isOpen())
			bank.depositAll();
	}

	private void makeGloss() { ///Old method.
		targetMenu = new MenuEntry("Make", "<col=ff9040>Molten glass</col>", 1, 57, -1, 17694734, false);
		mouse.delayMouseClick(client.getWidget(270, 14).getBounds(), sleepDelay());
	}
	private void makeGlass() {
		utils.doActionMsTime(new MenuEntry("Make", "<col=ff9040>Molten glass</col>", 1, 57, -1, 17694734, false), client.getWidget(270,14).getBounds(), sleepDelay());
		{
			mouse.getClickPoint(client.getWidget(270,14).getBounds());
		}
	}
	private glassmakerState getGlassMakerState() {
		if (client.getWidget(270, 14) != null) {
			return MAKE_GLASS;
		}
		return TIMEOUT;
	}
	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.info(event.toString());
	}
	private void withdrawX(int ID){
		if(client.getVarbitValue(3960)!=14){
			bank.withdrawItemAmount(ID,14);
			timeout+=3;
		} else {
			targetMenu = new MenuEntry("", "", (client.getVarbitValue(6590) == 3) ? 1 : 5, MenuOpcode.CC_OP.getId(), bank.getBankItemWidget(ID).getIndex(), 786444, false);
			menu.setEntry(targetMenu);
			clickBounds = bank.getBankItemWidget(ID).getBounds()!=null ? bank.getBankItemWidget(ID).getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
			mouse.delayMouseClick(clickBounds,sleepDelay());
		}
	}
}