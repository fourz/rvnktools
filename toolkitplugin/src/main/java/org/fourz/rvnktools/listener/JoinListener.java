package org.fourz.rvnktools.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class JoinListener implements Listener {
    private List<String> messages = Arrays.asList(
                "Welcome to the server, where the code is made up and the bugs don't matter!",
                "Welcome, we've been expecting you!",
                "Welcome, enjoy your stay. Beware of the bugs!",
                "Welcome to our humble server, {player}!",
                "Glad to have you here, {player}!",
                "Look who's here! Welcome, {player}!",
                "The fun just doubled now that you're here, {player}!",
                "{player}, brace yourself for an awesome time!",
                "We've rolled out the red carpet for you, {player}!",
                "All hail {player}! Welcome to the server!",
                "Hey {player}, ready for some epic adventures?",
                "Welcome, {player}! Hope you've brought snacks!",
                "The party starts now that {player} is here!",
                "Ahoy, {player}! Welcome aboard!",
                "It's a bird! It's a plane! No, it's {player}! Welcome!",
                "{player}, you just made this server 100% better!",
                "Hey {player}, welcome! Mind the creepers!",
                "{player}, we hope you're ready for some serious fun!",
                "Welcome, {player}! Let the games begin!",
                "{player}, the server just got more awesome!",
                "{player}, ready to make some memories? Welcome!",
                "Welcome, {player}! We've got room for one more adventurer!",
                "{player}, welcome to the land of opportunities... and mobs!",
                "We were just waiting for you, {player}! Let's go!",
                "{player}, welcome! Remember, every hero starts somewhere!",
                "{player}, you are now officially part of the legend!",
                "The fun level just skyrocketed! Welcome, {player}!",
                "{player}, grab your sword and let's dive in!",
                "Welcome, {player}! Don't forget to craft some armor!"
            );

            private List<String> newPlayerMessages = Arrays.asList(
                "Welcome to Ravenkraft, {player}!",
                "Hey {player}, welcome to the adventure! We're happy to have you!",
                "A warm welcome to you, {player}! Let's start this journey together!",
                "Welcome aboard, {player}! Your adventure begins now!"
                // ... add more messages here
            );

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Random random = new Random();
            Player player = event.getPlayer();

            if (player.hasPlayedBefore()) {
                if (random.nextInt(100) < 40) {
                    String message = messages.get(random.nextInt(messages.size()));
                    player.sendMessage(message.replace("{player}", player.getName()));
                }
            } else {
                String message = newPlayerMessages.get(random.nextInt(newPlayerMessages.size()));
                player.sendMessage(message.replace("{player}", player.getName()));
            }
        }
    }