package net.runelite.client.plugins.glassmaker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.table.TableAlignment;
import net.runelite.client.ui.overlay.components.table.TableComponent;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

import static net.runelite.api.MenuOpcode.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDuration;

@Slf4j
@Singleton
class glassmakerOverlay extends OverlayPanel
{
	private final glassmakerPlugin plugin;
	private final glassmakerConfiguration config;

	String timeFormat;
	private String infoStatus = "Starting...";

	@Inject
	private glassmakerOverlay(final Client client, final glassmakerPlugin plugin, final glassmakerConfiguration config)
	{
		super(plugin);
		setPosition(OverlayPosition.BOTTOM_LEFT);
		this.plugin = plugin;
		this.config = config;
		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Glass Maker overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.botTimer == null || !plugin.startGlassMaker || !config.enableUI())
		{
			log.debug("Overlay conditions not met, not starting overlay");
			return null;
		}
		TableComponent tableComponent = new TableComponent();
		tableComponent.setColumnAlignments(TableAlignment.LEFT, TableAlignment.RIGHT);

		Duration duration = Duration.between(plugin.botTimer, Instant.now());
		timeFormat = (duration.toHours() < 1) ? "mm:ss" : "HH:mm:ss";
		tableComponent.addRow("Time running:", formatDuration(duration.toMillis(), timeFormat));
		if (plugin.state != null)
		{
			if (!plugin.state.name().equals("TIMEOUT"))
			{
				infoStatus = plugin.state.name();
			}
		}
		tableComponent.addRow("Status:", infoStatus);

		TableComponent tableDelayComponent = new TableComponent();
		tableDelayComponent.setColumnAlignments(TableAlignment.LEFT, TableAlignment.RIGHT);
		tableDelayComponent.addRow("Sleep delay:", plugin.sleepLength + "ms");
		tableDelayComponent.addRow("Tick delay:", String.valueOf(plugin.timeout));

		if (!tableComponent.isEmpty())
		{
			panelComponent.setBackgroundColor(ColorUtil.fromHex("#121212")); //Material Dark default
			panelComponent.setPreferredSize(new Dimension(200, 200));
			panelComponent.setBorder(new Rectangle(5, 5, 5, 5));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Oofie Glass Maker")
				.color(ColorUtil.fromHex("#ffbf00"))
				.build());
			panelComponent.getChildren().add(tableComponent);
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Delays")
				.color(ColorUtil.fromHex("#ffbf00"))
				.build());
			panelComponent.getChildren().add(tableDelayComponent);
		}
		return super.render(graphics);
	}
}