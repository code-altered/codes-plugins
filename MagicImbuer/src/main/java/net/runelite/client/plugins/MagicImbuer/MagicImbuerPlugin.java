package net.runelite.client.plugins.MagicImbuer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import java.awt.Rectangle;
///api
import net.runelite.api.*;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.GameObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;

import net.runelite.api.widgets.WidgetInfo;


///client
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;


///iUtils
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.ActionQueue;
import net.runelite.client.plugins.iutils.BankUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.PlayerUtils;





import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.time.Instant;

import static net.runelite.client.plugins.MagicImbuer.MagicImbuerState.*;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "code MagicImbuer",
	enabledByDefault = false,
	description = "Casts Magic Imbue",
	tags = {"magic, code"},
	type = PluginType.SKILLING
)
@Slf4j
public class MagicImbuerPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private MagicImbuerConfiguration config;

	@Inject
	private iUtils utils;

	@Inject
	private ActionQueue action;

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
	private MagicImbuerOverlay overlay;


	MagicImbuerState state;
	GameObject targetObject;
	MenuEntry targetMenu;
	WorldPoint skillLocation;
	Instant botTimer;
	LocalPoint beforeLoc;
	Player player;



	int timeout = 0;
	int casts = 0; //num of casts
	long sleepLength;
	boolean startMagicImbuer;
	Rectangle clickBounds;

	@Provides
	MagicImbuerConfiguration provideConfig(ConfigManager configManager) {
		return configManager.getConfig(MagicImbuerConfiguration.class);
	}

	private void resetVals() {
		overlayManager.remove(overlay);
		state = null;
		timeout = 0;
		botTimer = null;
		skillLocation = null;
		startMagicImbuer = false;
	}

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("MagicImbuer")) {
			return;
		}
		log.info("button {} pressed!", configButtonClicked.getKey());
		if (configButtonClicked.getKey().equals("startButton")) {
			if (!startMagicImbuer) {
				startMagicImbuer = true;
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
		startMagicImbuer = false;
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


	public MagicImbuerState getState() {

		if (timeout > 0) {
			return TIMEOUT;
		}
		if(player != null){
			return CAST_IMBUE;
		}
		if (playerUtils.isMoving(beforeLoc)) {
			timeout = 1 + tickDelay();
			return MOVING;
		}
		return IDLE;
	}

	@Subscribe
	private void onGameTick(GameTick tick) {
		if (!startMagicImbuer) {
			return;
		}
		player = client.getLocalPlayer();
		if (client != null && player != null && skillLocation != null) {
			if (!client.isResized()) {
				utils.sendGameMessage("Client must be set to resizable");
				startMagicImbuer = false;
				return;
			}
			state = getState();
			beforeLoc = player.getLocalLocation();
			switch (state) {
				case TIMEOUT:
					playerUtils.handleRun(30, 20);
					timeout--;
					break;
				case CAST_IMBUE:
					targetMenu = new MenuEntry("Cast", "<col=00ff00>Magic Imbue</col>", 1 , 57, -1, 14286972, true);
					utils.oneClickCastSpell(WidgetInfo.SPELL_MAGIC_IMBUE, targetMenu, new Rectangle(0, 0, 0, 0), sleepDelay());
					playerUtils.handleRun(30, 20);
					timeout = 14 + tickDelay(); //12.6 rounded up to 14 bc it would miss sometimes at 13

					casts++;

					break;
				case MOVING:
					playerUtils.handleRun(30, 20);
					timeout = tickDelay();
					break;
				case IDLE:
					break;
			}

		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN && startMagicImbuer) {
			state = TIMEOUT;
			timeout = 2;
		}
	}
	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event){
		log.info(event.toString());
	}



}