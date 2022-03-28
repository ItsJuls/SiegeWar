package com.gmail.goosius.siegewar.playeractions;

import com.gmail.goosius.siegewar.Messaging;
import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.SiegeWar;
import com.gmail.goosius.siegewar.enums.SiegeSide;
import com.gmail.goosius.siegewar.enums.SiegeStatus;
import com.gmail.goosius.siegewar.enums.SiegeWarPermissionNodes;
import com.gmail.goosius.siegewar.objects.Siege;
import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.gmail.goosius.siegewar.utils.CosmeticUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarBlockUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarDistanceUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarScoringUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarAllegianceUtil;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translatable;
import com.palmergames.bukkit.towny.permissions.TownyPermissionSource;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This class intercepts 'player death' events coming from the TownyPlayerListener class.
 *
 * This class evaluates the death, and determines if the player is involved in any nearby sieges.
 * If so, the opposing team gains battle points.
 *
 * @author Goosius
 */
public class PlayerDeath {

	/**
	 * Evaluates a siege death event.
	 * <p>
	 * If the dead player has a military rank of any sort:
	 * - They keep levels
	 * - They keep inventory
	 * - Any degradable items degrade a little (e.g. 20%)
	 * <p>
	 * If the dead player is officially involved in a nearby siege,
	 * - The opposing team gains battle points
	 * <p>
	 * The allegiance of the killer is not considered,
	 * in order to allows for a wider range of siege-kill-tactics.
	 * Examples:
	 * - Devices (cannons, traps, bombs etc.) can be used to gain battle points
	 *
	 * @param deadPlayer The player who died
	 * @param playerDeathEvent The player death event
	 */
	public static void evaluateSiegePlayerDeath(Player deadPlayer, PlayerDeathEvent playerDeathEvent) {
		World world = playerDeathEvent.getEntity().getWorld();
		if (!TownyAPI.getInstance().isTownyWorld(world)
			|| !TownyAPI.getInstance().getTownyWorld(world).isWarAllowed())
			return;

		Resident deadResident = TownyUniverse.getInstance().getResident(deadPlayer.getUniqueId());
		if (deadResident == null || !deadResident.hasTown())
			return;

		/*
		 * Do an early permission test to avoid hitting the sieges list if
		 * it could never return a proper SiegeSide.
		 */
		TownyPermissionSource tps = TownyUniverse.getInstance().getPermissionSource();
		boolean guard = tps.testPermission(deadPlayer, SiegeWarPermissionNodes.SIEGEWAR_TOWN_SIEGE_BATTLE_POINTS.getNode());
		boolean soldier = tps.testPermission(deadPlayer, SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_BATTLE_POINTS.getNode());
		if(!guard && !soldier)
			return;

		//Get nearest active siege
		Siege siege = SiegeController.getActiveSiegeAtLocation(deadPlayer.getLocation());
		if(siege == null)
			return;

		Town deadResidentTown = deadResident.getTownOrNull();
		if(soldier || siege.getTown().equals(deadResidentTown)) {
			//Keep and degrade inventory
			degradeInventory(playerDeathEvent);
			keepInventory(playerDeathEvent);
			//Keep level
			keepLevel(playerDeathEvent);
		}

		//Return if siege is not in progress
		if(siege.getStatus() != SiegeStatus.IN_PROGRESS)
			return;

		//Return if player is ineligible for penalty points
		SiegeSide playerSiegeSide = SiegeWarAllegianceUtil.calculateCandidateSiegePlayerSide(deadPlayer, deadResidentTown, siege);
		if(playerSiegeSide == SiegeSide.NOBODY)
			return;

		//Spawn death firework
		if (SiegeWarSettings.getWarSiegeDeathSpawnFireworkEnabled()) {
			if (isBannerMissing(siege.getFlagLocation())) {
				replaceMissingBanner(siege.getFlagLocation());
				Color bannerColor = ((Banner) siege.getFlagLocation().getBlock().getState()).getBaseColor().getColor();
				CosmeticUtil.spawnFirework(deadPlayer.getLocation().add(0, 2, 0), Color.RED, bannerColor, true);
			}
		}

		//Award penalty points
		SiegeWarScoringUtil.awardPenaltyPoints(
			playerSiegeSide == SiegeSide.ATTACKERS,
			deadPlayer,
			siege);

		//If the player that died had an ongoing session, remove it.
		if(siege.getBannerControlSessions().containsKey(deadPlayer)) {
			siege.removeBannerControlSession(siege.getBannerControlSessions().get(deadPlayer));
			Translatable errorMessage = SiegeWarSettings.isTrapWarfareMitigationEnabled() ? Translatable.of("msg_siege_war_banner_control_session_failure_with_altitude") : Translatable.of("msg_siege_war_banner_control_session_failure");
			Messaging.sendMsg(deadPlayer, errorMessage);
		}
	}

	private static void degradeInventory(PlayerDeathEvent playerDeathEvent) {
		Damageable damageable;
		double maxDurability;
		int currentDurability, damageToInflict, newDurability, durabilityWarning;
		boolean closeToBreaking = false;
		if (SiegeWarSettings.getWarSiegeDeathPenaltyDegradeInventoryEnabled()) {
			for (ItemStack itemStack : playerDeathEvent.getEntity().getInventory().getContents()) {
				if (itemStack != null && itemStack.getType().getMaxDurability() != 0 && !itemStack.getItemMeta().isUnbreakable()) {
					damageable = ((Damageable) itemStack.getItemMeta());
					maxDurability = itemStack.getType().getMaxDurability();
					currentDurability = damageable.getDamage();
					damageToInflict = (int)(maxDurability / 100 * SiegeWarSettings.getWarSiegeDeathPenaltyDegradeInventoryPercentage());
					newDurability = currentDurability + damageToInflict;
					if (newDurability >= maxDurability) {
						damageable.setDamage(Math.max((int)maxDurability-25, currentDurability));
						closeToBreaking = true;
					}
					else {
						damageable.setDamage(newDurability);
						durabilityWarning = damageToInflict * 2 + currentDurability;
						if (durabilityWarning >= maxDurability)
							closeToBreaking = true;
					}
					itemStack.setItemMeta((ItemMeta)damageable);
				}
			}
			if (closeToBreaking) //One or more items are close to breaking, send warning.
				Messaging.sendMsg(playerDeathEvent.getEntity(), Translatable.of("msg_inventory_degrade_warning"));
		}
	}

	private static void keepInventory(PlayerDeathEvent playerDeathEvent) {
		if(SiegeWarSettings.getWarSiegeDeathPenaltyKeepInventoryEnabled() && !playerDeathEvent.getKeepInventory()) {
			playerDeathEvent.setKeepInventory(true);
			playerDeathEvent.getDrops().clear();
		}
	}

	private static void keepLevel(PlayerDeathEvent playerDeathEvent) {
		if(SiegeWarSettings.getWarSiegeDeathPenaltyKeepLevelEnabled() && !playerDeathEvent.getKeepLevel()) {
			playerDeathEvent.setKeepLevel(true);
			playerDeathEvent.setDroppedExp(0);
		}
	}

	private static boolean isBannerMissing(Location location) {
		return !Tag.BANNERS.isTagged(location.getBlock().getType());
	}

	private static void replaceMissingBanner(Location location) {
		if (SiegeWarBlockUtil.isSupportBlockUnstable(location.getBlock()))
			location.getBlock().getRelative(BlockFace.DOWN).setType(Material.STONE);
		
		location.getBlock().setType(Material.BLACK_BANNER);
	}
}
