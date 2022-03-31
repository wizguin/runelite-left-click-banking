package com.leftclickbanking.LeftClickBanking;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LeftClickBankingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LeftClickBankingPlugin.class);
		RuneLite.main(args);
	}
}