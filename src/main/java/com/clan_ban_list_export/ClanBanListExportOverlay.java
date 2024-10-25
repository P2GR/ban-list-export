package com.clan_ban_list_export;

import com.google.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ClanBanListExportOverlay extends Overlay {


    private Client client;


    private TooltipManager tooltipManager;
    private final ClanBanListExportPlugin plugin;


    @Getter
    private final Map<String, BanDetails> banListMap = new HashMap<>();


    @Inject
    private ClanBanListExportOverlay(Client client, ClanBanListExportPlugin plugin, TooltipManager tooltipManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.tooltipManager = tooltipManager;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.isMenuOpen())
        {
            return null;
        }

        // Add a friend note tooltip to a hovered friend list entry
        final HoveredPlayer hovered = plugin.getHoveredPlayer();

        if (hovered != null) // Will always have a friend note if non-null
        {
            final BanDetails banDetails = hovered.getBanDetails();

            String tooltipText = plugin.buildTooltip(banDetails);

            tooltipManager.add(new Tooltip(tooltipText));
        }

        return null;
    }
}
