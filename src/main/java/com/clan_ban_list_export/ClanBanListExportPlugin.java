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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
	private ClanBanListExportOverlay clanBanListExportOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldService worldService;

	@Inject
	private OkHttpClient webClient;
	private static final Gson GSON = RuneLiteAPI.GSON;

	@Getter
	private final Map<String, BanDetails> banListMap = new HashMap<>();

	@Getter
	private HoveredPlayer hoveredPlayer;

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
		webClient = new OkHttpClient.Builder()
				.connectTimeout(15, TimeUnit.SECONDS)
				.readTimeout(15, TimeUnit.SECONDS)
				.writeTimeout(15, TimeUnit.SECONDS)
				.build();

		this.fetchClanMembersFromUrl();
		overlayManager.add(clanBanListExportOverlay);
		fetchClanMembersFromUrl();
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

		overlayManager.remove(clanBanListExportOverlay);

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


	/**
	 *  Event listener for when a player is hovered over in the clan chat.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (this.config.highlightInClanPanel()){
			int groupId = event.getActionParam1();

			if (groupId == ComponentID.CLAN_MEMBERS || groupId == ComponentID.CLAN_GUEST_MEMBERS) {
				String hoveredName = Text.toJagexName(Text.removeTags(event.getTarget()));
				setHoveredBannedPlayer(hoveredName);
			} else {
				hoveredPlayer = null;
			}
		}
	}

	/**
	 * Set the currently hovered banned player, if ban details exist for it.
	 */
	private void setHoveredBannedPlayer(String displayName)
	{
		hoveredPlayer = null;

		if (!Strings.isNullOrEmpty(displayName))
		{
			final BanDetails banDetails = banListMap.get(Text.standardize(displayName));
			if (banDetails != null)
			{
				hoveredPlayer = new HoveredPlayer(displayName, banDetails);
			}
		}
	}

	/**
	 * Build a tooltip for the hovered player, showing their ban details.
	 */
	String buildTooltip(BanDetails banDetails) {
		StringBuilder sb = new StringBuilder();
		String date = banDetails.getDate();
		if (!date.isEmpty()) {
			date = formatDate(date);
			sb.append("Date: ").append(ColorUtil.wrapWithColorTag(date, JagexColors.MENU_TARGET)).append("</br>");
		}
		String bannedBy = banDetails.getBannedBy();
		if (!bannedBy.isEmpty()) {
			sb.append("Banned by: ").append(ColorUtil.wrapWithColorTag(bannedBy, JagexColors.MENU_TARGET)).append("</br>");
		}
		String reason = formatReason(banDetails.getReason());
		if (!reason.isEmpty()) {
			sb.append("Reason: ").append(ColorUtil.wrapWithColorTag(reason, JagexColors.MENU_TARGET));
		}
		return sb.toString();
	}

	private String formatReason(String reason) {
		StringBuilder formattedReason = new StringBuilder();
		String[] words = reason.split(" ");
		int lineLength = 0;

		for (String word : words) {
			if (lineLength + word.length() > 40) {
				formattedReason.append("</br>");
				lineLength = 0;
			}
			formattedReason.append(word).append(" ");
			lineLength += word.length() + 1;
		}

		return formattedReason.toString().trim();
	}


	private String formatDate(String dateStr) {
		OffsetDateTime dateTime = OffsetDateTime.parse(dateStr);
		DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault());
		return dateTime.format(formatter);
	}


	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent scriptCallbackEvent)
	{
		if (this.config.highlightInChat()){
			final String eventName = scriptCallbackEvent.getEventName();
			if ("chatMessageBuilding".equals(eventName) && !config.getImportDataUrl().isEmpty()) {
				highlightRedInChat();
			}
		}
	}


	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (this.config.highlightInClanPanel()){
			if (event.getScriptId() == CLAN_SIDEPANEL_DRAW && !config.getImportDataUrl().isEmpty()) {
				highlightRedInClanPanel();
			}
		}
	}


	@Subscribe
	private void onClanMemberJoined(ClanMemberJoined event)
	{
		if (this.config.sendWarning()) {
			if (config.getImportDataUrl().isEmpty()) {
				return;
			}

			ClanChannelMember member = event.getClanMember();
			String sanitizedUsername = sanitizeUsername(member.getName());

			boolean isBanned = isBannedUser(sanitizedUsername);

			if (isBanned)
			{
				sendWarning(sanitizedUsername);
				highlightRedInChat();
			}
		}
	}


	public void highlightRedInClanPanel()
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
	 *  Highlights banned users in the chat
	 */
	private void highlightRedInChat()
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
			stringStack[size - 3] = ColorUtil.wrapWithColorTag(username, this.config.getHighlightColor());
		}
	}


	/**
	 *  Checks if a user is on the ban list
	 */
	private boolean isBannedUser(String username) {
		username = Text.standardize(username);
		synchronized (banListMap) {
			BanDetails banDetails = banListMap.get(username);
            return banDetails != null;
        }
	}




	/**
	 * Sends a warning to our player, notifying them that a player is on a ban list
	 */
	private void sendWarning(String playerName)
	{

		final BanDetails banDetails = banListMap.get(Text.standardize(playerName));
		final String title = ColorUtil.wrapWithColorTag("Ban List Enhanced", Color.green);

		// Array of messages to be sent
		List<String> messages = new ArrayList<>();

		messages.add(String.format("[%s] %s '%s' is on the ban list!",
				title,
				ColorUtil.wrapWithColorTag("Warning:", ColorUtil.fromHex("#fa2327")),
				playerName));


		if (this.config.showBanDate()) {
			messages.add(String.format("[%s] %s %s",
					title,
					ColorUtil.wrapWithColorTag("Date:", JagexColors.MENU_TARGET),
					formatDate(banDetails.getDate())));
		}

		if (this.config.showBannedBy()) {
			messages.add(String.format("[%s] %s %s",
					title,
					ColorUtil.wrapWithColorTag("Banned by:", JagexColors.MENU_TARGET),
					banDetails.getBannedBy()));
		}

		if (this.config.showReason()) {
			messages.add(String.format("[%s] %s %s",
					title,
					ColorUtil.wrapWithColorTag("Reason:", JagexColors.MENU_TARGET),
					banDetails.getReason()));
		}

		for (String message : messages) {
			chatMessageManager.queue(
					QueuedMessage.builder()
							.type(ChatMessageType.CONSOLE)
							.runeLiteFormattedMessage(message)
							.build());
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
		String result;
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
		if(!this.banListUser.isEmpty())
		{
			StringSelection stringSelection = new StringSelection(clipboardString);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
		}
	}

	/**
	 * Exports clanmembers to remote url
	 */
	public void SendClanMembersToUrl()
	{
		this.scrapeMembers();
		if(!this.banListUser.isEmpty())
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
					JsonObject usersObject = jsonObject.getAsJsonObject("users");
					synchronized (banListMap) {
						banListMap.clear();
						for (String key : usersObject.keySet()) {
							JsonObject userObject = usersObject.getAsJsonObject(key);
							String reason = userObject.get("reason").getAsString();
							String bannedBy = userObject.get("bannedBy").getAsString();
							String date = userObject.get("date").getAsString();
							BanDetails details = new BanDetails(key, date, bannedBy, reason);
							banListMap.put(Text.standardize(key), details);
						}
					}
				} else {
					log.error("Expected a JSON object but received: " + jsonElement);
				}
			}
		});
	}
}
