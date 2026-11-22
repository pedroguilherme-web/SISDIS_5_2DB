package aisafe.flights.infrastructure.messaging;

import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageEntity;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static aisafe.flights.infrastructure.messaging.MessagingTopology.HEADER_SOURCE_INSTANCE;

@Slf4j
@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class OutboxRelay {
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration KEEP_PUBLISHED = Duration.ofHours(24);

    private final OutboxMessageRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactions;
    private final String instanceId;
    private final ReentrantLock running = new ReentrantLock();

    public OutboxRelay(OutboxMessageRepository outbox, RabbitTemplate rabbitTemplate,
                       PlatformTransactionManager transactionManager,
                       @Value("${aisafe.messaging.instance-id:flight-operations}") String instanceId) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;

        // REQUIRES_NEW: the relay also runs from afterCommit(), where a joined transaction would never commit.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.instanceId = instanceId;
    }

    @Scheduled(fixedDelayString = "${aisafe.outbox.relay-interval:PT2S}", initialDelayString = "PT5S")
    public int publishPending() {
        if (!running.tryLock()) {
            return 0;
        }
        try {
            int published = 0;
            List<OutboxMessageEntity> pending;
            do {
                pending = outbox.findTop100ByPublishedAtIsNullOrderBySequenceNumberAsc();
                for (OutboxMessageEntity message : pending) {
                    // Stop at the first failure so events are never published out of order.
                    if (!publish(message)) {
                        return published;
                    }
                    published++;
                }
            } while (pending.size() == 100);
            return published;
        } finally {
            running.unlock();
        }
    }

    private boolean publish(OutboxMessageEntity message) {
        try {
            rabbitTemplate.invoke(operations -> {
                operations.send(message.getExchangeName(), message.getRoutingKey(), toAmqp(message));
                operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
                return null;
            });
            update(message.getMessageId(), true, null);
            log.info("Published {} {} from the outbox", message.getRoutingKey(), message.getMessageId());
            return true;
        } catch (AmqpException e) {
            update(message.getMessageId(), false, e.getMessage());
            log.warn("Broker unavailable, {} stays in the outbox and will be retried: {}", message.getMessageId(), e.getMessage());
            return false;
        }
    }

    private void update(String messageId, boolean published, String error) {
        transactions.executeWithoutResult(status -> outbox.findById(messageId).ifPresent(entity -> {
            if (published) {
                entity.markPublished();
            } else {
                entity.markFailed(error);
            }
        }));
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void purgePublished() {
        Integer removed = transactions.execute(status -> outbox.deletePublishedBefore(Instant.now().minus(KEEP_PUBLISHED)));
        if (removed != null && removed > 0) {
            log.info("Removed {} published outbox messages", removed);
        }
    }

    private Message toAmqp(OutboxMessageEntity message) {
        return MessageBuilder.withBody(message.getPayload())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setMessageId(message.getMessageId())
                .setType(message.getMessageType())
                .setAppId("flight-operations-service")
                .setTimestamp(Date.from(message.getCreatedAt()))
                .setHeader(HEADER_SOURCE_INSTANCE, instanceId)
                .build();
    }
}
