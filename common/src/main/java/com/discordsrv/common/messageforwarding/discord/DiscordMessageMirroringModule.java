/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2022 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.common.messageforwarding.discord;

import com.discordsrv.api.channel.GameChannel;
import com.discordsrv.api.discord.api.entity.DiscordUser;
import com.discordsrv.api.discord.api.entity.channel.DiscordMessageChannel;
import com.discordsrv.api.discord.api.entity.channel.DiscordTextChannel;
import com.discordsrv.api.discord.api.entity.channel.DiscordThreadChannel;
import com.discordsrv.api.discord.api.entity.guild.DiscordGuildMember;
import com.discordsrv.api.discord.api.entity.message.DiscordMessageEmbed;
import com.discordsrv.api.discord.api.entity.message.ReceivedDiscordMessage;
import com.discordsrv.api.discord.api.entity.message.SendableDiscordMessage;
import com.discordsrv.api.discord.events.message.DiscordMessageDeleteEvent;
import com.discordsrv.api.discord.events.message.DiscordMessageUpdateEvent;
import com.discordsrv.api.event.bus.Subscribe;
import com.discordsrv.api.event.events.message.receive.discord.DiscordChatMessageProcessingEvent;
import com.discordsrv.common.DiscordSRV;
import com.discordsrv.common.config.main.DiscordIgnoresConfig;
import com.discordsrv.common.config.main.channels.MirroringConfig;
import com.discordsrv.common.config.main.channels.base.BaseChannelConfig;
import com.discordsrv.common.config.main.channels.base.IChannelConfig;
import com.discordsrv.common.function.OrDefault;
import com.discordsrv.common.future.util.CompletableFutureUtil;
import com.discordsrv.common.logging.NamedLogger;
import com.discordsrv.common.module.type.AbstractModule;
import com.github.benmanes.caffeine.cache.Cache;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DiscordMessageMirroringModule extends AbstractModule<DiscordSRV> {

    private final Cache<String, Set<MessageReference>> mapping;

    public DiscordMessageMirroringModule(DiscordSRV discordSRV) {
        super(discordSRV, new NamedLogger(discordSRV, "DISCORD_MIRRORING"));
        this.mapping = discordSRV.caffeineBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    @Subscribe
    public void onDiscordChatMessageProcessing(DiscordChatMessageProcessingEvent event) {
        if (checkCancellation(event)) {
            return;
        }

        Map<GameChannel, OrDefault<BaseChannelConfig>> channels = discordSRV.channelConfig().orDefault(event.getChannel());
        if (channels == null || channels.isEmpty()) {
            return;
        }

        ReceivedDiscordMessage message = event.getDiscordMessage();
        DiscordMessageChannel channel = event.getChannel();

        List<Pair<DiscordMessageChannel, OrDefault<MirroringConfig>>> mirrorChannels = new ArrayList<>();
        List<CompletableFuture<DiscordThreadChannel>> futures = new ArrayList<>();

        for (Map.Entry<GameChannel, OrDefault<BaseChannelConfig>> entry : channels.entrySet()) {
            OrDefault<BaseChannelConfig> channelConfig = entry.getValue();
            OrDefault<MirroringConfig> config = channelConfig.map(cfg -> cfg.mirroring);
            if (!config.get(cfg -> cfg.enabled, true)) {
                continue;
            }

            DiscordIgnoresConfig ignores = config.get(cfg -> cfg.ignores);
            if (ignores != null && ignores.shouldBeIgnored(message.isWebhookMessage(), message.getAuthor(), message.getMember().orElse(null))) {
                continue;
            }

            IChannelConfig iChannelConfig = channelConfig.get(cfg -> cfg instanceof IChannelConfig ? (IChannelConfig) cfg : null);
            if (iChannelConfig == null) {
                continue;
            }

            List<Long> channelIds = iChannelConfig.channelIds();
            if (channelIds != null) {
                for (Long channelId : channelIds) {
                    discordSRV.discordAPI().getTextChannelById(channelId).ifPresent(textChannel -> {
                        if (textChannel.getId() != channel.getId()) {
                            mirrorChannels.add(Pair.of(textChannel, config));
                        }
                    });
                }
            }

            discordSRV.discordAPI().findOrCreateThreads(channelConfig, iChannelConfig, threadChannel -> {
                if (threadChannel.getId() != channel.getId()) {
                    mirrorChannels.add(Pair.of(threadChannel, config));
                }
            }, futures, false);
        }

        CompletableFutureUtil.combine(futures).whenComplete((v, t) -> {
            List<CompletableFuture<Pair<ReceivedDiscordMessage, OrDefault<MirroringConfig>>>> messageFutures = new ArrayList<>();
            for (Pair<DiscordMessageChannel, OrDefault<MirroringConfig>> pair : mirrorChannels) {
                DiscordMessageChannel mirrorChannel = pair.getKey();
                OrDefault<MirroringConfig> config = pair.getValue();
                SendableDiscordMessage sendableMessage = convert(event.getDiscordMessage(), config);

                CompletableFuture<Pair<ReceivedDiscordMessage, OrDefault<MirroringConfig>>> future =
                        mirrorChannel.sendMessage(sendableMessage).thenApply(msg -> Pair.of(msg, config));

                messageFutures.add(future);
                future.exceptionally(t2 -> {
                    discordSRV.logger().error("Failed to mirror message to " + mirrorChannel, t2);
                    return null;
                });
            }

            CompletableFutureUtil.combine(messageFutures).whenComplete((messages, t2) -> {
                Set<MessageReference> references = new HashSet<>();
                for (Pair<ReceivedDiscordMessage, OrDefault<MirroringConfig>> pair : messages) {
                    references.add(getReference(pair.getKey(), pair.getValue()));
                }
                mapping.put(getCacheKey(message), references);
            });
        });
    }

    @Subscribe
    public void onDiscordMessageUpdate(DiscordMessageUpdateEvent event) {
        ReceivedDiscordMessage message = event.getMessage();
        Set<MessageReference> references = mapping.get(getCacheKey(message), k -> null);
        if (references == null) {
            return;
        }

        for (MessageReference reference : references) {
            DiscordMessageChannel channel = reference.getMessageChannel(discordSRV);
            if (channel == null) {
                continue;
            }

            SendableDiscordMessage sendableMessage = convert(message, reference.config);
            channel.editMessageById(reference.messageId, sendableMessage).whenComplete((v, t) -> {
                if (t != null) {
                    discordSRV.logger().error("Failed to update mirrored message in " + channel);
                }
            });
        }
    }

    @Subscribe
    public void onDiscordMessageDelete(DiscordMessageDeleteEvent event) {
        Set<MessageReference> references = mapping.get(getCacheKey(event.getChannel(), event.getMessageId()), k -> null);
        if (references == null) {
            return;
        }

        for (MessageReference reference : references) {
            DiscordMessageChannel channel = reference.getMessageChannel(discordSRV);
            if (channel == null) {
                continue;
            }

            channel.deleteMessageById(reference.messageId, reference.webhookMessage).whenComplete((v, t) -> {
                if (t != null) {
                    discordSRV.logger().error("Failed to delete mirrored message in " + channel);
                }
            });
        }
    }

    /**
     * Converts a given received message to a sendable message.
     */
    private SendableDiscordMessage convert(ReceivedDiscordMessage message, OrDefault<MirroringConfig> config) {
        DiscordGuildMember member = message.getMember().orElse(null);
        DiscordUser user = message.getAuthor();
        String username = discordSRV.placeholderService().replacePlaceholders(
                config.get(cfg -> cfg.usernameFormat, "%user_effective_name% [M]"),
                member, user
        );
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }

        SendableDiscordMessage.Builder builder = SendableDiscordMessage.builder()
                .setContent(message.getContent().orElse(null))
                .setWebhookUsername(username) // (member != null ? member.getEffectiveName() : user.getUsername()) + " [M]"
                .setWebhookAvatarUrl(
                        member != null
                            ? member.getEffectiveServerAvatarUrl()
                            : user.getEffectiveAvatarUrl()
                );
        for (DiscordMessageEmbed embed : message.getEmbeds()) {
            builder.addEmbed(embed);
        }
        return builder.build();
    }

    private MessageReference getReference(ReceivedDiscordMessage message, OrDefault<MirroringConfig> config) {
        return getReference(message.getChannel(), message.getId(), message.isWebhookMessage(), config);
    }

    private MessageReference getReference(
            DiscordMessageChannel channel,
            long messageId,
            boolean webhookMessage,
            OrDefault<MirroringConfig> config
    ) {
        if (channel instanceof DiscordTextChannel) {
            DiscordTextChannel textChannel = (DiscordTextChannel) channel;
            return new MessageReference(textChannel, messageId, webhookMessage, config);
        } else if (channel instanceof DiscordThreadChannel) {
            DiscordThreadChannel threadChannel = (DiscordThreadChannel) channel;
            return new MessageReference(threadChannel, messageId, webhookMessage, config);
        }
        throw new IllegalStateException("Unexpected channel type: " + channel.getClass().getName());
    }

    private static String getCacheKey(ReceivedDiscordMessage message) {
        return getCacheKey(message.getChannel(), message.getId());
    }

    private static String getCacheKey(DiscordMessageChannel channel, long messageId) {
        if (channel instanceof DiscordTextChannel) {
            return getCacheKey(channel.getId(), 0L, messageId);
        } else if (channel instanceof DiscordThreadChannel) {
            long parentId = ((DiscordThreadChannel) channel).getParentChannel().getId();
            return getCacheKey(parentId, channel.getId(), messageId);
        }
        throw new IllegalStateException("Unexpected channel type: " + channel.getClass().getName());
    }

    private static String getCacheKey(long channelId, long threadId, long messageId) {
        return Long.toUnsignedString(channelId)
                + (threadId > 0 ? ":" + Long.toUnsignedString(threadId) : "")
                + ":" + Long.toUnsignedString(messageId);
    }

    public static class MessageReference {

        private final long channelId;
        private final long threadId;
        private final long messageId;
        private final boolean webhookMessage;
        private final OrDefault<MirroringConfig> config;

        public MessageReference(
                DiscordTextChannel textChannel,
                long messageId,
                boolean webhookMessage,
                OrDefault<MirroringConfig> config
        ) {
            this(textChannel.getId(), -1L, messageId, webhookMessage, config);
        }

        public MessageReference(
                DiscordThreadChannel threadChannel,
                long messageId,
                boolean webhookMessage,
                OrDefault<MirroringConfig> config
        ) {
            this(threadChannel.getParentChannel().getId(), threadChannel.getId(), messageId, webhookMessage, config);
        }

        public MessageReference(
                long channelId,
                long threadId,
                long messageId,
                boolean webhookMessage,
                OrDefault<MirroringConfig> config
        ) {
            this.channelId = channelId;
            this.threadId = threadId;
            this.messageId = messageId;
            this.webhookMessage = webhookMessage;
            this.config = config;
        }

        public DiscordMessageChannel getMessageChannel(DiscordSRV discordSRV) {
            DiscordTextChannel textChannel = discordSRV.discordAPI().getTextChannelById(channelId).orElse(null);
            if (textChannel == null) {
                return null;
            } else if (threadId == -1) {
                return textChannel;
            }

            for (DiscordThreadChannel activeThread : textChannel.getActiveThreads()) {
                if (activeThread.getId() == threadId) {
                    return activeThread;
                }
            }
            return null;
        }
    }
}
