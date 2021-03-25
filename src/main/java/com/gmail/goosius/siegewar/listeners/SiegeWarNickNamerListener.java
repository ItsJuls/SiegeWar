package com.gmail.goosius.siegewar.listeners;

import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.SiegeWar;
import com.gmail.goosius.siegewar.enums.SiegeSide;
import com.gmail.goosius.siegewar.objects.Siege;
import com.gmail.goosius.siegewar.utils.SiegeWarAllegianceUtil;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.inventivetalent.nicknamer.api.event.disguise.NickDisguiseEvent;

import java.awt.Color;

public class SiegeWarNickNamerListener implements Listener {

    public static final Color FRIEND_COLOR = Color.getHSBColor(0.00f,0.28f,0.73f);
    public static final Color ENEMY_COLOR = Color.getHSBColor(1.00f,0.16f,0.00f);
    public static final Color NEUTRAL_COLOR = Color.getHSBColor(0.70f,0.75f,0.71f);

    @SuppressWarnings("unused")
    private final SiegeWar plugin;

    public SiegeWarNickNamerListener(SiegeWar instance) {
        plugin = instance;
    }

    /**
     * This method is called every time a target player's name is viewed by another player.
     *
     * If the viewing player is in a siege zone, we set the name to
     * - Red if the target player is on the same siege side.
     * - Blue if the target player is on the enemy siege side.
     * - Grey if the target player is not officially on either side.
     *
     * @param event
     */
    @EventHandler
    public void on(NickDisguiseEvent event) {
        try {
            Siege siege = SiegeController.getNearestActiveSiegeAt(event.getReceiver().getLocation());

            if (siege != null) {
                SiegeSide receiverResidentSiegeSide;
                SiegeSide targetResidentSiegeSide;
                Resident receiverResident; //The player doing the looking
                Resident targetResident; //The player being looked at


                event.setNick(FRIEND_COLOR + event.getReceiver().getName());

                //Get the siege side of the receiver player
                receiverResident = TownyUniverse.getInstance().getResident(event.getReceiver().getUniqueId());
                if (receiverResident.hasTown()) {
                    receiverResidentSiegeSide = SiegeWarAllegianceUtil.calculateSiegePlayerSide(event.getReceiver(), receiverResident.getTown(), siege);
                } else {
                    receiverResidentSiegeSide = SiegeSide.NOBODY;
                }

                //Get the siege side of the target player
                if (event.getPlayer() == null) {
                    return;
                } else {
                    targetResident = TownyUniverse.getInstance().getResident(event.getPlayer().getUniqueId());
                    if (targetResident.hasTown()) {
                        targetResidentSiegeSide = SiegeWarAllegianceUtil.calculateSiegePlayerSide(event.getPlayer(), targetResident.getTown(), siege);
                    } else {
                        targetResidentSiegeSide = SiegeSide.NOBODY;
                    }
                }

                //Resolve colour changes
                switch(receiverResidentSiegeSide) {
                    case ATTACKERS:
                        /*
                         * If receiver is an attacker, they see:
                         * Attacker - blue
                         * Defender - red
                         * Nobody -grey
                         */
                        switch (targetResidentSiegeSide) {
                            case ATTACKERS:
                                event.setNick(FRIEND_COLOR + targetResident.getName());
                            case DEFENDERS:
                                event.setNick(ENEMY_COLOR + targetResident.getName());
                            case NOBODY:
                                event.setNick(NEUTRAL_COLOR + targetResident.getName());
                        }
                    break;
                    case DEFENDERS:
                    case NOBODY:
                        /*
                         * If receiver is on neither side, they see:
                         * Attacker - red
                         * Defender - blue
                         * Nobody -grey
                         */
                        switch (targetResidentSiegeSide) {
                            case ATTACKERS:
                                event.setNick(ENEMY_COLOR + targetResident.getName());
                            case DEFENDERS:
                                event.setNick(FRIEND_COLOR + targetResident.getName());
                            case NOBODY:
                                event.setNick(NEUTRAL_COLOR + targetResident.getName());
                        }
                        break;
                }
            }
        } catch(Throwable t){
            System.out.println("Problem changing soldier colours view by player " + event.getReceiver().getName());
            t.printStackTrace();
        }
    }

}
