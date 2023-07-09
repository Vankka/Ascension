package com.discordsrv.common.command.discord.commands;

import com.discordsrv.api.discord.entity.interaction.command.DiscordCommand;
import com.discordsrv.api.discord.entity.interaction.component.ComponentIdentifier;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.command.combined.commands.DebugCommand;
import com.discordsrv.common.command.combined.commands.ResyncCommand;
import com.discordsrv.common.command.combined.commands.VersionCommand;
import com.discordsrv.common.command.discord.commands.subcommand.ExecuteCommand;
import com.discordsrv.common.config.main.DiscordCommandConfig;

public class DiscordSRVDiscordCommand {

    private static final ComponentIdentifier IDENTIFIER = ComponentIdentifier.of("DiscordSRV", "discordsrv");

    private static DiscordCommand INSTANCE;

    public static DiscordCommand get(DiscordSRV discordSRV) {
        if (INSTANCE == null) {
            DiscordCommandConfig config = discordSRV.config().discordCommand;

            DiscordCommand.ChatInputBuilder builder = DiscordCommand.chatInput(IDENTIFIER, "discordsrv", "DiscordSRV related commands")
                    .addSubCommand(DebugCommand.getDiscord(discordSRV))
                    .addSubCommand(VersionCommand.getDiscord(discordSRV))
                    .addSubCommand(ResyncCommand.getDiscord(discordSRV));

            if (config.execute.enabled) {
                builder = builder.addSubCommand(ExecuteCommand.get(discordSRV));
            }

            INSTANCE = builder
                    .setGuildOnly(false)
                    .setDefaultPermission(DiscordCommand.DefaultPermission.ADMINISTRATOR)
                    .build();
        }

        return INSTANCE;
    }
}
