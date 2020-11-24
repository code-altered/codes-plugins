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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.botutils.BotUtils;
import net.runelite.client.plugins.botutils.Mouse;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.glassmaker.glassmakerState.*;

@Extension
@PluginDependency(BotUtils.class)
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
	private BotUtils utils;

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
		sleepLength = utils.randomDelay(config.sleepWeightedDistribution(), config.sleepMin(), config.sleepMax(), config.sleepDeviation(), config.sleepTarget());
		return sleepLength;
	}

	private int tickDelay() {
		int tickLength = (int) utils.randomDelay(config.tickDelayWeightedDistribution(), config.tickDelayMin(), config.tickDelayMax(), config.tickDelayDeviation(), config.tickDelayTarget());
		log.debug("tick delay for {} ticks", tickLength);
		return tickLength;
	}

	private void openBank() {
		NPC npc = utils.findNearestNpc(1618);
		if (npc != null) {
			targetMenu = new MenuEntry("", "",
					npc.getIndex(), MenuOpcode.NPC_THIRD_OPTION.getId(), 0, 0, false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(npc.getConvexHull().getBounds(), sleepDelay());
		}
	}

	private void useFurnace() {
		targetObject = utils.findNearestGameObject(16469);
		if (targetObject != null) {
			targetMenu = new MenuEntry("Smelt", "<col=ffff>Furnace", targetObject.getId(), 4, targetObject.getSceneMinLocation().getX(), targetObject.getSceneMinLocation().getY(), false);
			utils.setMenuEntry(targetMenu);
			utils.delayMouseClick(targetObject.getConvexHull().getBounds(), sleepDelay());
		}
	}

	private Point getRandomNullPoint() {
		if (client.getWidget(161, 34) != null) {
			Rectangle nullArea = client.getWidget(161, 34).getBounds();
			return new Point((int) nullArea.getX() + utils.getRandomIntBetweenRange(0, nullArea.width), (int) nullArea.getY() + utils.getRandomIntBetweenRange(0, nullArea.height));
		}

		return new Point(client.getCanvasWidth() - utils.getRandomIntBetweenRange(0, 2), client.getCanvasHeight() - utils.getRandomIntBetweenRange(0, 2));
	}

	private glassmakerState getBankState() {
		if (utils.inventoryContains(ItemID.BUCKET_OF_SAND) && utils.inventoryContains(ItemID.SODA_ASH)) {
			return WALK_TO_FURNACE;
		}
		if (utils.inventoryContains(ItemID.MOLTEN_GLASS)) {
			return DEPOSIT_ITEMS;
		}
		if (!utils.inventoryContains(ItemID.MOLTEN_GLASS)) {
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
		if (utils.isMoving(beforeLoc)) {
			timeout = 2 + tickDelay();
			return MOVING;
		}
		if (utils.isBankOpen()) {
			return getBankState();
		}
		if (client.getLocalPlayer().getAnimation() != -1) {
			return ANIMATING;
		}
		if ((utils.getInventoryItemCount(ItemID.BUCKET, false) > 13 && utils.getInventoryItemCount(ItemID.MOLTEN_GLASS, false) > 13) || (player.getWorldArea().intersectsWith(EDGE) && utils.inventoryFull())) {
			return FIND_BANK;
		}
		if (utils.inventoryFull()) {
			return getGlassMakerState();
		}
		if (player.getWorldArea().intersectsWith(EDGE) && !utils.inventoryFull()) {
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
					utils.handleRun(30, 20);
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
				case MOVING:
					utils.handleRun(30, 20);
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
					if (!utils.inventoryContains(ItemID.SODA_ASH)) {
						openBank();
						break;
					}
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
		if (utils.inventoryContains(ItemID.MOLTEN_GLASS) && utils.inventoryContains(ItemID.BUCKET))
			utils.depositAll();
		else if (utils.inventoryEmpty() && utils.isBankOpen()) {
			withdrawX(1783);
		}
		if (utils.inventoryContains(ItemID.BUCKET_OF_SAND))
			withdrawX(1781);
	}

	private void depositItems() {
		if (utils.inventoryFull() && utils.isBankOpen())
			utils.depositAll();
}
	private void makeGlass() {
		targetMenu = new MenuEntry("Make", "<col=ff9040>Molten glass</col>", 1, 57, -1, 17694734, false);
		utils.delayMouseClick(client.getWidget(270,14).getBounds(), sleepDelay());
		}

	private glassmakerState getGlassMakerState()
	{
		if (client.getWidget(270,14)!=null){
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
			utils.withdrawItemAmount(ID,14);
			timeout+=3;
		} else {
			targetMenu = new MenuEntry("", "", (client.getVarbitValue(6590) == 3) ? 1 : 5, MenuOpcode.CC_OP.getId(), utils.getBankItemWidget(ID).getIndex(), 786444, false);
			utils.setMenuEntry(targetMenu);
			clickBounds = utils.getBankItemWidget(ID).getBounds()!=null ? utils.getBankItemWidget(ID).getBounds() : new Rectangle(client.getCenterX() - 50, client.getCenterY() - 50, 100, 100);
			utils.delayMouseClick(clickBounds,sleepDelay());
		}
	}
}