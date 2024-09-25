package com.clan_ban_list_export;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanBanListExportTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanBanListExportPlugin.class);
		RuneLite.main(args);
	}
}