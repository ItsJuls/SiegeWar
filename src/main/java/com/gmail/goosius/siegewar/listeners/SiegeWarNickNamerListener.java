package com.gmail.goosius.siegewar.listeners;

import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.SiegeWar;
import com.gmail.goosius.siegewar.utils.SiegeWarDistanceUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.inventivetalent.nicknamer.api.event.disguise.NickDisguiseEvent;

public class SiegeWarNickNamerListener implements Listener {

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
        //Todo - to poeple of the same faction, show the normal skin
        //todo - to people of the oppising faction, show the other skin

        SiegeController.getActiveSiegesAt()

        if(SiegeWarDistanceUtil.isInSiegeZone())
        Player player = event.getReceiver()
        System.out.println("Nick disguise event called");
        event.setNick("Poopoopoo");
    }

}
