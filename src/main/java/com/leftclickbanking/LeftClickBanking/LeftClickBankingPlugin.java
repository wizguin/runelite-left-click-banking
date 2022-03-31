package com.leftclickbanking.LeftClickBanking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
	name = "Left Click Banking",
	description = "Left click menu entry swapping for bank deposit/withdraw interfaces",
	tags = {"bank", "items", "inventory", "menu", "entry", "swap", "left", "click"}
)
public class LeftClickBankingPlugin extends Plugin
{
	private static final String CONFIGURE = "Configure";
	private static final String SAVE = "Save";
	private static final String RESET = "Reset";
	private static final String LEFT_CLICK_MENU_TARGET = "Left-click";

	private static final String CONFIG_GROUP = "leftclickbanking";
	private static final String ITEM_KEY_PREFIX = "item_";

	private static final WidgetMenuOption BANK_DEPOSIT_INVENTORY_CONFIGURE = new WidgetMenuOption(CONFIGURE,
			LEFT_CLICK_MENU_TARGET, WidgetInfo.BANK_DEPOSIT_INVENTORY);
	private static final WidgetMenuOption BANK_DEPOSIT_INVENTORY_SAVE = new WidgetMenuOption(SAVE,
			LEFT_CLICK_MENU_TARGET, WidgetInfo.BANK_DEPOSIT_INVENTORY);

	private static final List<String> VALID_OPTIONS = Arrays.asList(
			"withdraw-",
			"deposit-",
			"wield",
			"wear",
			"equip",
			"hold",
			"eat",
			"drink",
			"fill",
			"empty",
			"placeholder"
	);

	private boolean configuringLeftClick = false;

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private MenuManager menuManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LeftClickBankingConfig config;

	@Provides
	LeftClickBankingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LeftClickBankingConfig.class);
	}

	@Override
	public void startUp()
	{
		enableCustomization();
	}

	@Override
	public void shutDown()
	{
		disableCustomization();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!configuringLeftClick)
		{
			return;
		}

		MenuEntry firstEntry = event.getFirstEntry();
		if (firstEntry == null)
		{
			return;
		}

		int widgetId = firstEntry.getParam1();
		if (widgetId != WidgetInfo.BANK_ITEM_CONTAINER.getId()
				&& widgetId != WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId())
		{
			return;
		}

		int itemId = getItemId(firstEntry.getParam0(), widgetId);
		if (itemId == -1)
		{
			return;
		}

		String activeOption = null;

		// Apply left click action from configuration
		String config = getSwapConfig(itemId, widgetId);
		if (config != null)
		{
			activeOption = config;
		}

		MenuEntry[] entries = event.getMenuEntries();

		for (MenuEntry entry : entries)
		{
			String menuOption = getOption(entry);

			entry.setType(MenuAction.RUNELITE);
			entry.onClick(e ->
			{
				if (isValidOption(menuOption))
				{
					setSwapConfig(itemId, widgetId, getOption(e));
				}
			});

			if (Objects.equals(menuOption, activeOption))
			{
				entry.setOption("* " + entry.getOption());
			}
		}

		client.createMenuEntry(-1)
				.setOption(RESET)
				.setTarget(LEFT_CLICK_MENU_TARGET)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> unsetSwapConfig(itemId, widgetId));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded)
	{
		int widgetId = menuEntryAdded.getActionParam1();
		if (widgetId != WidgetInfo.BANK_ITEM_CONTAINER.getId()
				&& widgetId != WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId())
		{
			return;
		}

		int itemId = getItemId(menuEntryAdded.getActionParam0(), widgetId);
		if (itemId == -1)
		{
			return;
		}

		String config = getSwapConfig(itemId, widgetId);
		if (config != null)
		{
			bankModeSwap(config);
		}
	}

	private void bankModeSwap(String entryOption)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();

		for (int i = menuEntries.length - 1; i >= 0; --i)
		{
			MenuEntry entry = menuEntries[i];

			if (Objects.equals(getOption(entry), entryOption))
			{
				// Raise the priority of the op so it doesn't get sorted later
				entry.setType(MenuAction.CC_OP);

				menuEntries[i] = menuEntries[menuEntries.length - 1];
				menuEntries[menuEntries.length - 1] = entry;

				client.setMenuEntries(menuEntries);
				break;
			}
		}
	}

	private String getSwapConfig(int itemId, int widgetId)
	{
		itemId = ItemVariationMapping.map(itemId);
		String config = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + widgetId + "_" + itemId);
		if (config == null || config.isEmpty())
		{
			return null;
		}

		return config;
	}

	private void setSwapConfig(int itemId, int widgetId, String option)
	{
		itemId = ItemVariationMapping.map(itemId);
		configManager.setConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + widgetId + "_" + itemId, option);
	}

	private void unsetSwapConfig(int itemId, int widgetId)
	{
		itemId = ItemVariationMapping.map(itemId);
		configManager.unsetConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + widgetId + "_" + itemId);
	}

	private Integer getItemId(int inventoryIndex, int widgetId)
	{
		InventoryID inventoryId = (widgetId == WidgetInfo.BANK_ITEM_CONTAINER.getId())
				? InventoryID.BANK
				: InventoryID.INVENTORY;
		ItemContainer itemContainer = client.getItemContainer(inventoryId);

		if (itemContainer == null)
		{
			return -1;
		}

		Item[] items = itemContainer.getItems();
		if (inventoryIndex < 0 || inventoryIndex >= items.length)
		{
			return -1;
		}

		Item item = itemContainer.getItems()[inventoryIndex];
		if (item == null)
		{
			return -1;
		}

		return item.getId();
	}

	private String getOption(MenuEntry entry)
	{
		return entry.getOption().replace("* ", "").toLowerCase();
	}

	private Boolean isValidOption(String option)
	{
		for (String validOption : VALID_OPTIONS)
		{
			if (option.startsWith(validOption))
			{
				return true;
			}
		}

		return false;
	}

	private void enableCustomization()
	{
		rebuildCustomizationMenus();
	}

	private void disableCustomization()
	{
		removeCustomizationMenus();
		configuringLeftClick = false;
	}

	private void rebuildCustomizationMenus()
	{
		removeCustomizationMenus();
		if (configuringLeftClick)
		{
			menuManager.addManagedCustomMenu(BANK_DEPOSIT_INVENTORY_SAVE, this::save);
		}
		else
		{
			menuManager.addManagedCustomMenu(BANK_DEPOSIT_INVENTORY_CONFIGURE, this::configure);
		}
	}

	private void removeCustomizationMenus()
	{
		menuManager.removeManagedCustomMenu(BANK_DEPOSIT_INVENTORY_CONFIGURE);
		menuManager.removeManagedCustomMenu(BANK_DEPOSIT_INVENTORY_SAVE);
	}

	private void save(MenuEntry menuEntry)
	{
		configuringLeftClick = false;
		rebuildCustomizationMenus();
	}

	private void configure(MenuEntry menuEntry)
	{
		String target = Text.removeTags(menuEntry.getTarget());
		configuringLeftClick = target.equals(LEFT_CLICK_MENU_TARGET);
		rebuildCustomizationMenus();
	}
}
