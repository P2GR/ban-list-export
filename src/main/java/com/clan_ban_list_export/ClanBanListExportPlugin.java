/*
 * Copyright (c) 2021, Bailey Townsend <baileytownsend2323@gmail.com>
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

package com.clan_ban_list_export;

import com.google.gson.Gson;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.runelite.http.api.RuneLiteAPI.JSON;

@Slf4j
@PluginDescriptor(
	name = "Clan Ban List Export"
)
public class ClanBanListExportPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClanBanListExportConfig config;
	@Inject
	private ClanBanListExportChatMenuManager clanBanListExportChatMenuManager;
	@Inject
	private OkHttpClient webClient;
	private static final Gson GSON = RuneLiteAPI.GSON;

	private static final int CLAN_SETTINGS_INFO_PAGE_WIDGET = 690;

	private static final int CLAN_SETTINGS_BANS_LIST_WIDGET_ID = 689;

	/**
	 * The ban list, scraped from your clan setup widget
	 */
	private List<ClanMemberMap> banListUser = null;

	@Provides
	ClanBanListExportConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanBanListExportConfig.class);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{

		if (widget.getGroupId() == CLAN_SETTINGS_INFO_PAGE_WIDGET && config.getShowHelperText())
		{
			log.info("ClanBanListExportPlugin: onWidgetLoaded: CLAN_SETTINGS_INFO_PAGE_WIDGET");
			clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.OPEN_BAN_SCREEN);
		}

		log.info(widget.getGroupId() + " " + CLAN_SETTINGS_BANS_LIST_WIDGET_ID);

		if (widget.getGroupId() == CLAN_SETTINGS_BANS_LIST_WIDGET_ID)
		{
			if (this.client.getWidget(689, 0) == null)
			{
				log.info("ClanBanListExportPlugin: onWidgetLoaded: CLAN_SETTINGS_MEMBERS_PAGE_WIDGET_ID: null");
				this.banListUser = null;
			}
			else
			{
				log.info("ClanBanListExportPlugin: onWidgetLoaded: CLAN_SETTINGS_MEMBERS_PAGE_WIDGET_ID");
				clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.SHOW_EXPORT_OPTIONS);
			}
		}
	}

	/**
	 * Subroutine - Update our memory of clan members and their ranks for
	 * clan setup
	 */
	public void scrapeMembers()
	{
		if (this.banListUser == null)
		{
			this.banListUser = new ArrayList<>();
		}
		this.banListUser.clear();

		//Checks to set up scraping
		Widget clanMemberNamesWidget = this.client.getWidget(689, 6);

        assert clanMemberNamesWidget != null;
        Widget[] clanMemberNamesWidgetValues = clanMemberNamesWidget.getChildren();

		if (clanMemberNamesWidgetValues == null)
		{
			return;
		}

        for (Widget clanMemberNamesWidgetValue : clanMemberNamesWidgetValues) {
            String rsn = Text.removeTags(clanMemberNamesWidgetValue.getText());

            if (rsn != null && !rsn.isEmpty()) {
                ClanMemberMap clanMember = new ClanMemberMap(rsn);
                this.banListUser.add(clanMember);
            }
        }
	}

	public void ClanToClipBoard()
	{

		this.scrapeMembers();

		if (this.config.exportToClipBoard())
		{
			String clipBoardString = "";

			switch (this.config.getDataExportFormat())
			{
				case JSON:
					clipBoardString = toJson(this.banListUser);
					break;
				case CSV:
					clipBoardString = toCSV(this.banListUser);
					break;

			}

			this.clanMembersToClipBoard(clipBoardString);
			clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.SUCCESS);
		}
	}

	/**
	 * Creates a csv string from clan members
	 *
	 * @param clanBanListMaps Clan members info
	 * @return csv with clan members info
	 */
	private String toCSV(List<ClanMemberMap> clanBanListMaps)
	{
		String result = "";

		StringBuilder sb = new StringBuilder();

		for (ClanMemberMap clanMember : clanBanListMaps)
		{
			sb.append(clanMember.getRSN()).append(",");
			sb.append("\n");
		}

		result = sb.deleteCharAt(sb.length() - 1).toString();

		return result;
	}

	private String toJson(List<ClanMemberMap> clanBanListMaps)
	{
		return GSON.toJson(clanBanListMaps);
	}

	/**
	 * Exports clanmembers to clip board
	 */
	private void clanMembersToClipBoard(String clipboardString)
	{
		if(this.banListUser.size() != 0)
		{
			StringSelection stringSelection = new StringSelection(clipboardString);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
		}
	}

	/**
	 * Exports clanmembers to remote url
	 *
	 * @return
	 */
	public void SendClanMembersToUrl()
	{
		this.scrapeMembers();
		if(this.banListUser.size() != 0)
		{
			try
			{
				String clanName = Objects.requireNonNull(this.client.getClanSettings()).getName();
				ClanBanListExportWebRequestModel webRequestModel = new ClanBanListExportWebRequestModel(clanName, this.banListUser);

				final Request request = new Request.Builder()
					.post(RequestBody.create(JSON, GSON.toJson(webRequestModel)))
					.url(config.getDataUrl())
					.build();

				webClient.newCall(request).enqueue(new Callback()
				{
					@Override
					public void onFailure(Call call, IOException e)
					{
						clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.WEB_REQUEST_FAILED);
					}

					@Override
					public void onResponse(Call call, Response response) throws IOException
					{
						if (response.isSuccessful())
						{
							clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.SUCCESS);
						}
						else
						{
							clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.WEB_REQUEST_FAILED);
						}

					}
				});
			}
			catch (Exception e)
			{
				clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.WEB_REQUEST_FAILED);

			}
		}
	}
}
