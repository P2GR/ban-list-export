/*
 * Copyright (c) 2024, P2GR
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.runelite.http.api.RuneLiteAPI.JSON;

@Slf4j
@PluginDescriptor(
	name = "Clan Ban List Enhanced"
)
public class ClanBanListExportPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClanBanListExportConfig config;

	@Inject
	private ClanBanListExportChatMenuManager clanBanListExportChatMenuManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OkHttpClient webClient;

	private static final Gson GSON = RuneLiteAPI.GSON;

	private static final int CLAN_SETTINGS_INFO_PAGE_WIDGET = 690;

	private static final int CLAN_SIDEPANEL_DRAW = 4397;

	private static final int CLAN_SETTINGS_BANS_LIST_WIDGET_ID = 689;

	private List<ClanMemberMap> banListUser = null;

	private final List<String> importedUsernames = new ArrayList<>();



	@Provides
	ClanBanListExportConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanBanListExportConfig.class);
	}


	@Override
	protected void startUp() throws Exception
	{
		this.fetchClanMembersFromUrl();
	}

	@Override
	protected void shutDown() throws Exception
	{
		synchronized (importedUsernames) {
			importedUsernames.clear();
		}

		if (banListUser != null) {
			banListUser.clear();
		}
	}

    @Subscribe
	public void onWidgetLoaded(WidgetLoaded widget)
	{
		switch (widget.getGroupId())
		{
			case CLAN_SETTINGS_INFO_PAGE_WIDGET:
				if (config.getShowHelperText())
				{
					clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.OPEN_BAN_SCREEN);
				}
				break;

			case CLAN_SETTINGS_BANS_LIST_WIDGET_ID:
				if (this.config.autoUpdateBanList()) {
					this.SendClanMembersToUrl();
				} else {
					if (this.client.getWidget(689, 0) == null)
					{
						this.banListUser = null;
					}
					else
					{
						clanBanListExportChatMenuManager.update(ClanBanListExportChatMenuManager.WhatToShow.SHOW_EXPORT_OPTIONS);
					}
				}
				break;

			default:
				break;
		}
	}


	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent)
	{
		final String eventName = scriptCallbackEvent.getEventName();
		if ("chatMessageBuilding".equals(eventName) && !config.getImportDataUrl().isEmpty()) {
			highlightRedInCC();
		}
	}


	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == CLAN_SIDEPANEL_DRAW && !config.getImportDataUrl().isEmpty()) {
			rebuildClanPanel();
		}
	}


	@Subscribe
	private void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
	{
		if (config.getImportDataUrl().isEmpty()) {
			return;
		}

		FriendsChatMember member = event.getMember();
		String memberUsername = Text.standardize(member.getName());

		boolean isBanned = isBannedUser(memberUsername);

		if (isBanned)
		{
			highlightRedInCC();
		}
	}


	public void rebuildClanPanel()
	{
		Widget containerWidget = client.getWidget(ComponentID.CLAN_MEMBERS);
		if (containerWidget == null)
		{
			return;
		}

		Widget[] children = containerWidget.getChildren();
		if (children == null)
		{
			return;
		}

		for (Widget child : children) {
			if (child.getFontId() == -1 || child.getXTextAlignment() != 0) {
				continue;
			}

			String name = Text.removeTags(child.getText());
			String sanitized = Text.toJagexName(name);

			if (isBannedUser(sanitized)) {
				child.setText(ColorUtil.wrapWithColorTag(name, this.config.getPanelHightlightColor()));
			} else {
				child.setText(name);
			}
		}
	}


	/**
	 *  Checks if a user is on the ban list
	 */
	private boolean isBannedUser(String username) {
		username = Text.standardize(username);
		synchronized (importedUsernames) {
			for (String importedUsername : importedUsernames) {
				importedUsername = Text.standardize(importedUsername);
				if (username.equals(importedUsername)) {
					return true;
				}
			}
			return false;
		}
	}


	/**
	 *  Highlights banned users in the chat
	 */
	private void highlightRedInCC()
	{
		final String[] stringStack = client.getStringStack();
		final int size = client.getStringStackSize();

		if (size < 3) {
			log.error("Attempted to write chat colors with a small stack: " + size);
			return;
		}

		final String username = stringStack[size - 3];

		if (username == null || username.isEmpty())
		{
			return;
		}

		String sanitizedUsername = sanitizeUsername(username);
		boolean isBanned = isBannedUser(sanitizedUsername);

		if (isBanned)
		{
			sendWarning(sanitizedUsername);
			stringStack[size - 3] = ColorUtil.wrapWithColorTag(username, this.config.getHighlightColor());
		}
	}


	/**
	 * Sends a warning to our player, notifying them that a player is on a ban list
	 */
	private void sendWarning(String playerName)
	{
		final String url_message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("[Ban List Enhanced]: Warning! " + playerName + " is on the ban list!")
				.build();

		chatMessageManager.queue(
				QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(url_message)
						.build());
	}


	private void appendImportedUsernamesToWidget(Widget widget, List<String> usernames)
	{
		for (String username : usernames)
		{
			// Assuming the widget has a method to add text or children
			Widget newWidget = widget.createChild(-1, WidgetType.TEXT);
			newWidget.setText("Import: " + username);
		}
	}


	/**
	 *  Sanitizes the username to remove any tags that we don't need
	 */
	private String sanitizeUsername(String username) {

		String sanitized = username;
		if (sanitized.contains("<img")) {
			sanitized = sanitized.replaceAll("<img=\\d*>", "");
		}
		if (sanitized.contains("<col")) {
			sanitized = sanitized.replaceAll("<col=[\\w\\d]*>", "");
		}
		if (sanitized.contains("</col>")) {
			sanitized = sanitized.replaceAll("</col>", "");
		}
		return sanitized;
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
					.url(config.getExportDataUrl())
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


	/**
	 * Fetches clan members from the configured URL and stores the usernames.
	 */
	@Schedule(period = 5, unit = ChronoUnit.MINUTES)
	public void fetchClanMembersFromUrl()
	{

		String url = config.getImportDataUrl();
		if (url.isEmpty())
		{
			log.warn("Import URL is not configured.");
			return;
		}

		Request request = new Request.Builder()
				.url(url)
				.build();

		webClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Failed to fetch clan members from URL: " + url, e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{

				if (!response.isSuccessful())
				{
					log.error("Failed to fetch clan members from URL: " + url + " - " + response.message());
					return;
				}

				assert response.body() != null;
				String responseBody = response.body().string();
				JsonElement jsonElement = GSON.fromJson(responseBody, JsonElement.class);

				if (jsonElement.isJsonObject()) {
					JsonObject jsonObject = jsonElement.getAsJsonObject();
					JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
					List<String> usernames = new ArrayList<>();
					for (JsonElement element : usernamesArray)
					{
						usernames.add(element.getAsString());
					}
					synchronized (importedUsernames)
					{
						importedUsernames.clear();
						importedUsernames.addAll(usernames);
					}
				} else {
					log.error("Expected a JSON object but received: " + jsonElement);
				}
			}
		});
	}
}
