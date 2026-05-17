package com.fliphelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

public class GrandFlipOutPluginTest
{
	@Test
	public void testPluginLoads() throws Exception
	{
		ExternalPluginManager.loadBuiltin(GrandFlipOutPlugin.class);
		RuneLite.main(new String[]{"--developer-mode", "--debug"});
	}
}
