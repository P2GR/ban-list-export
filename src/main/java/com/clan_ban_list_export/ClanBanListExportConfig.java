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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.*;

@ConfigGroup("clan_ban_list_export")
public interface ClanBanListExportConfig extends Config
{

	@ConfigSection(
			name = "Export",
			description = "Export settings",
			position = 0
	)
	String exportSection = "exportSection";

	@ConfigItem(
			position = 0,
			keyName = "dataInputFormat",
			name = "Data Format",
			description = "The format of the exported data (ONLY FOR CLIPBOARD)",
			section = exportSection
	)
	default ClanBanListExportDataFormat getDataExportFormat() {
		return ClanBanListExportDataFormat.CSV;
	}

	@ConfigItem(
			position = 1,
			keyName = "exportToClipBoard",
			name = "Export to clipboard",
			description = "When viewing the clan's ban list in settings, copy info to clipboard.",
			section = exportSection

	)
	default boolean exportToClipBoard() {
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "showHelperText",
		name = "Show Helper Text",
		description = "Turns off that helper test that shows up.",
		section = exportSection

	)
	default boolean getShowHelperText() {
		return true;
	}


	@ConfigItem(
			position = 4,
			keyName = "updateWebRequest",
			name = "Auto update Ban List",
			description = "Automatically updates ban list when you open the clan settings.",
			section = exportSection

	)
	default boolean autoUpdateBanList() {
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = "dataInputUrl",
			name = "Export URL",
			description = "",
			section = exportSection
	)
	default String getExportDataUrl() {
		return "";
	}


	@ConfigSection(
			name = "Import",
			description = "Import settings",
			position = 0
	)
	String importSection = "importSection";

	@ConfigItem(
			position = 1,
			keyName = "dataInputUrl",
			name = "Import URL",
			description = "Allows for importing a ban list from a URL. This will function as an extension of the ban list.",
			section = importSection
	)
	default String getImportDataUrl() {
		return "";
	}


	@ConfigItem(
			position = 2,
			keyName = "highlightColor",
			name = "Highlight color",
			description = "Select the color to highlight banned users",
			section = importSection
	)
	default Color getHighlightColor() {
		return Color.RED;
	}

	@ConfigItem(
			position = 2,
			keyName = "panelhighlightcolor",
			name = "Panel highlight color",
			description = "Select the color to highlight banned users in the clan panel",
			section = importSection
	)
	default Color getPanelHightlightColor() {
		return Color.RED;
	}
}

